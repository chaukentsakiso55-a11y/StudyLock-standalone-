(() => {
  if (window.__studyLockAppPickerAttached || !window.StudyLockNative) return;
  window.__studyLockAppPickerAttached = true;

  const native = window.StudyLockNative;

  function showToast(message) {
    if (window.StudyLockNativeHooks?.showToast) {
      window.StudyLockNativeHooks.showToast(message);
    }
  }

  function currentBlockedNames() {
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
  }

  function applyPickedApps(raw) {
    let picked;
    try {
      picked = JSON.parse(raw);
    } catch (_) {
      showToast('StudyLock could not read the selected apps.');
      return;
    }
    if (!Array.isArray(picked)) return;

    if (typeof blockedSites === 'undefined' || !Array.isArray(blockedSites)) {
      showToast('The block list is not ready yet. Try again.');
      return;
    }

    const existing = new Set(
      blockedSites
        .map(site => site?.name?.trim().toLowerCase())
        .filter(Boolean)
    );
    let added = 0;

    picked.forEach(item => {
      const name = item?.name?.toString().trim();
      if (!name) return;
      const key = name.toLowerCase();
      if (existing.has(key)) return;
      blockedSites.push({
        name,
        icon: typeof iconFor === 'function'
          ? iconFor(name)
          : name.replace(/[^a-zA-Z]/g, '').slice(0, 2).toUpperCase() || 'AP'
      });
      existing.add(key);
      added += 1;
    });

    if (added > 0) {
      if (typeof saveBlockedSites === 'function') saveBlockedSites();
      if (typeof renderSites === 'function') renderSites();
      if (typeof renderSettingsSiteList === 'function') renderSettingsSiteList();
      document.dispatchEvent(new Event('studylock:blocklist-changed'));
      showToast(`${added} app${added === 1 ? '' : 's'} added to the block list.`);
    } else {
      showToast('Those apps are already on the block list.');
    }
  }

  installPickerButtons();

  if (window.StudyLockNativeHooks) {
    window.StudyLockNativeHooks.onAppsPicked = applyPickedApps;
  }
})();
