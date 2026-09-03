(() => {
  if (window.__studyLockNativeBridgeAttached || !window.StudyLockNative) return;
  window.__studyLockNativeBridgeAttached = true;

  const native = window.StudyLockNative;
  const cloudKeys = [
    'studylock_auth_user',
    'studylock_planner_tasks',
    'studylock_notes',
    'studylock_rewards',
    'studylock_music_volume',
    'studylock_music_playing'
  ];
  let syncTimer = null;
  let lastCloudPayload = '';
  let lastFocusPayload = '';
  let lastMusicPayload = '';
  let nextTutorRequestId = 1;
  const tutorRequests = new Map();

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
        native.requestTutor(requestId, JSON.stringify({ apiKey, model, body }));
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
      showToast('Firebase needs the rotated StudyLock key. Local mode is still available.');
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
      showToast('Firebase needs the rotated StudyLock key. Local mode is still available.');
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

  function finishAuthentication(message, name, email) {
    try {
      localStorage.setItem('studylock_auth_seen', 'true');
      if (email || name) {
        localStorage.setItem('studylock_auth_user', JSON.stringify({ name, email }));
      }
    } catch (_) {}
    document.getElementById('authOverlay')?.classList.remove('show');
    showToast(message);
    scheduleCloudSync();
  }

  function parseTimer() {
    const value = document.getElementById('timerDisplay')?.textContent?.trim() || '00:00';
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

  function syncFocus() {
    const active = document.getElementById('hero')?.classList.contains('locked') || false;
    const paused = (document.getElementById('statusLabel')?.textContent || '')
      .toLowerCase()
      .includes('paused');
    const remainingSeconds = parseTimer();
    const blocked = blockedEntries();
    const payload = JSON.stringify({ active, paused, remainingSeconds, blocked });
    if (payload === lastFocusPayload) return;
    lastFocusPayload = payload;
    native.onFocusState(active, paused, remainingSeconds, JSON.stringify(blocked));
  }

  function syncMusic() {
    const playing = document.getElementById('musicToggleBtn')?.classList.contains('playing') || false;
    const trackName = document.getElementById('currentTrackName')?.textContent?.trim() || '';
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
      focusActive: document.getElementById('hero')?.classList.contains('locked') || false,
      focusPaused: (document.getElementById('statusLabel')?.textContent || '').toLowerCase().includes('paused'),
      remainingSeconds: parseTimer(),
      blockedEntries: blockedEntries(),
      todayMinutes: document.getElementById('statToday')?.textContent || '0m',
      sessionsCompleted: Number(document.getElementById('statSessions')?.textContent || 0),
      streak: Number(document.getElementById('streakCount')?.textContent || 0),
      localState: safeStoredState(),
      clientUpdatedAt: new Date().toISOString()
    });
  }

  function syncCloudNow() {
    syncTimer = null;
    const payload = cloudPayload();
    const stablePayload = payload.replace(/"clientUpdatedAt":"[^"]+"/, '"clientUpdatedAt":""');
    if (stablePayload === lastCloudPayload) return;
    lastCloudPayload = stablePayload;
    native.syncState(payload);
  }

  function scheduleCloudSync() {
    clearTimeout(syncTimer);
    syncTimer = setTimeout(syncCloudNow, 1200);
  }

  function syncAll() {
    syncFocus();
    syncMusic();
    scheduleCloudSync();
  }

  const observed = [
    document.getElementById('hero'),
    document.getElementById('timerDisplay'),
    document.getElementById('statusLabel'),
    document.getElementById('siteList'),
    document.getElementById('musicToggleBtn'),
    document.getElementById('currentTrackName'),
    document.getElementById('statToday'),
    document.getElementById('statSessions')
  ].filter(Boolean);

  const observer = new MutationObserver(syncAll);
  observed.forEach(element => observer.observe(element, {
    attributes: true,
    childList: true,
    characterData: true,
    subtree: true
  }));

  document.addEventListener('click', () => setTimeout(syncAll, 180), false);
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') syncCloudNow();
    else syncAll();
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
        const state = JSON.parse(rawState);
        if (!state.firebaseConfigured) {
          console.info('StudyLock Firebase is awaiting the rotated API key.');
        }
        if (state.focusActive && !state.accessibilityEnabled) {
          showToast('Focus is active, but Android app blocking still needs Accessibility access.');
        }
      } catch (_) {}
    }
  };

  syncAll();
  try { window.StudyLockNativeHooks.onNativeState(native.getNativeState()); }
  catch (_) {}
})();
