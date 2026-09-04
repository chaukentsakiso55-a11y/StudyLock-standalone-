(() => {
  if (window.__studyLockBlockListPolicyAttached || !window.StudyLockNative) return;
  window.__studyLockBlockListPolicyAttached = true;

  const native = window.StudyLockNative;
  const WEEK_MS = 7 * 24 * 60 * 60 * 1000;
  let policyState = null;
  let refreshTimer = null;

  function showToast(message) {
    if (window.StudyLockNativeHooks?.showToast) {
      window.StudyLockNativeHooks.showToast(message);
    }
  }

  function readState() {
    try {
      policyState = JSON.parse(native.getBlockedListPolicyState());
    } catch (_) {
      policyState = {
        canEdit: true,
        locked: false,
        initialConfiguration: true,
        remainingMs: 0,
        editWindowRemainingMs: 0,
        parentOverrideRemainingMs: 0
      };
    }
    return policyState;
  }

  function focusActive() {
    return document.getElementById('hero')?.classList.contains('locked') || false;
  }

  function formatDuration(ms) {
    const safe = Math.max(0, Number(ms) || 0);
    const totalMinutes = Math.ceil(safe / 60000);
    const days = Math.floor(totalMinutes / 1440);
    const hours = Math.floor((totalMinutes % 1440) / 60);
    const minutes = totalMinutes % 60;
    if (days > 0) return `${days}d ${hours}h`;
    if (hours > 0) return `${hours}h ${minutes}m`;
    return `${Math.max(1, minutes)}m`;
  }

  function currentSnapshot() {
    try {
      if (typeof blockedSites !== 'undefined' && Array.isArray(blockedSites)) {
        return JSON.stringify(blockedSites.map(site => site?.name?.trim()).filter(Boolean));
      }
    } catch (_) {}
    return JSON.stringify(
      Array.from(document.querySelectorAll('#siteList .site-name'))
        .map(node => node.textContent?.trim())
        .filter(Boolean)
    );
  }

  function studentCanEdit(announce = true) {
    const state = readState();
    if (focusActive()) {
      if (announce) showToast('Blocked apps cannot be changed during an active focus session.');
      return false;
    }
    if (state.canEdit) return true;
    if (announce) {
      showToast(`Blocked apps are locked. Next student edit window: ${formatDuration(state.remainingMs)}.`);
    }
    return false;
  }

  function recordChange() {
    try {
      policyState = JSON.parse(native.recordBlockedListChange());
    } catch (_) {
      readState();
    }
    applyUiState();
    return policyState;
  }

  function recordIfChanged(before) {
    setTimeout(() => {
      const after = currentSnapshot();
      if (after === before) return;
      recordChange();
      document.dispatchEvent(new Event('studylock:blocklist-changed'));
    }, 0);
  }

  function createStatusCard() {
    let card = document.getElementById('blockedListWeeklyPolicy');
    if (card) return card;

    const settingsList = document.getElementById('settingsSiteList');
    const section = settingsList?.closest('.settings-section');
    if (!section) return null;

    card = document.createElement('div');
    card.id = 'blockedListWeeklyPolicy';
    card.style.cssText = 'margin-top:12px;padding:12px;border:1px solid var(--glass-border);border-radius:14px;background:var(--surface-soft);';
    card.innerHTML = `
      <div class="settings-row-label" id="blockedListPolicyTitle">Weekly edit protection</div>
      <div class="settings-row-sub" id="blockedListPolicySub" style="margin-top:4px;">Checking blocked-app policy…</div>
      <div id="blockedListParentOverride" style="display:none;margin-top:10px;">
        <input type="password" class="settings-input" id="blockedListOverridePassword" placeholder="Parent password" autocomplete="current-password" style="margin-bottom:8px;">
        <button type="button" class="settings-btn primary" id="blockedListOverrideBtn">Parent unlock for 10 minutes</button>
      </div>`;
    section.appendChild(card);

    card.querySelector('#blockedListOverrideBtn')?.addEventListener('click', () => {
      const password = card.querySelector('#blockedListOverridePassword')?.value || '';
      if (!password) {
        showToast('Enter the parent password first.');
        return;
      }
      try {
        const result = JSON.parse(native.authorizeBlockedListOverride(password));
        policyState = result.state || readState();
        if (result.success) {
          card.querySelector('#blockedListOverridePassword').value = '';
        }
        showToast(result.message || (result.success ? 'Parent override enabled.' : 'Could not unlock the block list.'));
        applyUiState();
      } catch (_) {
        showToast('StudyLock could not verify the parent password.');
      }
    });

    return card;
  }

  function setDisabled(element, disabled) {
    if (!element) return;
    element.disabled = disabled;
    element.setAttribute('aria-disabled', String(disabled));
    if (disabled) {
      element.style.opacity = '0.55';
    } else {
      element.style.removeProperty('opacity');
    }
  }

  function applyUiState() {
    const state = policyState || readState();
    const lockedByWeek = !state.canEdit;
    const lockedByFocus = focusActive();
    const locked = lockedByWeek || lockedByFocus;

    ['siteInput', 'confirmAddBtn', 'toggleAddBtn', 'settingsSiteInput', 'settingsAddBtn',
      'selectInstalledAppsBtn', 'settingsSelectInstalledAppsBtn']
      .forEach(id => setDisabled(document.getElementById(id), locked));

    document.querySelectorAll('.site-remove').forEach(button => setDisabled(button, locked));

    const card = createStatusCard();
    if (!card) return;
    const title = card.querySelector('#blockedListPolicyTitle');
    const sub = card.querySelector('#blockedListPolicySub');
    const override = card.querySelector('#blockedListParentOverride');

    if (lockedByFocus) {
      title.textContent = 'Blocked list locked during focus';
      sub.textContent = 'Finish the current focus session before changing blocked apps.';
      override.style.display = 'none';
    } else if (state.parentOverrideActive) {
      title.textContent = 'Parent override active';
      sub.textContent = `Temporary edit access remains for ${formatDuration(state.parentOverrideRemainingMs)}.`;
      override.style.display = 'none';
    } else if (state.editWindowActive) {
      title.textContent = 'Weekly edit window open';
      sub.textContent = `Finish this week’s blocked-app changes within ${formatDuration(state.editWindowRemainingMs)}. After that, the list locks until next week.`;
      override.style.display = 'none';
    } else if (state.initialConfiguration || state.weeklyWindowAvailable) {
      title.textContent = 'Weekly edit available now';
      sub.textContent = 'Your first change starts a 10-minute configuration window, then the blocked list locks for 7 days.';
      override.style.display = 'none';
    } else {
      title.textContent = 'Blocked list locked for this week';
      sub.textContent = `Students can change the list again in ${formatDuration(state.remainingMs)}. A parent can temporarily unlock it with the parent password.`;
      override.style.display = 'block';
    }
  }

  function blockEvent(event, message) {
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
    showToast(message);
  }

  document.addEventListener('click', event => {
    const target = event.target instanceof Element ? event.target : null;
    const action = target?.closest('#confirmAddBtn,#settingsAddBtn,.site-remove');
    if (!action) return;
    if (!studentCanEdit(false)) {
      const state = policyState || readState();
      const message = focusActive()
        ? 'Blocked apps cannot be changed during an active focus session.'
        : `Blocked apps are locked for ${formatDuration(state.remainingMs)} more.`;
      blockEvent(event, message);
      return;
    }
    const before = currentSnapshot();
    recordIfChanged(before);
  }, true);

  document.addEventListener('keydown', event => {
    if (event.key !== 'Enter') return;
    const target = event.target;
    if (!(target instanceof Element)) return;
    if (!target.matches('#siteInput,#settingsSiteInput')) return;
    if (!studentCanEdit(false)) {
      const state = policyState || readState();
      blockEvent(event, `Blocked apps are locked for ${formatDuration(state.remainingMs)} more.`);
      return;
    }
    const before = currentSnapshot();
    recordIfChanged(before);
  }, true);

  const listObserver = new MutationObserver(() => applyUiState());
  [document.getElementById('siteList'), document.getElementById('settingsSiteList')]
    .filter(Boolean)
    .forEach(list => listObserver.observe(list, { childList: true }));

  window.StudyLockBlockListPolicy = {
    canEditNow: studentCanEdit,
    recordChange,
    refresh() {
      readState();
      applyUiState();
      return policyState;
    },
    getState() {
      return policyState || readState();
    },
    cooldownMs: WEEK_MS
  };

  createStatusCard();
  readState();
  applyUiState();
  refreshTimer = setInterval(() => {
    readState();
    applyUiState();
  }, 60000);

  window.addEventListener('beforeunload', () => {
    if (refreshTimer) clearInterval(refreshTimer);
    listObserver.disconnect();
  }, { once: true });
})();
