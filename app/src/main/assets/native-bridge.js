(() => {
  if (window.__studyLockNativeBridgeAttached || !window.StudyLockNative) return;
  window.__studyLockNativeBridgeAttached = true;

  const native = window.StudyLockNative;
  const FOCUS_SYNC_INTERVAL_MS = 5000;
  const CLOUD_SYNC_INTERVAL_MS = 15000;
  const cloudKeys = [
    'studylock_auth_user',
    'studylock_planner_tasks',
    'studylock_notes',
    'studylock_rewards',
    'studylock_music_volume',
    'studylock_music_playing'
  ];

  let cloudTimer = null;
  let focusTimer = null;
  let animationQueued = false;
  let lastCloudPayload = '';
  let lastFocusPayload = '';
  let lastFocusStructural = '';
  let lastFocusSentAt = 0;
  let lastMusicPayload = '';
  let nextTutorRequestId = 1;
  const tutorRequests = new Map();

  const heroEl = document.getElementById('hero');
  const timerEl = document.getElementById('timerDisplay');
  const statusEl = document.getElementById('statusLabel');
  const siteListEl = document.getElementById('siteList');
  const musicToggleEl = document.getElementById('musicToggleBtn');
  const trackNameEl = document.getElementById('currentTrackName');
  const statTodayEl = document.getElementById('statToday');
  const statSessionsEl = document.getElementById('statSessions');

  function showToast(message) {
    const toast = document.getElementById('toast');
    if (!toast) return;
    toast.textContent = message;
    toast.classList.add('show');
    clearTimeout(toast.__nativeTimer);
    toast.__nativeTimer = setTimeout(() => toast.classList.remove('show'), 2600);
  }

  function setError(id, message) {
    const error = document.getElementById(id);
    if (!error) return;
    error.textContent = message;
    error.classList.add('show');
  }

  function clearError(id) {
    document.getElementById(id)?.classList.remove('show');
  }

  function setAuthBusy(kind, busy) {
    const button = document.getElementById(kind === 'signup' ? 'signupSubmitBtn' : 'loginSubmitBtn');
    if (!button) return;
    button.disabled = busy;
    button.textContent = busy
      ? 'Connecting…'
      : kind === 'signup' ? 'Create account' : 'Log in';
  }

  function firebaseEnabled() {
    try { return native.isFirebaseConfigured(); }
    catch (_) { return false; }
  }

  function refreshPersonalKeyLabel() {
    try {
      const key = localStorage.getItem('studylock_openrouter_api_key') || '';
      if (!key.startsWith('AQ.')) return;
      const status = document.getElementById('apiKeyStatusSub');
      if (status) status.textContent = `Gemini auth key saved (•••• ${key.slice(-4)})`;
    } catch (_) {}
  }

  window.studyLockNativeAI = function requestNativeAI(body, overrideApiKey) {
    return new Promise((resolve, reject) => {
      const requestId = String(nextTutorRequestId++);
      let apiKey = overrideApiKey || '';
      let model = 'openrouter/free';
      try {
        apiKey = apiKey || localStorage.getItem('studylock_openrouter_api_key') || '';
        model = localStorage.getItem('studylock_openrouter_model') || model;
      } catch (_) {}

      const timeout = setTimeout(() => {
        tutorRequests.delete(requestId);
        reject(new Error('The AI provider timed out. Check your connection and try again.'));
      }, 45000);
      tutorRequests.set(requestId, { resolve, reject, timeout });

      try {
        native.requestTutor(requestId, JSON.stringify({
          apiKey,
          model,
          body,
          preferPersonal: Boolean(apiKey)
        }));
      } catch (error) {
        clearTimeout(timeout);
        tutorRequests.delete(requestId);
        reject(error);
      }
    });
  };

  const signupButton = document.getElementById('signupSubmitBtn');
  signupButton?.addEventListener('click', event => {
    if (!firebaseEnabled()) {
      showToast('Firebase is not configured. Local mode is still available.');
      return;
    }
    const name = document.getElementById('signupName')?.value.trim() || '';
    const email = document.getElementById('signupEmail')?.value.trim() || '';
    const password = document.getElementById('signupPassword')?.value || '';
    if (!name || !email || password.length < 6) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    clearError('signupError');
    setAuthBusy('signup', true);
    native.signUp(name, email, password);
  }, true);

  const loginButton = document.getElementById('loginSubmitBtn');
  loginButton?.addEventListener('click', event => {
    if (!firebaseEnabled()) {
      showToast('Firebase is not configured. Local mode is still available.');
      return;
    }
    const email = document.getElementById('loginEmail')?.value.trim() || '';
    const password = document.getElementById('loginPassword')?.value || '';
    if (!email || !password) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    clearError('loginError');
    setAuthBusy('login', true);
    native.signIn(email, password);
  }, true);

  document.getElementById('authGuestBtn')?.addEventListener('click', () => {
    if (firebaseEnabled()) native.continueAsGuest();
  }, true);

  document.getElementById('saveApiKeyBtn')?.addEventListener('click', () => {
    setTimeout(refreshPersonalKeyLabel, 120);
  });

  function finishAuthentication(message, name, email) {
    try {
      localStorage.setItem('studylock_auth_seen', 'true');
      if (email || name) {
        localStorage.setItem('studylock_auth_user', JSON.stringify({ name, email }));
      }
    } catch (_) {}
    document.getElementById('authOverlay')?.classList.remove('show');
    showToast(message);
    scheduleCloudSync(500);
  }

  function parseTimer() {
    const value = timerEl?.textContent?.trim() || '00:00';
    const parts = value.split(':').map(Number);
    return parts.length === 2 && parts.every(Number.isFinite)
      ? parts[0] * 60 + parts[1]
      : 0;
  }

  function blockedEntries() {
    return Array.from(document.querySelectorAll('#siteList .site-name'))
      .map(element => element.textContent?.trim())
      .filter(Boolean);
  }

  function focusSnapshot() {
    const active = heroEl?.classList.contains('locked') || false;
    const paused = (statusEl?.textContent || '').toLowerCase().includes('paused');
    const remainingSeconds = parseTimer();
    const blocked = blockedEntries();
    return { active, paused, remainingSeconds, blocked };
  }

  function syncFocus(force = false) {
    const snapshot = focusSnapshot();
    const payload = JSON.stringify(snapshot);
    if (!force && payload === lastFocusPayload) return;

    const structural = JSON.stringify({
      active: snapshot.active,
      paused: snapshot.paused,
      blocked: snapshot.blocked
    });
    const now = Date.now();
    const structuralChanged = structural !== lastFocusStructural;
    const due = now - lastFocusSentAt >= FOCUS_SYNC_INTERVAL_MS;
    const urgent = structuralChanged || snapshot.remainingSeconds === 0 || !snapshot.active;

    lastFocusPayload = payload;
    if (force || urgent || due) {
      clearTimeout(focusTimer);
      focusTimer = null;
      lastFocusStructural = structural;
      lastFocusSentAt = now;
      native.onFocusState(
        snapshot.active,
        snapshot.paused,
        snapshot.remainingSeconds,
        JSON.stringify(snapshot.blocked)
      );
      return;
    }

    if (!focusTimer) {
      const delay = Math.max(250, FOCUS_SYNC_INTERVAL_MS - (now - lastFocusSentAt));
      focusTimer = setTimeout(() => {
        focusTimer = null;
        syncFocus(true);
      }, delay);
    }
  }

  function syncMusic() {
    const playing = musicToggleEl?.classList.contains('playing') || false;
    const trackName = trackNameEl?.textContent?.trim() || '';
    const payload = JSON.stringify({ playing, trackName });
    if (payload === lastMusicPayload) return;
    lastMusicPayload = payload;
    native.onMusicState(playing, trackName);
  }

  function safeStoredState() {
    const result = {};
    cloudKeys.forEach(key => {
      try {
        const value = localStorage.getItem(key);
        if (value !== null) result[key] = value;
      } catch (_) {}
    });
    return result;
  }

  function cloudPayload() {
    return JSON.stringify({
      focusActive: heroEl?.classList.contains('locked') || false,
      focusPaused: (statusEl?.textContent || '').toLowerCase().includes('paused'),
      remainingSeconds: parseTimer(),
      blockedEntries: blockedEntries(),
      todayMinutes: statTodayEl?.textContent || '0m',
      sessionsCompleted: Number(statSessionsEl?.textContent || 0),
      streak: Number(document.getElementById('streakCount')?.textContent || 0),
      localState: safeStoredState(),
      clientUpdatedAt: new Date().toISOString()
    });
  }

  function syncCloudNow() {
    clearTimeout(cloudTimer);
    cloudTimer = null;
    const payload = cloudPayload();
    const stablePayload = payload.replace(/"clientUpdatedAt":"[^"]+"/, '"clientUpdatedAt":""');
    if (stablePayload === lastCloudPayload) return;
    lastCloudPayload = stablePayload;
    native.syncState(payload);
  }

  function scheduleCloudSync(delay = CLOUD_SYNC_INTERVAL_MS) {
    if (cloudTimer) return;
    cloudTimer = setTimeout(syncCloudNow, delay);
  }

  function runBatchedSync() {
    animationQueued = false;
    syncFocus(false);
    syncMusic();
    scheduleCloudSync();
  }

  function queueBatchedSync() {
    if (animationQueued) return;
    animationQueued = true;
    if (typeof requestAnimationFrame === 'function') {
      requestAnimationFrame(runBatchedSync);
    } else {
      setTimeout(runBatchedSync, 16);
    }
  }

  const observer = new MutationObserver(queueBatchedSync);
  [heroEl, timerEl, statusEl, siteListEl, musicToggleEl, trackNameEl, statTodayEl, statSessionsEl]
    .filter(Boolean)
    .forEach(element => observer.observe(element, {
      attributes: true,
      childList: true,
      characterData: true,
      subtree: true
    }));

  document.addEventListener('click', () => {
    queueBatchedSync();
    if (!cloudTimer) scheduleCloudSync(1200);
  }, { passive: true });

  document.addEventListener('studylock:blocklist-changed', () => {
    syncFocus(true);
    scheduleCloudSync(500);
  });

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      syncFocus(true);
      syncCloudNow();
    } else {
      queueBatchedSync();
    }
  });

  function wireNativeMicrophone(id, target) {
    document.getElementById(id)?.addEventListener('click', event => {
      event.preventDefault();
      event.stopImmediatePropagation();
      native.startSpeech(target);
    }, true);
  }

  wireNativeMicrophone('chatMicBtn', 'chat');
  wireNativeMicrophone('quizMicBtn', 'quiz');

  function restoreFocusFromNative(nativeState) {
    if (!nativeState || !nativeState.focusActive) return;
    try {
      if (typeof state === 'undefined') return;
      const remaining = Math.max(0, Number(nativeState.focusRemainingSeconds) || 0);
      state.isLocked = true;
      state.isPaused = !!nativeState.focusPaused;
      state.remainingSeconds = remaining;
      state.totalSeconds = Math.max(Number(state.totalSeconds) || 0, remaining);
      if (typeof updateLockVisuals === 'function') updateLockVisuals();
      if (typeof render === 'function') render();
      if (
        typeof tickHandle !== 'undefined' &&
        !tickHandle &&
        remaining > 0 &&
        typeof tick === 'function'
      ) {
        tickHandle = setInterval(tick, 1000);
      }
    } catch (error) {
      console.warn('StudyLock could not restore the native focus session.', error);
    }
  }

  window.StudyLockNativeHooks = {
    showToast,
    onAuthResult(success, message, name, email) {
      setAuthBusy('signup', false);
      setAuthBusy('login', false);
      if (success) {
        finishAuthentication(message, name, email);
      } else {
        const signupVisible = document.getElementById('authSignup')?.classList.contains('active');
        setError(signupVisible ? 'signupError' : 'loginError', message);
      }
    },
    onSpeechListening(target) {
      const button = document.getElementById(target === 'quiz' ? 'quizMicBtn' : 'chatMicBtn');
      button?.classList.add('listening');
      showToast('Listening…');
    },
    onSpeechResult(target, text) {
      const input = document.getElementById(target === 'quiz' ? 'quizTopicInput' : 'chatInput');
      if (input) {
        input.value = text;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.focus();
      }
      document.querySelectorAll('.mic-btn.listening').forEach(button => button.classList.remove('listening'));
      showToast('Voice input ready');
    },
    onSpeechError(message) {
      document.querySelectorAll('.mic-btn.listening').forEach(button => button.classList.remove('listening'));
      showToast(message);
    },
    onTutorResult(requestId, success, answer, message) {
      const pending = tutorRequests.get(String(requestId));
      if (!pending) return;
      clearTimeout(pending.timeout);
      tutorRequests.delete(String(requestId));
      if (success) pending.resolve(answer);
      else pending.reject(new Error(message || 'The AI provider could not answer.'));
    },
    onNativeState(rawState) {
      try {
        const nativeState = JSON.parse(rawState);
        restoreFocusFromNative(nativeState);
        if (!nativeState.firebaseConfigured) {
          console.info('StudyLock Firebase is not configured.');
        } else {
          document.documentElement.dataset.studylockManagedAi = 'connected';
          const section = document.getElementById('apiKeySection');
          const hint = section?.querySelector('.settings-hint');
          if (section) section.style.display = 'block';
          if (hint) {
            hint.textContent = 'StudyLock AI is connected automatically. A personal Gemini or OpenRouter key is optional and, when saved, will be tried first.';
          }
        }
        refreshPersonalKeyLabel();
        if (nativeState.focusActive && !nativeState.accessibilityEnabled) {
          showToast('Focus is active, but Android app blocking still needs Accessibility access.');
        }
      } catch (_) {}
    }
  };

  window.StudyLockNativePerf = {
    flushFocus: () => syncFocus(true),
    flushCloud: syncCloudNow
  };

  try { window.StudyLockNativeHooks.onNativeState(native.getNativeState()); }
  catch (_) {}
  refreshPersonalKeyLabel();
  syncFocus(true);
  syncMusic();
  scheduleCloudSync(1200);
})();
