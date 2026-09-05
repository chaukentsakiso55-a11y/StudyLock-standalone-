(() => {
  if (window.__studyLockOfflineDictionaryAttached || !window.StudyLockNative) return;
  window.__studyLockOfflineDictionaryAttached = true;

  const native = window.StudyLockNative;
  const pending = new Map();
  let nextRequestId = 1;
  const originalLookup = typeof window.lookupWord === 'function' ? window.lookupWord.bind(window) : null;

  function nativeLookup(word) {
    return new Promise((resolve, reject) => {
      const requestId = String(nextRequestId++);
      const timeout = setTimeout(() => {
        pending.delete(requestId);
        reject(new Error('The offline dictionary took too long to respond.'));
      }, 12000);
      pending.set(requestId, { resolve, reject, timeout });
      try {
        native.lookupOfflineDictionary(requestId, word);
      } catch (error) {
        clearTimeout(timeout);
        pending.delete(requestId);
        reject(error);
      }
    });
  }

  function showState(mode) {
    if (typeof window.showDictState === 'function') {
      window.showDictState(mode);
      return;
    }
    const loading = document.getElementById('dictLoading');
    const error = document.getElementById('dictError');
    const empty = document.getElementById('dictEmpty');
    const result = document.getElementById('dictResult');
    if (loading) loading.style.display = mode === 'loading' ? 'block' : 'none';
    if (error) error.style.display = mode === 'error' ? 'block' : 'none';
    if (empty) empty.style.display = mode === 'empty' ? 'block' : 'none';
    result?.classList.toggle('show', mode === 'result');
  }

  function renderOffline(entry, requestedWord, definitionAvailable) {
    if (typeof window.renderDictResult === 'function') {
      window.renderDictResult(entry, requestedWord);
      const result = document.getElementById('dictResult');
      if (result && !result.querySelector('.studylock-offline-dict-badge')) {
        const badge = document.createElement('div');
        badge.className = 'studylock-offline-dict-badge';
        badge.textContent = definitionAvailable
          ? '✓ Offline definition — no internet required'
          : '✓ Recognized in the offline English word database';
        badge.style.cssText = 'margin-top:12px;font-size:11px;font-weight:700;color:#ff7a1f;padding:8px 10px;border-radius:10px;background:rgba(255,122,31,.08);border:1px solid rgba(255,122,31,.16)';
        result.appendChild(badge);
      }
      return;
    }

    const result = document.getElementById('dictResult');
    if (!result) return;
    const firstMeaning = (entry.meanings || [])[0] || {};
    const firstDefinition = (firstMeaning.definitions || [])[0] || {};
    result.innerHTML = '<div class="dict-word-row"><div class="dict-word"></div></div>' +
      '<div class="dict-meaning"><div class="dict-pos"></div><ol class="dict-def-list"><li></li></ol></div>';
    result.querySelector('.dict-word').textContent = entry.word || requestedWord;
    result.querySelector('.dict-pos').textContent = firstMeaning.partOfSpeech || 'word';
    result.querySelector('.dict-def-list li').textContent = firstDefinition.definition || 'Definition unavailable.';
    showState('result');
  }

  async function lookupOfflineFirst(rawWord) {
    const word = String(rawWord || '').trim().toLowerCase();
    if (!word) return;
    showState('loading');

    try {
      const raw = await nativeLookup(word);
      const payload = typeof raw === 'string' ? JSON.parse(raw) : raw;
      if (payload?.found && payload.entry) {
        if (!payload.definitionAvailable && navigator.onLine && originalLookup) {
          try {
            await originalLookup(word);
            return;
          } catch (_) {}
        }
        renderOffline(payload.entry, word, payload.definitionAvailable !== false);
        return;
      }

      if (navigator.onLine && originalLookup) {
        await originalLookup(word);
        return;
      }

      const error = document.getElementById('dictError');
      if (error) {
        error.textContent = 'No offline definition was found for "' + word + '". Check the spelling and try again.';
      }
      showState('error');
    } catch (error) {
      if (navigator.onLine && originalLookup) {
        try {
          await originalLookup(word);
          return;
        } catch (_) {}
      }
      const errorEl = document.getElementById('dictError');
      if (errorEl) errorEl.textContent = error?.message || 'The offline dictionary could not answer.';
      showState('error');
    }
  }

  window.lookupWord = lookupOfflineFirst;
  window.studyLockOfflineDictionary = { lookup: lookupOfflineFirst };

  const hooks = window.StudyLockNativeHooks || (window.StudyLockNativeHooks = {});
  const previousDictionaryResult = hooks.onDictionaryResult;
  hooks.onDictionaryResult = function onDictionaryResult(requestId, success, payload, message) {
    if (typeof previousDictionaryResult === 'function') {
      try { previousDictionaryResult(requestId, success, payload, message); } catch (_) {}
    }
    const request = pending.get(String(requestId));
    if (!request) return;
    clearTimeout(request.timeout);
    pending.delete(String(requestId));
    if (success) request.resolve(payload);
    else request.reject(new Error(message || 'Offline dictionary lookup failed.'));
  };

  const input = document.getElementById('dictSearchInput');
  const button = document.getElementById('dictSearchBtn');
  button?.addEventListener('click', event => {
    event.preventDefault();
    event.stopImmediatePropagation();
    lookupOfflineFirst(input?.value || '');
  }, true);
  input?.addEventListener('keydown', event => {
    if (event.key !== 'Enter') return;
    event.preventDefault();
    event.stopImmediatePropagation();
    lookupOfflineFirst(input.value);
  }, true);
})();

(() => {
  if (window.__studyLockOfflineLibraryScriptRequested) return;
  window.__studyLockOfflineLibraryScriptRequested = true;
  const script = document.createElement('script');
  script.src = 'https://appassets.androidplatform.net/assets/studylock-offline-library-ui.js';
  script.async = true;
  document.head.appendChild(script);
})();

(() => {
  if (window.__studyLockFirebaseParentScriptsRequested) return;
  window.__studyLockFirebaseParentScriptsRequested = true;
  const configScript = document.createElement('script');
  configScript.src = 'https://appassets.androidplatform.net/assets/studylock-firebase-parent-config.js';
  configScript.onload = () => {
    const parentScript = document.createElement('script');
    parentScript.src = 'https://appassets.androidplatform.net/assets/studylock-firebase-parent.js';
    parentScript.async = true;
    document.head.appendChild(parentScript);
  };
  document.head.appendChild(configScript);
})();
