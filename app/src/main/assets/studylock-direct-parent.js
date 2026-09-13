(() => {
  if (window.__studyLockDirectParentAttached || !window.StudyLockParentDirect) return;
  window.__studyLockDirectParentAttached = true;

  const native = window.StudyLockParentDirect;
  const SEEN_KEY = 'studylock_parent_seen_commands_v1';
  const MODE_KEY = 'studylock_parent_mode';
  const GOAL_KEY = 'studylock_parent_goal';
  const ASSIGNMENT_KEY = 'studylock_parent_assignment';
  const QUIZ_KEY = 'studylock_parent_quiz';
  const ALLOWED_KEY = 'studylock_parent_allowed_apps';
  const AUTO_KEY = 'studylock_parent_auto_v2';
  const BLOCKED_KEY = 'studylock_blocked_sites';
  let pendingCode = '';
  let directConnected = false;
  let fallbackStarted = false;

  function toast(message) {
    window.StudyLockNativeHooks?.showToast?.(message);
  }

  function readPairingCode() {
    const grid = document.getElementById('passkeyGrid');
    if (!grid) return '';
    return Array.from(grid.querySelectorAll('.passkey-digit'))
      .map(input => String(input.value || '').replace(/\D/g, '').slice(0, 1))
      .join('');
  }

  function directState() {
    try { return JSON.parse(native.getState() || '{}'); }
    catch (_) { return {}; }
  }

  function currentState() {
    let base = {};
    try {
      if (typeof snapshotState === 'function') base = snapshotState() || {};
    } catch (_) {}

    const timerText = document.getElementById('timerDisplay')?.textContent?.trim() || '00:00';
    const timerParts = timerText.split(':').map(Number);
    const remainingSeconds = timerParts.length === 2 && timerParts.every(Number.isFinite)
      ? timerParts[0] * 60 + timerParts[1]
      : 0;

    const nativeState = (() => {
      try { return JSON.parse(window.StudyLockNative?.getNativeState?.() || '{}'); }
      catch (_) { return {}; }
    })();

    return Object.assign({}, base, {
      focusActive: document.getElementById('hero')?.classList.contains('locked') === true,
      focusPaused: (document.getElementById('statusLabel')?.textContent || '').toLowerCase().includes('paused'),
      remainingSeconds,
      todayMinutes: document.getElementById('statToday')?.textContent || '0m',
      sessionsCompleted: Number(document.getElementById('statSessions')?.textContent || 0),
      streak: Number(document.getElementById('streakCount')?.textContent || 0),
      studyMode: localStorage.getItem(MODE_KEY) || 'normal',
      parentGoal: localStorage.getItem(GOAL_KEY) || '',
      parentAssignment: localStorage.getItem(ASSIGNMENT_KEY) || '',
      directParent: directState(),
      protection: nativeState
    });
  }

  function pushState() {
    if (!directConnected) return false;
    try { return native.sendState(JSON.stringify(currentState())); }
    catch (_) { return false; }
  }

  function seenIds() {
    try { return JSON.parse(localStorage.getItem(SEEN_KEY) || '[]'); }
    catch (_) { return []; }
  }

  function alreadySeen(id) {
    if (!id) return false;
    const ids = seenIds();
    if (ids.includes(id)) return true;
    ids.push(id);
    while (ids.length > 80) ids.shift();
    try { localStorage.setItem(SEEN_KEY, JSON.stringify(ids)); } catch (_) {}
    return false;
  }

  function chooseMinutes(minutes) {
    const wanted = Math.max(25, Math.min(300, Number(minutes || 25)));
    const buttons = Array.from(document.querySelectorAll('.preset-btn'));
    if (!buttons.length) return;
    let selected = buttons.find(button => Number(button.dataset.mins || 0) === wanted);
    if (!selected) {
      selected = buttons.reduce((best, button) => {
        const a = Math.abs(Number(best.dataset.mins || 25) - wanted);
        const b = Math.abs(Number(button.dataset.mins || 25) - wanted);
        return b < a ? button : best;
      }, buttons[0]);
    }
    selected?.click();
  }

  function normalizeEntries(value) {
    const source = Array.isArray(value) ? value : [];
    return source.map(item => String(item || '').trim()).filter(Boolean);
  }

  function updateParentSettingsSummary() {
    const mount = document.getElementById('studylockParentControlSummary');
    if (!mount) return;
    const goal = localStorage.getItem(GOAL_KEY) || 'No parent goal assigned';
    const assignment = localStorage.getItem(ASSIGNMENT_KEY) || 'No assignment waiting';
    let quiz = {};
    try { quiz = JSON.parse(localStorage.getItem(QUIZ_KEY) || '{}'); } catch (_) {}
    const mode = localStorage.getItem(MODE_KEY) || 'normal';
    mount.innerHTML = `
      <div class="settings-row"><div><div class="settings-row-title">Parent mode</div><div class="settings-row-sub">${escapeText(mode)}</div></div></div>
      <div class="settings-row"><div><div class="settings-row-title">Current parent goal</div><div class="settings-row-sub">${escapeText(goal)}</div></div></div>
      <div class="settings-row"><div><div class="settings-row-title">Current assignment</div><div class="settings-row-sub">${escapeText(assignment)}</div></div></div>
      <div class="settings-row"><div><div class="settings-row-title">Assigned quiz</div><div class="settings-row-sub">${escapeText(quiz.topic || 'None')} ${quiz.difficulty ? '· ' + escapeText(quiz.difficulty) : ''}</div></div></div>
    `;
  }

  function escapeText(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function ensureParentSettingsSection() {
    if (document.getElementById('studylockParentControlSection')) return;
    const settingsList = document.getElementById('settingsSiteList');
    const anchor = settingsList?.closest('.settings-section') || document.querySelector('.settings-section');
    const parent = anchor?.parentElement;
    if (!parent) return;

    const section = document.createElement('div');
    section.className = 'settings-section';
    section.id = 'studylockParentControlSection';
    section.innerHTML = `
      <div class="settings-label">Parent controls</div>
      <div id="studylockParentControlSummary"></div>
      <div class="settings-hint" style="margin-top:8px;">Commands from StudyLock Parent sync here directly on the same Wi-Fi when possible, with Firebase as fallback.</div>
    `;
    parent.insertBefore(section, anchor);
    updateParentSettingsSummary();
  }

  function applyCommand(command) {
    if (!command || command.type !== 'cmd') return;
    const requestId = String(command.requestId || '');
    if (alreadySeen(requestId)) return;
    const action = String(command.action || '');
    const payload = command.payload && typeof command.payload === 'object' ? command.payload : {};

    try {
      switch (action) {
        case 'start_focus': {
          const subject = String(payload.subject || '').trim();
          if (subject) localStorage.setItem('studylock_parent_subject', subject);
          chooseMinutes(payload.minutes);
          if (document.getElementById('hero')?.classList.contains('locked') !== true && typeof startSession === 'function') {
            startSession();
          }
          toast(subject ? `Parent started ${subject} focus` : 'Parent started a StudyLock focus session');
          break;
        }
        case 'pause_focus':
          if (typeof state !== 'undefined' && state.isLocked && !state.isPaused && typeof pauseBtn !== 'undefined') pauseBtn.click();
          break;
        case 'resume_focus':
          if (typeof state !== 'undefined' && state.isLocked && state.isPaused && typeof pauseBtn !== 'undefined') pauseBtn.click();
          break;
        case 'end_focus':
          if (document.getElementById('hero')?.classList.contains('locked') && typeof endSessionEarly === 'function') endSessionEarly();
          break;
        case 'set_schedule':
          localStorage.setItem(AUTO_KEY, JSON.stringify({
            enabled: payload.enabled !== false,
            minutes: Math.max(25, Math.min(300, Number(payload.minutes || 25))),
            startMinuteOfDay: Number(payload.startMinuteOfDay ?? -1)
          }));
          window.studyLockFirebaseParent?.maybeApplyAutoStudy?.();
          toast('Parent updated the StudyLock schedule');
          break;
        case 'set_blocked': {
          const entries = normalizeEntries(payload.entries);
          const stored = entries.map(name => ({ name, icon: name.slice(0, 2).toUpperCase() }));
          localStorage.setItem(BLOCKED_KEY, JSON.stringify(stored));
          document.dispatchEvent(new CustomEvent('studylock:blocklist-changed'));
          if (typeof render === 'function') render();
          toast('Parent updated blocked apps/sites');
          break;
        }
        case 'set_allowed':
          localStorage.setItem(ALLOWED_KEY, JSON.stringify(normalizeEntries(payload.entries)));
          toast('Parent updated allowed apps');
          break;
        case 'set_goal':
          localStorage.setItem(GOAL_KEY, String(payload.text || '').trim());
          updateParentSettingsSummary();
          toast('New parent study goal received');
          break;
        case 'set_assignment':
          localStorage.setItem(ASSIGNMENT_KEY, String(payload.text || '').trim());
          updateParentSettingsSummary();
          toast('New StudyLock assignment received');
          break;
        case 'set_quiz':
          localStorage.setItem(QUIZ_KEY, JSON.stringify({
            topic: String(payload.topic || '').trim(),
            difficulty: String(payload.difficulty || 'medium').trim()
          }));
          updateParentSettingsSummary();
          toast('Parent assigned a quiz');
          break;
        case 'set_mode':
          localStorage.setItem(MODE_KEY, String(payload.mode || 'normal'));
          updateParentSettingsSummary();
          toast(`StudyLock mode: ${String(payload.mode || 'normal')}`);
          break;
        case 'lock_settings':
          window.StudyLockSettingsLock?.lockNow?.();
          toast('Settings locked by Parent');
          break;
        case 'refresh_state':
        case 'protection_status':
          pushState();
          break;
      }
    } catch (error) {
      console.warn('StudyLock could not apply Parent command', action, error);
    }
    setTimeout(pushState, 250);
  }

  function drainCommands() {
    let commands = [];
    try { commands = JSON.parse(native.drainCommands() || '[]'); } catch (_) {}
    if (!Array.isArray(commands)) return;
    commands.forEach(applyCommand);
  }

  window.StudyLockDirectParentHooks = {
    onPairingResult(success, message) {
      const button = document.getElementById('syncConnectPasskeyBtn');
      if (button) {
        button.disabled = false;
        button.textContent = 'Connect';
      }
      if (success) {
        directConnected = true;
        fallbackStarted = false;
        document.getElementById('syncPasskeyError')?.classList.remove('show');
        try { localStorage.setItem('studylock_direct_parent_code', pendingCode); } catch (_) {}
        toast(message || 'Connected directly to StudyLock Parent ✓');
        pushState();
      } else if (!fallbackStarted && pendingCode) {
        fallbackStarted = true;
        toast('Direct Wi-Fi pairing unavailable — trying cloud fallback…');
        setTimeout(() => {
          try {
            if (typeof attemptPairing === 'function') attemptPairing(pendingCode);
          } catch (error) {
            console.warn('StudyLock cloud fallback pairing failed to start', error);
          }
        }, 250);
      }
    },
    onConnectionChanged(connected, message) {
      directConnected = !!connected;
      if (message) toast(message);
      if (connected) pushState();
    },
    onCommandAvailable() {
      drainCommands();
    }
  };

  document.getElementById('syncConnectPasskeyBtn')?.addEventListener('click', event => {
    const code = readPairingCode();
    if (!/^\d{6}$/.test(code)) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    pendingCode = code;
    fallbackStarted = false;
    const button = event.currentTarget;
    button.disabled = true;
    button.textContent = 'Connecting…';
    native.pair(code);
  }, true);

  ensureParentSettingsSection();
  drainCommands();

  try {
    const state = directState();
    directConnected = !!state.connected;
    if (directConnected) pushState();
  } catch (_) {}

  setInterval(() => {
    drainCommands();
    pushState();
  }, 5000);

  window.StudyLockDirectParent = {
    applyCommand,
    drainNow: drainCommands,
    pushState,
    isConnected: () => directConnected
  };
})();
