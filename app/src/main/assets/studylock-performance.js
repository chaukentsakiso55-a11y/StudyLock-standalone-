(() => {
  if (window.__studyLockPerformanceAttached) return;
  window.__studyLockPerformanceAttached = true;

  const style = document.createElement('style');
  style.id = 'studylockAndroidPerformanceStyle';
  style.textContent = `
    /* Android performance profile: prefer stable frame-times over expensive
       live blur/refraction. The visual language stays white/orange and glassy,
       but the renderer has much less continuous GPU work. */
    html, body {
      overscroll-behavior: none !important;
    }

    .orb {
      animation: none !important;
      filter: none !important;
      opacity: 0.18 !important;
      transform: none !important;
    }
    .orb-3, .orb-4 {
      display: none !important;
    }

    .glass {
      background: rgba(255,255,255,0.80) !important;
      backdrop-filter: none !important;
      -webkit-backdrop-filter: none !important;
      box-shadow:
        0 8px 24px rgba(40,20,5,0.10),
        0 1px 0 rgba(255,255,255,0.85) inset,
        0 0 0 1px rgba(255,138,31,0.07) inset !important;
      will-change: auto !important;
    }
    .glass::before {
      display: none !important;
      animation: none !important;
    }
    .glass::after {
      opacity: 0.42 !important;
      box-shadow: 0 0 0 1px rgba(255,138,31,0.12) inset !important;
    }
    .glass .glass-spec {
      display: none !important;
    }

    .settings-section {
      content-visibility: auto;
      contain-intrinsic-size: 120px;
    }

    @media (hover:none), (pointer:coarse) {
      .glass,
      .site-chip,
      .settings-btn,
      .nav-item,
      .tab-btn,
      button,
      input,
      textarea {
        transition-duration: 0.08s !important;
      }
      .glass:hover {
        transform: none !important;
      }
    }

    html[data-studylock-backgrounded="true"] .orb,
    html[data-studylock-backgrounded="true"] .glass::after {
      display: none !important;
    }
  `;
  document.head.appendChild(style);

  document.addEventListener('visibilitychange', () => {
    document.documentElement.dataset.studylockBackgrounded =
      document.visibilityState === 'hidden' ? 'true' : 'false';
  }, { passive: true });
})();
