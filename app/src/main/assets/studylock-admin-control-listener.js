(() => {
  if (window.__studyLockAdminControlListenerAttached) return;
  window.__studyLockAdminControlListenerAttached = true;

  const TOKEN_KEY = 'studylock_control_channel_token_v1';
  const APP_NAME = 'studylock-global-control-listener';
  let controlToken = '';
  let unsubscribe = null;

  function createToken() {
    const bytes = new Uint8Array(32);
    if (window.crypto?.getRandomValues) window.crypto.getRandomValues(bytes);
    else for (let i = 0; i < bytes.length; i++) bytes[i] = Math.floor(Math.random() * 256);
    return Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('');
  }

  function token() {
    if (controlToken) return controlToken;
    try { controlToken = localStorage.getItem(TOKEN_KEY) || ''; } catch (_) {}
    if (!/^[a-f0-9]{64}$/.test(controlToken)) {
      controlToken = createToken();
      try { localStorage.setItem(TOKEN_KEY, controlToken); } catch (_) {}
    }
    return controlToken;
  }

  function syncToken() {
    try {
      const profile = window.StudyLockTerm3System?.getProfile?.() || {};
      window.StudyLockNative?.syncState(JSON.stringify({
        controlChannelToken: token(),
        profileName: profile.name || '',
        grade: Number(profile.grade) || 10,
        term: Number(profile.term) || 3,
        controlChannelReady: true,
        clientUpdatedAt: new Date().toISOString()
      }));
    } catch (_) {}
  }

  function loadScript(src) {
    return new Promise((resolve, reject) => {
      const existing = Array.from(document.scripts).find(script => script.src === src);
      if (existing && window.firebase) return resolve();
      const script = document.createElement('script');
      script.src = src;
      script.async = true;
      script.onload = resolve;
      script.onerror = reject;
      document.head.appendChild(script);
    });
  }

  async function waitForConfig() {
    for (let i = 0; i < 50; i++) {
      if (window.__STUDYLOCK_FIREBASE_PARENT_CONFIG?.apiKey) return window.__STUDYLOCK_FIREBASE_PARENT_CONFIG;
      await new Promise(resolve => setTimeout(resolve, 100));
    }
    throw new Error('StudyLock Firebase control config is unavailable.');
  }

  async function ensureFirebase() {
    const config = await waitForConfig();
    if (!window.firebase?.initializeApp) {
      await loadScript('https://www.gstatic.com/firebasejs/10.14.1/firebase-app-compat.js');
    }
    if (!window.firebase?.auth) {
      await loadScript('https://www.gstatic.com/firebasejs/10.14.1/firebase-auth-compat.js');
    }
    if (!window.firebase?.firestore) {
      await loadScript('https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore-compat.js');
    }
    const app = window.firebase.apps.find(item => item.name === APP_NAME) || window.firebase.initializeApp(config, APP_NAME);
    const auth = app.auth();
    if (!auth.currentUser) await auth.signInAnonymously();
    return app.firestore();
  }

  async function connect() {
    syncToken();
    const db = await ensureFirebase();
    unsubscribe?.();
    unsubscribe = db.collection('studylock_control_channels').doc(token()).onSnapshot(snapshot => {
      if (!snapshot.exists) return;
      const data = snapshot.data() || {};
      window.StudyLockTerm3System?.applyRemoteControl?.(data);
    }, error => {
      console.warn('StudyLock Control listener unavailable', error);
    });
  }

  token();
  syncToken();
  connect().catch(error => console.warn('StudyLock Control listener could not connect', error));
  setInterval(syncToken, 30000);
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') syncToken();
  });
  window.addEventListener('beforeunload', () => unsubscribe?.(), { once: true });

  window.StudyLockAdminControl = {
    getToken: token,
    sync: syncToken,
    reconnect: connect
  };
})();
