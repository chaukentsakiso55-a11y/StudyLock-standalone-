(() => {
  if (window.__studyLockPerformanceAttached) return;
  window.__studyLockPerformanceAttached = true;

  const style = document.createElement('style');
  style.id = 'studylockAndroidPerformanceStyle';
  style.textContent = `
    /* Android performance profile: keep the liquid-glass look while reducing
       the most expensive continuous GPU effects on budget devices. */
    .orb {
      animation: none !important;
      filter: blur(42px) !important;
      opacity: 0.42 !important;
    }
    .glass {
      backdrop-filter: blur(14px) saturate(135%) !important;
      -webkit-backdrop-filter: blur(14px) saturate(135%) !important;
      box-shadow:
        0 14px 38px rgba(0,0,0,0.20),
        0 1px 0 var(--inset-top) inset,
        0 0 0 1px rgba(255,138,31,0.08) inset !important;
    }
    .glass::before {
      animation: none !important;
      opacity: 0.58 !important;
    }
    .glass::after {
      opacity: 0.70 !important;
    }
    .glass .glass-spec {
      display: none !important;
    }
    @media (hover:none), (pointer:coarse) {
      .glass {
        transition: none !important;
      }
      .glass:hover {
        transform: none !important;
      }
    }
  `;
  document.head.appendChild(style);

  // Avoid keeping expensive animation work alive when the app is backgrounded.
  document.addEventListener('visibilitychange', () => {
    document.documentElement.dataset.studylockBackgrounded =
      document.visibilityState === 'hidden' ? 'true' : 'false';
  }, { passive: true });
})();
