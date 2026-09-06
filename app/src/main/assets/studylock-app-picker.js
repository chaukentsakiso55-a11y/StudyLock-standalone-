(() => {
  if (window.__studyLockAppPickerAttached || !window.StudyLockNative) return;
  window.__studyLockAppPickerAttached = true;

  const native = window.StudyLockNative;
  const STORAGE_KEY = 'studylock_blocked_sites';

  function showToast(message) {
    if (window.StudyLockNativeHooks?.showToast) {
      window.StudyLockNativeHooks.showToast(message);
    }
  }

  function currentBlockedNames() {
    try {
      const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null');
      if (Array.isArray(saved)) {
        const names = saved.map(site => site?.name?.toString().trim()).filter(Boolean);
        if (names.length) return names;
      }
    } catch (_) {}
    try {
      if (typeof blockedSites !== 'undefined' && Array.isArray(blockedSites)) {
        return blockedSites.map(site => site?.name?.trim()).filter(Boolean);
      }
    } catch (_) {}
    return Array.from(document.querySelectorAll('#siteList .site-name'))
      .map(element => element.textContent?.trim())
      .filter(Boolean);
  }

  function requestPicker() {
    const focusActive = document.getElementById('hero')?.classList.contains('locked');
    if (focusActive) {
      showToast('End the current focus session before changing blocked apps.');
      return;
    }
    if (
      window.StudyLockBlockListPolicy &&
      !window.StudyLockBlockListPolicy.canEditNow(true)
    ) {
      return;
    }
    try {
      native.openAppPicker(JSON.stringify(currentBlockedNames()));
    } catch (_) {
      showToast('StudyLock could not open the installed-app picker.');
    }
  }

  function makePickerButton(id, compact) {
    const button = document.createElement('button');
    button.id = id;
    button.type = 'button';
    button.className = 'settings-btn primary';
    button.textContent = '📱 Select installed apps';
    button.style.width = '100%';
    button.style.marginTop = compact ? '8px' : '10px';
    button.style.marginBottom = '10px';
    button.addEventListener('click', requestPicker);
    return button;
  }

  function installPickerButtons() {
    const addForm = document.getElementById('addForm');
    if (addForm && !document.getElementById('selectInstalledAppsBtn')) {
      addForm.insertAdjacentElement('afterend', makePickerButton('selectInstalledAppsBtn', false));
    }

    const settingsInput = document.getElementById('settingsSiteInput');
    const settingsForm = settingsInput?.parentElement;
    if (settingsForm && !document.getElementById('settingsSelectInstalledAppsBtn')) {
      settingsForm.insertAdjacentElement(
        'afterend',
        makePickerButton('settingsSelectInstalledAppsBtn', true)
      );
    }

    window.StudyLockBlockListPolicy?.refresh?.();
  }

  function iconForName(name) {
    try {
      if (typeof iconFor === 'function') return iconFor(name);
    } catch (_) {}
    return name.replace(/[^a-zA-Z]/g, '').slice(0, 2).toUpperCase() || 'AP';
  }

  function normalizePickedName(item) {
    const label = item?.name?.toString().trim() || '';
    const packageName = item?.packageName?.toString().trim() || '';
    if (!label) return '';
    // Keep the package identifier in the stored entry so Android can resolve the
    // selected app exactly after StudyLock is restarted. The label stays first
    // so the list is still readable to the student.
    return packageName ? `${label} · ${packageName}` : label;
  }

  function persistPickedApps(picked) {
    const currentNames = currentBlockedNames();
    const merged = [];
    const seen = new Set();

    currentNames.forEach(name => {
      const clean = String(name || '').trim();
      const key = clean.toLowerCase();
      if (!clean || seen.has(key)) return;
      merged.push({ name: clean, icon: iconForName(clean) });
      seen.add(key);
    });

    let added = 0;
    picked.forEach(item => {
      const name = normalizePickedName(item);
      const key = name.toLowerCase();
      if (!name || seen.has(key)) return;
      merged.push({ name, icon: iconForName(item?.name?.toString().trim() || name) });
      seen.add(key);
      added += 1;
    });

    if (added < 1) return 0;
    localStorage.setItem(STORAGE_KEY, JSON.stringify(merged));
    return added;
  }

  function applyPickedApps(raw) {
    if (
      window.StudyLockBlockListPolicy &&
      !window.StudyLockBlockListPolicy.canEditNow(true)
    ) {
      return;
    }

    let picked;
    try {
      picked = typeof raw === 'string' ? JSON.parse(raw) : raw;
    } catch (_) {
      showToast('StudyLock could not read the selected apps.');
      return;
    }
    if (!Array.isArray(picked)) return;

    let added = 0;
    try {
      added = persistPickedApps(picked);
    } catch (_) {
      showToast('StudyLock could not save the selected apps.');
      return;
    }

    if (added > 0) {
      window.StudyLockBlockListPolicy?.recordChange?.();
      document.dispatchEvent(new Event('studylock:blocklist-changed'));
      showToast(`${added} app${added === 1 ? '' : 's'} added to the block list.`);
      // Reload from the same local page so the original StudyLock state code
      // rebuilds its in-memory blockedSites array from the newly saved list.
      setTimeout(() => window.location.reload(), 220);
    } else {
      showToast('Those apps are already on the block list.');
    }
  }

  installPickerButtons();

  window.studyLockApplyPickedApps = applyPickedApps;
  if (window.StudyLockNativeHooks) {
    window.StudyLockNativeHooks.onAppsPicked = applyPickedApps;
  }
})();
