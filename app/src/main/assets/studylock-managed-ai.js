(() => {
  if (window.__studyLockManagedAiUiApplied) return;
  window.__studyLockManagedAiUiApplied = true;

  const PERSONAL_KEY_STORAGE = 'studylock_openrouter_api_key';
  const PERSONAL_MODEL_STORAGE = 'studylock_openrouter_model';

  function clearPersonalProviderState() {
    try {
      localStorage.removeItem(PERSONAL_KEY_STORAGE);
      localStorage.removeItem(PERSONAL_MODEL_STORAGE);
    } catch (_) {}
  }

  function hideKeyControls() {
    const ids = [
      'topbarApiKeyBtn',
      'apiKeySection',
      'apiKeyInput',
      'toggleKeyVisibilityBtn',
      'saveApiKeyBtn',
      'clearApiKeyBtn'
    ];

    ids.forEach(id => {
      const element = document.getElementById(id);
      if (!element) return;
      element.style.display = 'none';
      element.setAttribute('aria-hidden', 'true');
      if ('disabled' in element) element.disabled = true;
    });

    document.documentElement.dataset.studylockManagedAi = 'connected';
  }

  clearPersonalProviderState();
  hideKeyControls();

  const observer = new MutationObserver(() => {
    clearPersonalProviderState();
    hideKeyControls();
  });
  observer.observe(document.documentElement, {
    childList: true,
    subtree: true,
    attributes: false
  });

  window.addEventListener('storage', event => {
    if (event.key === PERSONAL_KEY_STORAGE || event.key === PERSONAL_MODEL_STORAGE) {
      clearPersonalProviderState();
    }
  });
})();
