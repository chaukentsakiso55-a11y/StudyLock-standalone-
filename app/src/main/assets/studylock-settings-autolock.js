(() => {
  if (window.__studyLockSettingsAutolockAttached) return;
  window.__studyLockSettingsAutolockAttached = true;

  const backdrop = document.getElementById('settingsBackdrop');
  if (!backdrop) return;

  let wasOpen = false;

  function isOpen() {
    if (backdrop.classList.contains('show') || backdrop.classList.contains('active') || backdrop.classList.contains('open')) {
      return true;
    }
    const style = getComputedStyle(backdrop);
    return style.display !== 'none' && style.visibility !== 'hidden' && Number(style.opacity || 1) > 0;
  }

  function lockNow() {
    try {
      if (typeof settingsUnlocked !== 'undefined') settingsUnlocked = false;
    } catch (_) {}
    try { localStorage.setItem('studylock_settings_locked', 'true'); } catch (_) {}
  }

  function watch() {
    const open = isOpen();
    if (wasOpen && !open) lockNow();
    wasOpen = open;
  }

  const observer = new MutationObserver(watch);
  observer.observe(backdrop, { attributes: true, attributeFilter: ['class', 'style'] });

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState !== 'visible') lockNow();
  });
  window.addEventListener('pagehide', lockNow);

  // Force the first Settings visit after launch to use the password gate when one exists.
  lockNow();
  wasOpen = isOpen();

  window.StudyLockSettingsLock = {
    lockNow,
    isLocked() {
      try { return typeof settingsUnlocked === 'undefined' ? true : !settingsUnlocked; }
      catch (_) { return true; }
    }
  };
})();
