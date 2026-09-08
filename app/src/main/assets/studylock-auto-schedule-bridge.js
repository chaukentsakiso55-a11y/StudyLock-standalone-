(() => {
  if (window.__studyLockAutoScheduleBridgeAttached) return;
  window.__studyLockAutoScheduleBridgeAttached = true;

  const KEY = 'studylock_parent_schedule_v3';
  let lastPayload = '';

  function readSchedule() {
    try {
      const value = JSON.parse(localStorage.getItem(KEY) || '{}');
      return {
        enabled: !!value.enabled,
        startMinute: Math.max(0, Math.min(1439, Number(value.startMinute) || 0)),
        endMinute: Math.max(0, Math.min(1439, Number(value.endMinute) || 0)),
        defaultMinutes: Math.max(25, Math.min(300, Number(value.defaultMinutes) || 60))
      };
    } catch (_) {
      return { enabled: false, startMinute: 1080, endMinute: 1200, defaultMinutes: 60 };
    }
  }

  function syncNativeSchedule(force = false) {
    const schedule = readSchedule();
    const payload = JSON.stringify(schedule);
    if (!force && payload === lastPayload) return;
    lastPayload = payload;

    const query = new URLSearchParams({
      enabled: schedule.enabled ? '1' : '0',
      start: String(schedule.startMinute),
      end: String(schedule.endMinute),
      minutes: String(schedule.defaultMinutes)
    });
    const iframe = document.createElement('iframe');
    iframe.style.display = 'none';
    iframe.src = `studylock://auto-study?${query.toString()}`;
    document.body.appendChild(iframe);
    setTimeout(() => iframe.remove(), 1200);
  }

  window.addEventListener('storage', event => {
    if (event.key === KEY) syncNativeSchedule(true);
  });

  document.addEventListener('click', event => {
    const target = event.target instanceof Element ? event.target : null;
    if (target?.closest('#studylockSaveSchedule')) setTimeout(() => syncNativeSchedule(true), 80);
  }, true);

  syncNativeSchedule(true);
  setInterval(syncNativeSchedule, 5000);
  window.StudyLockAutoScheduleBridge = { sync: () => syncNativeSchedule(true) };
})();
