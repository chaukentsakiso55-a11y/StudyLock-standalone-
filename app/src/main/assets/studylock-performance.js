(() => {
  if (window.__studyLockPerformanceAttached) return;
  window.__studyLockPerformanceAttached = true;

  try {
    const viewport = document.querySelector('meta[name="viewport"]');
    if (viewport) {
      viewport.setAttribute(
        'content',
        'width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover, interactive-widget=resizes-content'
      );
    }
  } catch (_) {}

  try {
    window.StudyLockNative?.enterImmersiveFullscreen?.();
  } catch (_) {}

  const style = document.createElement('style');
  style.id = 'studylockAndroidPerformanceStyle';
  style.textContent = `
    /* StudyLock Android ultra-performance profile.
       Fill the whole phone and prefer stable frame-times over live blur,
       layered refraction, animated background effects and long transitions. */
    html,
    body {
      width: 100% !important;
      min-width: 100% !important;
      min-height: 100% !important;
      margin: 0 !important;
      padding: 0 !important;
      overscroll-behavior: none !important;
      background: #ffffff !important;
      scroll-behavior: auto !important;
    }

    html {
      height: 100% !important;
    }

    body {
      display: block !important;
      min-height: 100vh !important;
      min-height: 100dvh !important;
      align-items: initial !important;
      justify-content: initial !important;
      overflow-x: hidden !important;
    }

    .app {
      width: 100% !important;
      max-width: none !important;
      min-height: 100vh !important;
      min-height: 100dvh !important;
      margin: 0 !important;
      padding:
        max(8px, env(safe-area-inset-top))
        max(8px, env(safe-area-inset-right))
        max(10px, env(safe-area-inset-bottom))
        max(8px, env(safe-area-inset-left)) !important;
      gap: 10px !important;
    }

    .topbar {
      width: 100% !important;
      margin: 0 !important;
      padding: 4px 2px !important;
    }

    .orb {
      display: none !important;
      animation: none !important;
      filter: none !important;
    }

    body * {
      backdrop-filter: none !important;
      -webkit-backdrop-filter: none !important;
      will-change: auto !important;
      scroll-behavior: auto !important;
    }

    .glass {
      background: rgba(255,255,255,0.94) !important;
      box-shadow:
        0 2px 10px rgba(40,20,5,0.075),
        0 0 0 1px rgba(255,138,31,0.065) inset !important;
      isolation: auto !important;
      contain: paint;
      transition: none !important;
    }

    .glass::before,
    .glass::after,
    .glass .glass-spec {
      display: none !important;
      content: none !important;
      animation: none !important;
    }

    .brand-mark,
    .streak-pill,
    .settings-section,
    .site-chip,
    .quiz-option,
    .modal-sheet,
    .toast {
      box-shadow: none !important;
    }

    .settings-section,
    .site-list,
    .quiz-card,
    .note-card,
    .planner-card {
      content-visibility: auto;
      contain-intrinsic-size: auto 140px;
    }

    .view:not(.active) {
      display: none !important;
    }

    @media (hover:none), (pointer:coarse) {
      .glass,
      .site-chip,
      .settings-btn,
      .nav-item,
      .tab-btn,
      button,
      input,
      textarea,
      select {
        transition: none !important;
      }
      .glass:hover,
      .site-chip:hover,
      button:hover {
        transform: none !important;
      }
    }

    @media (prefers-reduced-motion: no-preference) {
      .orb,
      .glass::before,
      .glass::after {
        animation: none !important;
      }
    }

    html[data-studylock-backgrounded="true"] .view,
    html[data-studylock-backgrounded="true"] .settings-section {
      content-visibility: auto;
    }
  `;
  document.head.appendChild(style);

  let resumeTimer = null;
  document.addEventListener('visibilitychange', () => {
    const backgrounded = document.visibilityState === 'hidden';
    document.documentElement.dataset.studylockBackgrounded = backgrounded ? 'true' : 'false';
    if (!backgrounded) {
      clearTimeout(resumeTimer);
      resumeTimer = setTimeout(() => {
        try { window.StudyLockNative?.enterImmersiveFullscreen?.(); } catch (_) {}
      }, 80);
    }
  }, { passive: true });

  window.addEventListener('resize', () => {
    clearTimeout(resumeTimer);
    resumeTimer = setTimeout(() => {
      document.documentElement.style.setProperty('--studylock-vh', `${window.innerHeight * 0.01}px`);
    }, 100);
  }, { passive: true });

  document.documentElement.style.setProperty('--studylock-vh', `${window.innerHeight * 0.01}px`);
})();
