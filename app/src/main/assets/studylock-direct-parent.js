(() => {
  if (window.__studyLockDirectParentAttached || !window.StudyLockParentDirect) return;
  window.__studyLockDirectParentAttached = true;

  const native = window.StudyLockParentDirect;
  const SEEN_KEY = 'studylock_parent_seen_commands_v1';
  const MODE_KEY = 'studylock_parent_mode';
  const GOAL_KEY = 'studylock_parent_goal';
  const ASSIGNMENT_KEY = 'studylock_parent_assignment';
  const QUIZ_KEY = 'studylock_parent_quiz';
  const ALLOWED_KEY = 'studylock_parent_allowed_apps';
  const AUTO_KEY = 'studylock_parent_auto_v2';
  const BLOCKED_KEY = 'studylock_blocked_sites';
  let pendingCode = '';
  let directConnected = false;
  let fallbackStarted = false;

  function toast(message) {
    window.StudyLockNativeHooks?.showToast?.(message);
  }

  function readPairingCode() {
    const grid = document.getElementById('passkeyGrid');
    if (!grid) return '';
    return Array.from(grid.querySelectorAll('.passkey-digit'))
      .map(input => String(input.value || '').replace(/\D/g, '').slice(0, 1))
      .join('');
  }

  function directState() {
    try { return JSON.parse(native.getState() || '{}'); }
    catch (_) { return {}; }
  }

  function currentState() {
    let base = {};
    try {
      base = window.StudyLockParentControl?.snapshot?.() || {};
    } catch (_) {}

    const timerText = document.getElementById('timerDisplay')?.textContent?.trim() || '00:00';
    const timerParts = timerText.split(':').map(Number);
    const remainingSeconds = timerParts.length === 2 && timerParts.every(Number.isFinite)
      ? timerParts[0] * 60 + timerParts[1]
      : 0;

    const nativeState = (() => {
      try { return JSON.parse(window.StudyLockNative?.getNativeState?.() || '{}'); }
      catch (_) { return {}; }
    })();

    return Object.assign({}, base, {
      focusActive: document.getElementById('hero')?.classList.contains('locked') === true,
      focusPaused: (document.getElementById('statusLabel')?.textContent || '').toLowerCase().includes('paused'),
      remainingSeconds,
      todayMinutes: document.getElementById('statToday')?.textContent || '0m',
      sessionsCompleted: Number(document.getElementById('statSessions')?.textContent || 0),
      streak: Number(document.getElementById('streakCount')?.textContent || 0),
      studyMode: localStorage.getItem(MODE_KEY) || 'normal',
      parentGoal: localStorage.getItem(GOAL_KEY) || '',
      parentAssignment: localStorage.getItem(ASSIGNMENT_KEY) || '',
      directParent: directState(),
      protection: nativeState
    });
  }

  function pushState() {
    if (!directConnected) return false;
    try { return native.sendState(JSON.stringify(currentState())); }
    catch (_) { return false; }
  }

  function seenIds() {
    try { return JSON.parse(localStorage.getItem(SEEN_KEY) || '[]'); }
    catch (_) { return []; }
  }

  function alreadySeen(id) {
    if (!id) return false;
    const ids = seenIds();
    if (ids.includes(id)) return true;
    ids.push(id);
    while (ids.length > 80) ids.shift();
    try { localStorage.setItem(SEEN_KEY, JSON.stringify(ids)); } catch (_) {}
    return false;
  }

  function chooseMinutes(minutes) {
    const wanted = Math.max(25, Math.min(300, Number(minutes || 25)));
    const buttons = Array.from(document.querySelectorAll('.preset-btn'));
    if (!buttons.length) return;
    let selected = buttons.find(button => Number(button.dataset.mins || 0) === wanted);
    if (!selected) {
      selected = buttons.reduce((best, button) => {
        const a = Math.abs(Number(best.dataset.mins || 25) - wanted);
        const b = Math.abs(Number(button.dataset.mins || 25) - wanted);
        return b < a ? button : best;
      }, buttons[0]);
    }
    selected?.click();
  }

  function normalizeEntries(value) {
    const source = Array.isArray(value) ? value : [];
    return source.map(item => String(item || '').trim()).filter(Boolean);
  }

  function updateParentSettingsSummary() {
    const mount = document.getElementById('studylockParentControlSummary');
    if (!mount) return;
    const goal = localStorage.getItem(GOAL_KEY) || 'No parent goal assigned';
    const assignment = localStorage.getItem(ASSIGNMENT_KEY) || 'No assignment waiting';
    let quiz = {};
    try { quiz = JSON.parse(localStorage.getItem(QUIZ_KEY) || '{}'); } catch (_) {}
    const mode = localStorage.getItem(MODE_KEY) || 'normal';
    mount.innerHTML = `
      <div class="settings-row"><div><div class="settings-row-title">Parent mode</div><div class="settings-row-sub">${escapeText(mode)}</div></div></div>
      <div class="settings-row"><div><div class="settings-row-title">Current parent goal</div><div class="settings-row-sub">${escapeText(goal)}</div></div></div>
      <div class="settings-row"><div><div class="settings-row-title">Current assignment</div><div class="settings-row-sub">${escapeText(assignment)}</div></div></div>
      <div class="settings-row"><div><div class="settings-row-title">Assigned quiz</div><div class="settings-row-sub">${escapeText(quiz.topic || 'None')} ${quiz.difficulty ? '· ' + escapeText(quiz.difficulty) : ''}</div></div></div>
    `;
  }

  function escapeText(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  function ensureParentSettingsSection() {
    if (document.getElementById('studylockParentControlSection')) return;
    const settingsList = document.getElementById('settingsSiteList');
    const anchor = settingsList?.closest('.settings-section') || document.querySelector('.settings-section');
    const parent = anchor?.parentElement;
    if (!parent) return;

    const section = document.createElement('div');
    section.className = 'settings-section';
    section.id = 'studylockParentControlSection';
    section.innerHTML = `
      <div class="settings-label">Parent controls</div>
      <div id="studylockParentControlSummary"></div>
      <div class="settings-hint" style="margin-top:8px;">Commands from StudyLock Parent sync here directly on the same Wi-Fi when possible, with Firebase as fallback.</div>
    `;
    parent.insertBefore(section, anchor);
    updateParentSettingsSummary();
  }

  function acknowledgeCommand(requestId, action, ok, message) {
    const state = currentState();
    try {
      native.ackCommand(
        String(requestId || ''),
        String(action || ''),
        !!ok,
        String(message || ''),
        JSON.stringify(state)
      );
    } catch (_) {}
    try {
      window.studyLockFirebaseParent?.sendToParent?.({
        type: 'command_ack',
        requestId: String(requestId || ''),
        action: String(action || ''),
        ok: !!ok,
        message: String(message || ''),
        at: Date.now(),
        state
      });
    } catch (_) {}
  }

  function applyCommand(command) {
    if (!command || command.type !== 'cmd') return;
    const requestId = String(command.requestId || '');
    if (alreadySeen(requestId)) return;
    const action = String(command.action || '');
    const payload = command.payload && typeof command.payload === 'object' ? command.payload : {};
    const control = window.StudyLockParentControl;
    let ok = true;
    let message = 'Applied';

    try {
      switch (action) {
        case 'start_focus': {
          const subject = String(payload.subject || '').trim();
          ok = !!control?.startFocus?.(payload.minutes, subject);
          message = ok ? 'Focus session started' : 'Focus could not start. Check Student Accessibility permission.';
          break;
        }
        case 'pause_focus':
          ok = !!control?.pauseFocus?.();
          message = ok ? 'Focus paused' : 'No active focus session to pause';
          break;
        case 'resume_focus':
          ok = !!control?.resumeFocus?.();
          message = ok ? 'Focus resumed' : 'No paused focus session to resume';
          break;
        case 'end_focus':
          ok = !!control?.endFocus?.();
          message = ok ? 'Focus session ended' : 'Focus session could not be ended';
          break;
        case 'set_schedule':
          localStorage.setItem(AUTO_KEY, JSON.stringify({
            enabled: payload.enabled !== false,
            minutes: Math.max(25, Math.min(300, Number(payload.minutes || 25))),
            startMinuteOfDay: Number(payload.startMinuteOfDay ?? -1)
          }));
          window.studyLockFirebaseParent?.maybeApplyAutoStudy?.();
          message = 'Schedule updated';
          break;
        case 'set_blocked': {
          const entries = normalizeEntries(payload.entries);
          const applied = control?.setBlocked?.(entries);
          ok = Array.isArray(applied) && applied.length === entries.length &&
            applied.every((entry, index) => entry === entries[index]);
          message = ok ? 'Blocked-app list updated' : 'Blocked-app list did not apply correctly';
          break;
        }
        case 'set_allowed':
          localStorage.setItem(ALLOWED_KEY, JSON.stringify(normalizeEntries(payload.entries)));
          message = 'Allowed-app list updated';
          break;
        case 'set_goal':
          localStorage.setItem(GOAL_KEY, String(payload.text || '').trim());
          updateParentSettingsSummary();
          message = 'Study goal updated';
          break;
        case 'set_assignment':
          localStorage.setItem(ASSIGNMENT_KEY, String(payload.text || '').trim());
          updateParentSettingsSummary();
          message = 'Assignment updated';
          break;
        case 'set_quiz':
          localStorage.setItem(QUIZ_KEY, JSON.stringify({
            topic: String(payload.topic || '').trim(),
            difficulty: String(payload.difficulty || 'medium').trim()
          }));
          updateParentSettingsSummary();
          message = 'Quiz assignment updated';
          break;
        case 'set_mode':
          localStorage.setItem(MODE_KEY, String(payload.mode || 'normal'));
          updateParentSettingsSummary();
          message = 'Study mode updated';
          break;
        case 'lock_settings':
          if (window.StudyLockSettingsLock?.lockNow) {
            window.StudyLockSettingsLock.lockNow();
            message = 'Settings locked';
          } else {
            ok = false;
            message = 'Settings lock is unavailable';
          }
          break;
        case 'refresh_state':
        case 'protection_status':
          message = 'Student state refreshed';
          break;
        default:
          ok = false;
          message = 'Unknown Parent command';
      }
    } catch (error) {
      ok = false;
      message = error?.message || 'Student command execution failed';
      console.warn('StudyLock could not apply Parent command', action, error);
    }

    if (ok) toast(message);
    acknowledgeCommand(requestId, action, ok, message);
    setTimeout(() => {
      pushState();
      try { window.StudyLockParentControl?.pushParentState?.(); } catch (_) {}
    }, 150);
  }

  function drainCommands() {
    let commands = [];
    try { commands = JSON.parse(native.drainCommands() || '[]'); } catch (_) {}
    if (!Array.isArray(commands)) return;
    commands.forEach(applyCommand);
  }

  window.StudyLockDirectParentHooks = {
    onPairingResult(success, message) {
      const button = document.getElementById('syncConnectPasskeyBtn');
      if (button) {
        button.disabled = false;
        button.textContent = 'Connect';
      }
      if (success) {
        directConnected = true;
        fallbackStarted = false;
        document.getElementById('syncPasskeyError')?.classList.remove('show');
        try { localStorage.setItem('studylock_direct_parent_code', pendingCode); } catch (_) {}
        toast(message || 'Connected directly to StudyLock Parent ✓');
        pushState();
      } else if (!fallbackStarted && pendingCode) {
        fallbackStarted = true;
        toast('Direct Wi-Fi pairing unavailable — trying cloud fallback…');
        setTimeout(() => {
          try {
            const cloudPair = window.studyLockFirebaseParent?.attemptPairing;
            if (typeof cloudPair === 'function') cloudPair(pendingCode);
            else throw new Error('Cloud pairing is still initializing');
          } catch (error) {
            console.warn('StudyLock cloud fallback pairing failed to start', error);
          }
        }, 250);
      }
    },
    onConnectionChanged(connected, message) {
      directConnected = !!connected;
      if (message) toast(message);
      if (connected) pushState();
    },
    onCommandAvailable() {
      drainCommands();
    }
  };

  document.getElementById('syncConnectPasskeyBtn')?.addEventListener('click', event => {
    const code = readPairingCode();
    if (!/^\d{6}$/.test(code)) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    pendingCode = code;
    fallbackStarted = false;
    const button = event.currentTarget;
    button.disabled = true;
    button.textContent = 'Connecting…';
    native.pair(code);
  }, true);

  ensureParentSettingsSection();
  drainCommands();

  try {
    const state = directState();
    directConnected = !!state.connected;
    if (directConnected) pushState();
  } catch (_) {}

  setInterval(() => {
    drainCommands();
    pushState();
  }, 5000);

  window.StudyLockDirectParent = {
    applyCommand,
    drainNow: drainCommands,
    pushState,
    isConnected: () => directConnected
  };
})();
