(() => {
  if (window.__studyLockManagedAiUiAttached || !window.StudyLockNative) return;
  window.__studyLockManagedAiUiAttached = true;

  function refreshManagedAiUi(nativeState) {
    const section = document.getElementById('apiKeySection');
    const hint = section?.querySelector('.settings-hint');
    const status = document.getElementById('apiKeyStatusSub');

    if (hint) {
      hint.textContent = 'StudyLock AI connects automatically through Firebase authentication. A personal Gemini or OpenRouter key is optional and is only used as a fallback.';
    }

    if (nativeState?.managedAiConnected && status) {
      status.textContent = 'StudyLock managed AI connected ✓';
    } else if (nativeState?.firebaseConfigured && status && !status.textContent?.includes('key saved')) {
      status.textContent = 'Managed AI will connect automatically when you sign in or use the tutor.';
    }
  }

  const hooks = window.StudyLockNativeHooks || (window.StudyLockNativeHooks = {});
  const previous = hooks.onNativeState;
  hooks.onNativeState = function managedAiNativeState(rawState) {
    if (typeof previous === 'function') previous(rawState);
    try {
      refreshManagedAiUi(JSON.parse(rawState));
    } catch (_) {
      refreshManagedAiUi(null);
    }
  };

  refreshManagedAiUi(null);
})();
