(() => {
  if (window.__studyLockSessionHardeningAttached) return;
  window.__studyLockSessionHardeningAttached = true;

  const SCHEDULE_KEY = 'studylock_parent_schedule_v3';

  function schedule() {
    try { return JSON.parse(localStorage.getItem(SCHEDULE_KEY) || '{}'); }
    catch (_) { return {}; }
  }

  function currentMinute() {
    const now = new Date();
    return now.getHours() * 60 + now.getMinutes();
  }

  function enforcedNow() {
    const s = schedule();
    if (!s?.enabled) return false;
    const start = Number(s.startMinute);
    const end = Number(s.endMinute);
    if (!Number.isFinite(start) || !Number.isFinite(end)) return false;
    const now = currentMinute();
    if (start === end) return true;
    return start < end ? now >= start && now < end : now >= start || now < end;
  }

  function secondsToEnd() {
    const s = schedule();
    const end = Number(s.endMinute);
    if (!Number.isFinite(end)) return 0;
    const now = new Date();
    const nowSeconds = now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds();
    let target = end * 60;
    if (target <= nowSeconds) target += 24 * 3600;
    return Math.max(1, target - nowSeconds);
  }

  function focusActive() {
    return document.getElementById('hero')?.classList.contains('locked') === true;
  }

  function removePauseUi() {
    Array.from(document.querySelectorAll('button,a,[role="button"]')).forEach(node => {
      const label = `${node.textContent || ''} ${node.getAttribute('aria-label') || ''}`.trim().toLowerCase();
      if (label.includes('pause') && !label.includes('music')) node.remove();
    });
  }

  function forceRunningState() {
    if (!focusActive() || !enforcedNow()) return;
    try {
      if (typeof state !== 'undefined') {
        const remaining = secondsToEnd();
        state.isLocked = true;
        state.isPaused = false;
        if ((Number(state.remainingSeconds) || 0) < remaining - 3) {
          state.remainingSeconds = remaining;
          state.totalSeconds = Math.max(Number(state.totalSeconds) || 0, remaining);
        }
      }
      if (typeof updateLockVisuals === 'function') updateLockVisuals();
      if (typeof render === 'function') render();
    } catch (_) {}

    try {
      const blocked = typeof blockedSites !== 'undefined' && Array.isArray(blockedSites)
        ? blockedSites.map(site => site?.name?.trim()).filter(Boolean)
        : [];
      window.StudyLockNative?.onFocusState(true, false, secondsToEnd(), JSON.stringify(blocked));
    } catch (_) {}
  }

  function blockStudentEnd(event) {
    if (!focusActive() || !enforcedNow() || window.__studyLockTrustedParentCommand === true) return false;
    event?.preventDefault?.();
    event?.stopPropagation?.();
    event?.stopImmediatePropagation?.();
    window.StudyLockNativeHooks?.showToast?.('Parent Auto Study is active. This session ends at the parent-set end time.');
    forceRunningState();
    return true;
  }

  document.addEventListener('click', event => {
    const target = event.target instanceof Element ? event.target : null;
    const label = `${target?.closest('button,a,[role="button"]')?.textContent || ''}`.toLowerCase();
    if (label.includes('pause')) {
      event.preventDefault();
      event.stopPropagation();
      event.stopImmediatePropagation();
      window.StudyLockNativeHooks?.showToast?.('StudyLock sessions cannot be paused.');
      return;
    }
    const action = target?.closest('#mainAction,.main-action,#endSessionBtn,[data-action="end"]');
    if (action) blockStudentEnd(event);
  }, true);

  if (typeof window.pauseSession === 'function') {
    window.pauseSession = function studyLockNoPause() {
      window.StudyLockNativeHooks?.showToast?.('StudyLock sessions cannot be paused.');
    };
  }

  const originalComplete = typeof window.completeSession === 'function' ? window.completeSession.bind(window) : null;
  if (originalComplete) {
    window.completeSession = function scheduleAwareCompleteSession(...args) {
      if (focusActive() && enforcedNow() && window.__studyLockTrustedParentCommand !== true) {
        forceRunningState();
        window.StudyLockNativeHooks?.showToast?.('Study time continues until the parent-set end time.');
        return;
      }
      return originalComplete(...args);
    };
  }

  const originalEndEarly = typeof window.endSessionEarly === 'function' ? window.endSessionEarly.bind(window) : null;
  if (originalEndEarly) {
    window.endSessionEarly = function scheduleAwareEndSession(...args) {
      if (focusActive() && enforcedNow() && window.__studyLockTrustedParentCommand !== true) {
        forceRunningState();
        window.StudyLockNativeHooks?.showToast?.('Only an authorized parent/control command can end this scheduled session early.');
        return;
      }
      return originalEndEarly(...args);
    };
  }

  const observer = new MutationObserver(() => {
    removePauseUi();
    if (enforcedNow()) forceRunningState();
  });
  observer.observe(document.body, { childList: true, subtree: true });

  removePauseUi();
  forceRunningState();
  setInterval(() => {
    removePauseUi();
    forceRunningState();
  }, 1000);

  window.StudyLockSessionHardening = {
    isParentWindowActive: enforcedNow,
    forceRunningState
  };
})();
