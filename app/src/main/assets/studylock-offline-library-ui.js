(() => {
  if (window.__studyLockOfflineLibraryUiAttached || !window.StudyLockNative) return;
  window.__studyLockOfflineLibraryUiAttached = true;

  const native = window.StudyLockNative;
  const BUNDLED_AI_SENTINEL = 'AQ.STUDYLOCK_BUNDLED';
  let currentState = null;
  let pollTimer = null;

  try {
    localStorage.setItem('studylock_openrouter_api_key', BUNDLED_AI_SENTINEL);
    localStorage.setItem('studylock_openrouter_model', 'studylock/bundled-gemini');
  } catch (_) {}

  const policyStyle = document.createElement('style');
  policyStyle.textContent = `
    #apiKeySection { display:none !important; }
    #offlineTutorLibrarySection { display:block !important; }
  `;
  document.head.appendChild(policyStyle);

  function formatBytes(bytes) {
    const value = Math.max(0, Number(bytes) || 0);
    if (!value) return '—';
    const kb = value / 1024;
    const mb = kb / 1024;
    if (mb < 1) return `${Math.max(1, Math.round(kb))} KB`;
    return mb >= 1024 ? `${(mb / 1024).toFixed(2)} GB` : `${mb.toFixed(mb >= 100 ? 0 : 1)} MB`;
  }

  function settingsContainer() {
    const apiSection = document.getElementById('apiKeySection');
    if (apiSection?.parentElement) return apiSection.parentElement;
    const existing = document.querySelector('.settings-section');
    if (existing?.parentElement) return existing.parentElement;
    return document.querySelector('[id*="settings" i]') || null;
  }

  function ensureUi() {
    if (document.getElementById('offlineTutorLibrarySection')) return true;
    const parent = settingsContainer();
    if (!parent) return false;

    const section = document.createElement('div');
    section.className = 'settings-section';
    section.id = 'offlineTutorLibrarySection';
    section.innerHTML = `
      <div class="settings-label">Offline Study Libraries</div>

      <div class="settings-row" style="display:block;margin-bottom:10px;">
        <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:12px;">
          <div>
            <div class="settings-row-label">English Dictionary</div>
            <div class="settings-row-sub">Included in StudyLock • works without internet</div>
          </div>
          <div style="font-size:10.5px;font-weight:800;padding:5px 8px;border-radius:999px;background:rgba(255,122,31,.10);color:#ff7a1f;white-space:nowrap;">INCLUDED</div>
        </div>
      </div>

      <div class="settings-row" style="display:block;">
        <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:12px;">
          <div>
            <div class="settings-row-label">Offline Tutor Reference Library</div>
            <div class="settings-row-sub" id="offlineLibraryStatus">Loading starter library…</div>
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

      <div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:10px;">
        <button type="button" class="settings-btn primary" id="offlineLibraryDownloadBtn" style="flex:1;min-width:150px;">Download Full Library</button>
        <button type="button" class="settings-btn ghost" id="offlineLibraryCancelBtn" style="display:none;flex:1;min-width:110px;">Cancel</button>
        <button type="button" class="settings-btn ghost" id="offlineLibraryRefreshBtn" style="flex:1;min-width:110px;">Check update</button>
        <button type="button" class="settings-btn danger" id="offlineLibraryRemoveBtn" style="display:none;flex:1;min-width:110px;">Remove Full Library</button>
      </div>
      <div class="settings-hint" id="offlineLibraryHint">A starter reference library is included. The larger school library can be downloaded when published.</div>
    `;

    parent.insertBefore(section, parent.firstChild || null);

    document.getElementById('offlineLibraryDownloadBtn')?.addEventListener('click', () => {
      if (currentState && !currentState.published) {
        window.StudyLockNativeHooks?.showToast?.('The full Offline Tutor Library is not published yet. The included starter library is ready.');
        return;
      }
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
      if (!confirm('Remove the downloaded full Offline Tutor Library? The built-in starter library will remain available.')) return;
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
    const starter = !!state.starterInstalled;
    const published = !!state.published;
    const total = Number(state.totalBytes) || 0;
    const transferred = Number(state.transferredBytes) || 0;
    const percent = Math.max(0, Math.min(100, Number(state.progress) || 0));
    const busy = status === 'downloading' || status === 'checking';

    if (statusEl) {
      if (status === 'downloading') {
        statusEl.textContent = `Downloading full library ${percent}% • ${formatBytes(transferred)} / ${formatBytes(total)}`;
      } else if (status === 'checking') {
        statusEl.textContent = starter ? 'Starter library installed • checking for full library…' : 'Checking library download…';
      } else if (installed && starter) {
        statusEl.textContent = `Starter library included • ${formatBytes(state.installedBytes)} • offline Tutor ready`;
      } else if (installed) {
        statusEl.textContent = `Full library installed • version ${state.installedVersion || 1} • ${formatBytes(state.installedBytes)}`;
      } else if (!published) {
        statusEl.textContent = 'Starter library is being prepared';
      } else if (state.error) {
        statusEl.textContent = state.error;
      } else if (total > 0) {
        statusEl.textContent = `Full library ready to download • ${formatBytes(total)}`;
      } else {
        statusEl.textContent = 'Offline Tutor reference library';
      }
    }

    if (badge) {
      if (status === 'downloading') badge.textContent = `${percent}%`;
      else if (starter) badge.textContent = published ? 'STARTER + UPDATE' : 'STARTER';
      else if (installed) badge.textContent = state.updateAvailable ? 'UPDATE' : 'INSTALLED';
      else badge.textContent = 'OFFLINE';
    }

    if (progressWrap) progressWrap.style.display = status === 'downloading' ? 'block' : 'none';
    if (progress) progress.style.width = `${percent}%`;
    if (progressText) progressText.textContent = `${percent}% • ${formatBytes(transferred)} of ${formatBytes(total)}`;

    if (downloadBtn) {
      const fullInstalled = installed && !starter;
      const canDownload = published && !busy && (!fullInstalled || !!state.updateAvailable || starter);
      downloadBtn.disabled = !canDownload;
      downloadBtn.style.display = fullInstalled && !state.updateAvailable ? 'none' : 'block';
      downloadBtn.textContent = state.updateAvailable ? 'Download Update' : published ? 'Download Full Library' : 'Full Library Not Published Yet';
    }
    if (cancelBtn) cancelBtn.style.display = status === 'downloading' ? 'block' : 'none';
    if (refreshBtn) refreshBtn.disabled = busy;
    if (removeBtn) removeBtn.style.display = installed && !starter && !busy ? 'block' : 'none';

    if (hint) {
      const free = Number(state.freeBytes) || 0;
      if (starter && !published) {
        hint.textContent = `The built-in starter library works offline now. The larger downloadable school reference pack has not been published yet.${free ? ` Free storage: ${formatBytes(free)}.` : ''}`;
      } else if (starter && published) {
        hint.textContent = `Starter library is active. Download the full reference library for broader offline Tutor coverage.${total ? ` Full download: ${formatBytes(total)}.` : ''}`;
      } else if (installed) {
        hint.textContent = `Offline Tutor is ready with the downloaded reference library.${free ? ` Free storage: ${formatBytes(free)}.` : ''}`;
      } else if (state.error) {
        hint.textContent = `${state.error}${free ? ` • Free storage: ${formatBytes(free)}` : ''}`;
      } else {
        hint.textContent = `Wi-Fi recommended. The full library stays privately inside StudyLock and can be removed later.${total ? ` Download size: ${formatBytes(total)}.` : ''}`;
      }
    }

    schedulePoll(busy ? 1200 : 15000);
  }

  function refreshLocalState() {
    if (!ensureUi()) {
      setTimeout(refreshLocalState, 400);
      return;
    }
    try { render(JSON.parse(native.getOfflineLibraryState())); } catch (_) {}
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
    if (typeof previousOfflineState === 'function') {
      try { previousOfflineState(rawState); } catch (_) {}
    }
    try { render(JSON.parse(rawState)); } catch (_) {}
  };

  const previousNativeState = hooks.onNativeState;
  hooks.onNativeState = function offlineLibraryNativeState(rawState) {
    if (typeof previousNativeState === 'function') {
      try { previousNativeState(rawState); } catch (_) {}
    }
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
