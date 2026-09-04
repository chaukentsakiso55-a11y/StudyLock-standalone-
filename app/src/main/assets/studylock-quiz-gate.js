(() => {
  if (window.__studyLockQuizGateAttached) return;
  window.__studyLockQuizGateAttached = true;

  const GATE_KEY = 'studylock_exit_quiz_required_v1';
  let gateActive = false;
  let gateBanner = null;

  const originalCompleteSession = typeof completeSession === 'function' ? completeSession : null;
  const originalShowQuizResults = typeof showQuizResults === 'function' ? showQuizResults : null;

  function showToast(message) {
    if (window.StudyLockNativeHooks?.showToast) {
      window.StudyLockNativeHooks.showToast(message);
    }
  }

  function setGatePersisted(active) {
    try {
      if (active) localStorage.setItem(GATE_KEY, '1');
      else localStorage.removeItem(GATE_KEY);
    } catch (_) {}
  }

  function isGatePersisted() {
    try { return localStorage.getItem(GATE_KEY) === '1'; }
    catch (_) { return false; }
  }

  function ensureSettingsSection() {
    if (document.getElementById('mandatoryQuizSettingSection')) return;
    const dataSection = Array.from(document.querySelectorAll('.settings-section')).find(section =>
      section.querySelector('.settings-label')?.textContent?.trim() === 'Data'
    );
    const parent = dataSection?.parentElement;
    if (!parent) return;

    const section = document.createElement('div');
    section.className = 'settings-section';
    section.id = 'mandatoryQuizSettingSection';
    section.innerHTML = `
      <div class="settings-label">Session completion</div>
      <div class="settings-row" style="cursor:default;">
        <div>
          <div class="settings-row-label">Quiz required before unlock</div>
          <div class="settings-row-sub">Always enabled for protected focus sessions.</div>
        </div>
        <div style="font-size:12px;font-weight:800;color:var(--amber);padding:7px 10px;border-radius:999px;background:var(--amber-soft);">REQUIRED</div>
      </div>
      <div class="settings-hint">When the timer reaches 00:00, blocked apps stay blocked until the student completes all questions in one quiz. The score does not have to be perfect. A parent-password emergency override remains available.</div>`;
    parent.insertBefore(section, dataSection);
  }

  function ensureQuizGateBanner() {
    if (gateBanner?.isConnected) return gateBanner;
    const quizView = document.getElementById('viewQuiz');
    if (!quizView) return null;

    gateBanner = document.createElement('div');
    gateBanner.id = 'mandatoryQuizGateBanner';
    gateBanner.style.display = 'none';
    gateBanner.style.marginBottom = '12px';
    gateBanner.style.padding = '13px 14px';
    gateBanner.style.border = '1px solid var(--glass-border-bright)';
    gateBanner.style.borderRadius = '18px';
    gateBanner.style.background = 'var(--amber-soft)';
    gateBanner.innerHTML = `
      <div style="font-weight:800;margin-bottom:4px;">🔒 Final quiz required</div>
      <div style="font-size:12.5px;color:var(--text-1);line-height:1.45;">Your study timer is complete. Finish all questions in one quiz to unlock the session.</div>
      <button type="button" class="settings-btn ghost" id="offlineExitQuizBtn" style="width:100%;margin-top:10px;">Use offline mixed quiz</button>`;

    quizView.insertBefore(gateBanner, quizView.firstChild);
    gateBanner.querySelector('#offlineExitQuizBtn')?.addEventListener('click', startOfflineExitQuiz);
    return gateBanner;
  }

  function currentBlockedEntries() {
    try {
      if (typeof blockedSites !== 'undefined' && Array.isArray(blockedSites)) {
        return blockedSites.map(site => site?.name?.trim()).filter(Boolean);
      }
    } catch (_) {}
    return Array.from(document.querySelectorAll('#siteList .site-name'))
      .map(element => element.textContent?.trim())
      .filter(Boolean);
  }

  function flushNativeLockedState() {
    try {
      window.StudyLockNative?.onFocusState(
        true,
        false,
        0,
        JSON.stringify(currentBlockedEntries())
      );
    } catch (_) {}
  }

  function refreshGateVisuals() {
    if (!gateActive) return;
    try {
      if (typeof state !== 'undefined') {
        state.isLocked = true;
        state.isPaused = false;
        state.remainingSeconds = 0;
      }
      if (typeof updateLockVisuals === 'function') updateLockVisuals();
      if (typeof render === 'function') render();
    } catch (_) {}

    const status = document.getElementById('statusLabel');
    const timerSub = document.getElementById('timerSub');
    if (status) status.textContent = 'Quiz required to unlock';
    if (timerSub) timerSub.innerHTML = '<b>Study time complete</b> · finish the quiz to end this session';

    const banner = ensureQuizGateBanner();
    if (banner) banner.style.display = 'block';
  }

  function moveToQuiz() {
    try {
      if (typeof switchTab === 'function') switchTab('quiz');
      if (typeof showQuizScreen === 'function' && !isQuizInProgress?.()) {
        showQuizScreen('setup');
      }
    } catch (_) {}
    setTimeout(() => document.getElementById('quizTopicInput')?.focus(), 100);
  }

  function enterQuizGate() {
    if (gateActive) {
      refreshGateVisuals();
      moveToQuiz();
      return;
    }

    gateActive = true;
    setGatePersisted(true);
    try {
      if (typeof tickHandle !== 'undefined' && tickHandle) clearInterval(tickHandle);
      if (typeof tickHandle !== 'undefined') tickHandle = null;
      if (typeof state !== 'undefined') {
        state.isLocked = true;
        state.isPaused = false;
        state.remainingSeconds = 0;
      }
    } catch (_) {}

    refreshGateVisuals();
    flushNativeLockedState();
    moveToQuiz();
    showToast('Timer complete. Finish a quiz to unlock StudyLock.');
  }

  function finishAfterQuiz() {
    if (!gateActive) return;
    gateActive = false;
    setGatePersisted(false);
    if (gateBanner) gateBanner.style.display = 'none';

    if (originalCompleteSession) {
      originalCompleteSession();
    } else {
      try {
        state.isLocked = false;
        state.isPaused = false;
        updateLockVisuals?.();
        render?.();
      } catch (_) {}
    }
    showToast('Quiz complete — focus session unlocked. 🎉');
  }

  function startOfflineExitQuiz() {
    if (!gateActive) return;
    try {
      quizQuestions = [
        {
          question: 'What is 15 × 4?',
          options: ['45', '50', '60', '75'],
          correctIndex: 2,
          explain: '15 multiplied by 4 equals 60.'
        },
        {
          question: 'Which decimal is equal to 3/4?',
          options: ['0.25', '0.50', '0.75', '1.25'],
          correctIndex: 2,
          explain: 'Three quarters equals 0.75.'
        },
        {
          question: 'In plant cells, where does photosynthesis mainly occur?',
          options: ['Nucleus', 'Chloroplasts', 'Cell wall', 'Ribosomes'],
          correctIndex: 1,
          explain: 'Chloroplasts contain chlorophyll and are the main site of photosynthesis.'
        },
        {
          question: 'At standard atmospheric pressure, at what temperature does pure water freeze?',
          options: ['0 °C', '10 °C', '50 °C', '100 °C'],
          correctIndex: 0,
          explain: 'Pure water freezes at 0 °C under standard atmospheric pressure.'
        },
        {
          question: 'In the sentence “Birds fly south,” which word is the verb?',
          options: ['Birds', 'fly', 'south', 'the'],
          correctIndex: 1,
          explain: '“Fly” expresses the action performed by the birds.'
        }
      ];
      quizCurrentIndex = 0;
      quizScore = 0;
      renderQuizQuestion();
      showQuizScreen('active');
      showToast('Offline exit quiz started. Complete all 5 questions to unlock.');
    } catch (error) {
      console.error('Offline exit quiz failed to start.', error);
      showToast('Could not start the offline quiz. Try again.');
    }
  }

  if (originalCompleteSession) {
    completeSession = function mandatoryQuizCompleteSession() {
      enterQuizGate();
    };
  }

  if (originalShowQuizResults) {
    showQuizResults = function mandatoryQuizShowResults() {
      originalShowQuizResults();
      if (!gateActive) return;

      const scoreSub = document.getElementById('quizScoreSub');
      if (scoreSub) scoreSub.textContent = 'Quiz completed. StudyLock is unlocking this focus session…';
      setTimeout(finishAfterQuiz, 900);
    };
  }

  ensureSettingsSection();
  ensureQuizGateBanner();

  function restoreGateIfNeeded() {
    if (!isGatePersisted()) return;
    try {
      const nativeState = JSON.parse(window.StudyLockNative?.getNativeState?.() || '{}');
      if (!nativeState.focusActive) {
        setGatePersisted(false);
        return;
      }
    } catch (_) {}
    gateActive = true;
    refreshGateVisuals();
    moveToQuiz();
  }

  restoreGateIfNeeded();
  setTimeout(restoreGateIfNeeded, 350);
  setTimeout(restoreGateIfNeeded, 1200);

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && gateActive) {
      refreshGateVisuals();
      flushNativeLockedState();
    }
  });

  window.StudyLockQuizGate = {
    isActive: () => gateActive,
    enter: enterQuizGate,
    startOfflineQuiz: startOfflineExitQuiz
  };
})();
