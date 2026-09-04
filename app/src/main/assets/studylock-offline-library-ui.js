(() => {
  if (window.__studyLockOfflineLibraryUiAttached || !window.StudyLockNative) return;
  window.__studyLockOfflineLibraryUiAttached = true;

  const native = window.StudyLockNative;
  let currentState = null;
  let pollTimer = null;

  function formatBytes(bytes) {
    const value = Math.max(0, Number(bytes) || 0);
    if (!value) return '—';
    const mb = value / (1024 * 1024);
    return mb >= 1024 ? `${(mb / 1024).toFixed(2)} GB` : `${mb.toFixed(mb >= 100 ? 0 : 1)} MB`;
  }

  function ensureUi() {
    if (document.getElementById('offlineTutorLibrarySection')) return true;
    const anchor = document.getElementById('apiKeySection');
    const parent = anchor?.parentElement;
    if (!anchor || !parent) return false;

    const section = document.createElement('div');
    section.className = 'settings-section';
    section.id = 'offlineTutorLibrarySection';
    section.innerHTML = `
      <div class="settings-label">Offline Tutor Library</div>
      <div class="settings-row" style="display:block;">
        <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:12px;">
          <div>
            <div class="settings-row-label">Reference library</div>
            <div class="settings-row-sub" id="offlineLibraryStatus">Checking library…</div>
          </div>
          <div id="offlineLibraryBadge" style="font-size:10.5px;font-weight:800;padding:5px 8px;border-radius:999px;background:rgba(255,122,31,.10);color:#ff7a1f;white-space:nowrap;">OFFLINE</div>
        </div>
        <div id="offlineLibraryProgressWrap" style="display:none;margin-top:12px;">
          <div style="height:7px;border-radius:999px;background:rgba(255,122,31,.10);overflow:hidden;">
            <div id="offlineLibraryProgress" style="height:100%;width:0%;background:linear-gradient(90deg,#ff9a45,#ff7a1f);transition:width .2s ease;"></div>
          </div>
          <div class="settings-row-sub" id="offlineLibraryProgressText" style="margin-top:6px;">0%</div>
        </div>
      </div>
      <div style="display:flex;gap:8px;flex-wrap:wrap;">
        <button type="button" class="settings-btn primary" id="offlineLibraryDownloadBtn" style="flex:1;min-width:150px;">Download Library</button>
        <button type="button" class="settings-btn ghost" id="offlineLibraryCancelBtn" style="display:none;flex:1;min-width:110px;">Cancel</button>
        <button type="button" class="settings-btn ghost" id="offlineLibraryRefreshBtn" style="flex:1;min-width:110px;">Check update</button>
        <button type="button" class="settings-btn danger" id="offlineLibraryRemoveBtn" style="display:none;flex:1;min-width:110px;">Remove</button>
      </div>
      <div class="settings-hint" id="offlineLibraryHint">Wi-Fi recommended. The library is stored privately inside StudyLock and can be removed later to free space.</div>
    `;
    parent.insertBefore(section, anchor);

    document.getElementById('offlineLibraryDownloadBtn')?.addEventListener('click', () => {
      try {
        native.startOfflineLibraryDownload();
        setTimeout(refreshLocalState, 350);
      } catch (_) {}
    });

    document.getElementById('offlineLibraryCancelBtn')?.addEventListener('click', () => {
      try {
        native.cancelOfflineLibraryDownload();
        setTimeout(refreshLocalState, 250);
      } catch (_) {}
    });

    document.getElementById('offlineLibraryRefreshBtn')?.addEventListener('click', () => {
      try { native.refreshOfflineLibraryMetadata(); } catch (_) {}
      setTimeout(refreshLocalState, 350);
    });

    document.getElementById('offlineLibraryRemoveBtn')?.addEventListener('click', () => {
      if (!confirm('Remove the Offline Tutor Library from this phone? You can download it again later.')) return;
      try {
        const removed = native.removeOfflineLibrary();
        if (!removed) window.StudyLockNativeHooks?.showToast?.('The library could not be removed while a download is active.');
      } catch (_) {}
      setTimeout(refreshLocalState, 250);
    });

    return true;
  }

  function render(state) {
    if (!state || !ensureUi()) return;
    currentState = state;

    const statusEl = document.getElementById('offlineLibraryStatus');
    const badge = document.getElementById('offlineLibraryBadge');
    const hint = document.getElementById('offlineLibraryHint');
    const progressWrap = document.getElementById('offlineLibraryProgressWrap');
    const progress = document.getElementById('offlineLibraryProgress');
    const progressText = document.getElementById('offlineLibraryProgressText');
    const downloadBtn = document.getElementById('offlineLibraryDownloadBtn');
    const cancelBtn = document.getElementById('offlineLibraryCancelBtn');
    const refreshBtn = document.getElementById('offlineLibraryRefreshBtn');
    const removeBtn = document.getElementById('offlineLibraryRemoveBtn');

    const status = state.status || 'ready';
    const installed = !!state.installed;
    const total = Number(state.totalBytes) || 0;
    const transferred = Number(state.transferredBytes) || 0;
    const percent = Math.max(0, Math.min(100, Number(state.progress) || 0));
    const busy = status === 'downloading' || status === 'checking';

    if (statusEl) {
      if (status === 'downloading') {
        statusEl.textContent = `Downloading ${percent}% • ${formatBytes(transferred)} / ${formatBytes(total)}`;
      } else if (status === 'checking') {
        statusEl.textContent = 'Checking the StudyLock library download…';
      } else if (installed) {
        statusEl.textContent = `Installed • version ${state.installedVersion || 1} • ${formatBytes(state.installedBytes)}`;
      } else if (status === 'unavailable') {
        statusEl.textContent = 'Library package is not published yet';
      } else if (status === 'error') {
        statusEl.textContent = state.error || 'Could not check the library download';
      } else if (state.published && total > 0) {
        statusEl.textContent = `Ready to download • ${formatBytes(total)}`;
      } else {
        statusEl.textContent = 'Download the reference library for offline Tutor answers';
      }
    }

    if (badge) {
      if (installed) {
        badge.textContent = state.updateAvailable ? 'UPDATE' : 'INSTALLED';
      } else if (status === 'downloading') {
        badge.textContent = `${percent}%`;
      } else {
        badge.textContent = 'OFFLINE';
      }
    }

    if (progressWrap) progressWrap.style.display = status === 'downloading' ? 'block' : 'none';
    if (progress) progress.style.width = `${percent}%`;
    if (progressText) progressText.textContent = `${percent}% • ${formatBytes(transferred)} of ${formatBytes(total)}`;

    if (downloadBtn) {
      downloadBtn.disabled = busy || status === 'unavailable' || (installed && !state.updateAvailable);
      downloadBtn.style.display = installed && !state.updateAvailable ? 'none' : 'block';
      downloadBtn.textContent = state.updateAvailable ? 'Download Update' : status === 'error' ? 'Retry Download' : 'Download Library';
    }
    if (cancelBtn) cancelBtn.style.display = status === 'downloading' ? 'block' : 'none';
    if (refreshBtn) refreshBtn.disabled = busy;
    if (removeBtn) removeBtn.style.display = installed && !busy ? 'block' : 'none';

    if (hint) {
      const free = Number(state.freeBytes) || 0;
      if (state.error) {
        hint.textContent = `${state.error}${free ? ` • Free storage: ${formatBytes(free)}` : ''}`;
      } else if (installed) {
        hint.textContent = `Offline Tutor is ready. StudyLock will use this library when there is no internet.${free ? ` Free storage: ${formatBytes(free)}.` : ''}`;
      } else {
        hint.textContent = `Wi-Fi recommended. The library stays inside StudyLock and can be removed later.${total ? ` Download size: ${formatBytes(total)}.` : ''}${free ? ` Free storage: ${formatBytes(free)}.` : ''}`;
      }
    }

    schedulePoll(status === 'downloading' || status === 'checking' ? 1200 : 15000);
  }

  function refreshLocalState() {
    if (!ensureUi()) {
      setTimeout(refreshLocalState, 400);
      return;
    }
    try {
      render(JSON.parse(native.getOfflineLibraryState()));
    } catch (_) {}
  }

  function schedulePoll(delay) {
    clearTimeout(pollTimer);
    pollTimer = setTimeout(() => {
      if (document.visibilityState === 'visible') refreshLocalState();
      else schedulePoll(5000);
    }, delay);
  }

  const hooks = window.StudyLockNativeHooks || (window.StudyLockNativeHooks = {});
  const previousOfflineState = hooks.onOfflineLibraryState;
  hooks.onOfflineLibraryState = function onOfflineLibraryState(rawState) {
    if (typeof previousOfflineState === 'function') previousOfflineState(rawState);
    try { render(JSON.parse(rawState)); } catch (_) {}
  };

  const previousNativeState = hooks.onNativeState;
  hooks.onNativeState = function offlineLibraryNativeState(rawState) {
    if (typeof previousNativeState === 'function') previousNativeState(rawState);
    try {
      const state = JSON.parse(rawState);
      if (state.offlineTutorLibrary) render(state.offlineTutorLibrary);
    } catch (_) {}
  };

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') refreshLocalState();
  });

  refreshLocalState();
  setTimeout(() => {
    try { native.refreshOfflineLibraryMetadata(); } catch (_) {}
  }, 700);
})();
