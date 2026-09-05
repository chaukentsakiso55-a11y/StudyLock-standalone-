(() => {
  if (window.__studyLockFirebaseParentAttached) return;
  window.__studyLockFirebaseParentAttached = true;

  const CHANNELS = 'studylock_parent_channels';
  const APP_NAME = 'studylock-parent-sync-webview';
  const AUTO_KEY = 'studylock_parent_auto_v2';
  const LAST_AUTO_KEY = 'studylock_parent_auto_last_date_v2';
  const LAST_START_KEY = 'studylock_parent_last_start_request_v2';
  const LAST_END_KEY = 'studylock_parent_last_end_request_v2';
  let app;
  let auth;
  let db;
  let currentUid = '';
  let messageUnsubscribe = null;
  let channelUnsubscribe = null;

  function loadScript(src) {
    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = src;
      script.async = true;
      script.onload = resolve;
      script.onerror = reject;
      document.head.appendChild(script);
    });
  }

  const ready = (async () => {
    await loadScript('https://www.gstatic.com/firebasejs/10.14.1/firebase-app-compat.js');
    await loadScript('https://www.gstatic.com/firebasejs/10.14.1/firebase-auth-compat.js');
    await loadScript('https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore-compat.js');
    const config = window.__STUDYLOCK_FIREBASE_PARENT_CONFIG;
    if (!config?.apiKey || !config?.projectId) throw new Error('Firebase parent sync config missing');
    app = window.firebase.apps.find(item => item.name === APP_NAME) || window.firebase.initializeApp(config, APP_NAME);
    auth = app.auth();
    db = app.firestore();
    if (!auth.currentUser) await auth.signInAnonymously();
    currentUid = auth.currentUser?.uid || '';
    return true;
  })();

  function topicFor(code) {
    return 'studylock-pair-' + code;
  }

  function codeFromTopic(topic) {
    const match = /^studylock-pair-(\d{6})$/.exec(String(topic || ''));
    return match ? match[1] : '';
  }

  function toPlain(value) {
    if (Array.isArray(value)) return value.map(toPlain);
    if (value && typeof value === 'object') {
      const output = {};
      Object.keys(value).forEach(key => { output[key] = toPlain(value[key]); });
      return output;
    }
    return value;
  }

  async function addMessage(channel, value, role) {
    await channel.collection('messages').add({
      senderUid: currentUid,
      senderRole: role || 'student',
      type: value?.type || '',
      payload: JSON.stringify(value || {}),
      createdAtMs: Date.now()
    });
  }

  async function firebaseRelayPublish(topic, value) {
    await ready;
    const code = codeFromTopic(topic);
    if (!code) return;
    const channel = db.collection(CHANNELS).doc(code);
    const data = value || {};

    if (data.type === 'hello') {
      const now = Date.now();
      await db.runTransaction(async transaction => {
        const snapshot = await transaction.get(channel);
        if (!snapshot.exists) throw new Error('Pairing code not found');
        const channelData = snapshot.data() || {};
        if (channelData.expiresAtMs && channelData.expiresAtMs < now) throw new Error('Pairing code expired');
        if (channelData.studentUid && channelData.studentUid !== currentUid) throw new Error('Pairing code already connected');
        transaction.set(channel, {
          studentUid: currentUid,
          connected: true,
          studentOnline: true,
          lastStudentSeenMs: now
        }, { merge: true });
      });
      if (data.state) {
        await channel.set({ studentState: toPlain(data.state), studentOnline: true, lastStudentSeenMs: Date.now() }, { merge: true });
      }
      await addMessage(channel, data, 'student');
      return;
    }

    const snapshot = await channel.get();
    const channelData = snapshot.data() || {};
    if (channelData.studentUid !== currentUid) throw new Error('Student is not paired to this parent dashboard');

    if (data.type === 'state') {
      await channel.set({ studentState: toPlain(data.state || {}), studentOnline: true, lastStudentSeenMs: Date.now() }, { merge: true });
      return;
    }
    if (data.type === 'bye') {
      await channel.set({ studentOnline: false, lastStudentSeenMs: Date.now() }, { merge: true });
    }
    await addMessage(channel, data, 'student');
  }

  function selectMinutes(minutes) {
    const wanted = Math.max(25, Math.min(300, Number(minutes || 25)));
    const buttons = Array.from(document.querySelectorAll('.preset-btn'));
    let button = buttons.find(item => parseInt(item.dataset.mins || '0', 10) === wanted);
    if (!button && buttons.length) {
      button = buttons.reduce((best, current) => {
        const a = Math.abs(parseInt(best.dataset.mins || '25', 10) - wanted);
        const b = Math.abs(parseInt(current.dataset.mins || '25', 10) - wanted);
        return b < a ? current : best;
      }, buttons[0]);
    }
    button?.click();
  }

  function focusActive() {
    return document.getElementById('hero')?.classList.contains('locked') === true;
  }

  function startStudy(minutes, message) {
    if (focusActive()) return;
    selectMinutes(minutes);
    if (typeof startSession === 'function') startSession();
    window.StudyLockNativeHooks?.showToast?.(message || 'Focus session started from the parent dashboard.');
  }

  function endStudy() {
    if (!focusActive()) return;
    if (typeof endSessionEarly === 'function') endSessionEarly();
    window.StudyLockNativeHooks?.showToast?.('Focus session ended from the parent dashboard.');
  }

  function storeAuto(data) {
    const settings = {
      enabled: !!data.autoStudyEnabled,
      minutes: Math.max(25, Math.min(300, Number(data.autoStudyMinutes || 25))),
      startMinuteOfDay: Number(data.autoStudyStartMinuteOfDay ?? -1)
    };
    localStorage.setItem(AUTO_KEY, JSON.stringify(settings));
  }

  function maybeApplyAutoStudy() {
    let settings;
    try { settings = JSON.parse(localStorage.getItem(AUTO_KEY) || 'null'); } catch (_) { settings = null; }
    if (!settings?.enabled || focusActive()) return;
    const scheduled = Number(settings.startMinuteOfDay);
    if (!Number.isFinite(scheduled) || scheduled < 0 || scheduled > 1439) return;
    const now = new Date();
    const current = now.getHours() * 60 + now.getMinutes();
    if (current < scheduled) return;
    const dateKey = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    if (localStorage.getItem(LAST_AUTO_KEY) === dateKey) return;
    localStorage.setItem(LAST_AUTO_KEY, dateKey);
    startStudy(settings.minutes, 'Auto Study started from the parent schedule.');
  }

  function applyControls(data) {
    storeAuto(data || {});
    const startId = String(data.startRequestId || '');
    if (startId && startId !== localStorage.getItem(LAST_START_KEY)) {
      localStorage.setItem(LAST_START_KEY, startId);
      startStudy(Number(data.startRequestMinutes || data.autoStudyMinutes || 25), 'Focus session started from the parent dashboard.');
    }
    const endId = String(data.endRequestId || '');
    if (endId && endId !== localStorage.getItem(LAST_END_KEY)) {
      localStorage.setItem(LAST_END_KEY, endId);
      endStudy();
    }
    maybeApplyAutoStudy();
  }

  function stopFirebaseListener() {
    if (messageUnsubscribe) messageUnsubscribe();
    if (channelUnsubscribe) channelUnsubscribe();
    messageUnsubscribe = null;
    channelUnsubscribe = null;
  }

  async function firebasePersistentListener(topic) {
    stopFirebaseListener();
    await ready;
    const code = codeFromTopic(topic);
    if (!code) return;
    const channel = db.collection(CHANNELS).doc(code);
    const since = Date.now() - 1500;
    messageUnsubscribe = channel.collection('messages').where('createdAtMs', '>=', since).onSnapshot(snapshot => {
      snapshot.docChanges().forEach(change => {
        if (change.type !== 'added') return;
        const data = change.doc.data() || {};
        if (data.senderRole !== 'parent') return;
        let message = {};
        try { message = JSON.parse(data.payload || '{}'); } catch (_) {}
        if (message.type === 'cmd' && message.action === 'end') endStudy();
      });
    });
    channelUnsubscribe = channel.onSnapshot(snapshot => {
      const data = snapshot.data() || {};
      if (data.studentUid === currentUid) applyControls(data);
    });
    persistentRelaySource = { close: stopFirebaseListener };
  }

  async function firebaseAttemptPairing(code) {
    if (!/^\d{6}$/.test(code)) {
      syncPasskeyError.textContent = 'Enter all 6 digits.';
      syncPasskeyError.classList.add('show');
      return;
    }
    syncPasskeyError.classList.remove('show');
    syncConnectPasskeyBtn.disabled = true;
    syncConnectPasskeyBtn.textContent = 'Connecting…';
    const topic = topicFor(code);
    let unsubscribe = () => {};
    try {
      await ready;
      const channel = db.collection(CHANNELS).doc(code);
      const startedAt = Date.now() - 1000;
      await firebaseRelayPublish(topic, { type: 'hello', state: snapshotState() });

      let settled = false;
      const timeout = setTimeout(() => {
        if (settled) return;
        settled = true;
        unsubscribe();
        syncConnectPasskeyBtn.disabled = false;
        syncConnectPasskeyBtn.textContent = 'Connect';
        syncPasskeyError.textContent = "Dashboard didn't respond. Make sure the parent app is open and showing this passkey.";
        syncPasskeyError.classList.add('show');
      }, 12000);

      unsubscribe = channel.collection('messages').where('createdAtMs', '>=', startedAt).onSnapshot(snapshot => {
        snapshot.docChanges().forEach(change => {
          const data = change.doc.data() || {};
          if (data.senderRole !== 'parent') return;
          let message = {};
          try { message = JSON.parse(data.payload || '{}'); } catch (_) {}
          if (message.type !== 'ack' || settled) return;
          settled = true;
          clearTimeout(timeout);
          unsubscribe();
          pairedTopic = topic;
          localStorage.setItem(RELAY_TOPIC_KEY, pairedTopic);
          syncConnectPasskeyBtn.disabled = false;
          syncConnectPasskeyBtn.textContent = 'Connect';
          renderSyncSection();
          firebasePersistentListener(topic);
          startHeartbeat();
          showToast('Connected to parent dashboard ✓');
        });
      }, error => {
        if (settled) return;
        settled = true;
        clearTimeout(timeout);
        unsubscribe();
        syncConnectPasskeyBtn.disabled = false;
        syncConnectPasskeyBtn.textContent = 'Connect';
        syncPasskeyError.textContent = error?.message || 'Firebase pairing failed.';
        syncPasskeyError.classList.add('show');
      });
    } catch (error) {
      unsubscribe();
      syncConnectPasskeyBtn.disabled = false;
      syncConnectPasskeyBtn.textContent = 'Connect';
      syncPasskeyError.textContent = error?.message || 'Firebase pairing failed.';
      syncPasskeyError.classList.add('show');
    }
  }

  ready.then(() => {
    relayPublish = function firebasePublishReplacement(topic, value) {
      firebaseRelayPublish(topic, value).catch(error => {
        console.warn('StudyLock Firebase parent publish failed', error);
      });
    };
    startPersistentListener = function firebaseListenerReplacement(topic) {
      firebasePersistentListener(topic).catch(error => console.warn('StudyLock Firebase parent listener failed', error));
    };
    attemptPairing = firebaseAttemptPairing;

    if (pairedTopic) {
      try { persistentRelaySource?.close?.(); } catch (_) {}
      firebasePersistentListener(pairedTopic);
      startHeartbeat();
    }
    maybeApplyAutoStudy();
  }).catch(error => {
    console.warn('StudyLock Firebase parent controls unavailable', error);
  });

  setInterval(maybeApplyAutoStudy, 30000);
  window.studyLockFirebaseParent = { ready, maybeApplyAutoStudy };
})();
