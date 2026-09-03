(() => {
  if (window.__studyLockEnhancementsAttached || !window.StudyLockNative) return;
  window.__studyLockEnhancementsAttached = true;

  const native = window.StudyLockNative;
  const focusBlobUrls = [];
  let latestProtectionState = null;

  function showToast(message) {
    if (window.StudyLockNativeHooks?.showToast) {
      window.StudyLockNativeHooks.showToast(message);
      return;
    }
    console.info(message);
  }

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  function seededRandom(seed) {
    let value = seed >>> 0;
    return () => {
      value = (value * 1664525 + 1013904223) >>> 0;
      return value / 4294967296;
    };
  }

  function makeSeamless(samples, sampleRate) {
    const crossfade = Math.min(Math.floor(sampleRate * 0.6), Math.floor(samples.length / 5));
    if (crossfade <= 1) return;
    const start = samples.slice(0, crossfade);
    const endStart = samples.length - crossfade;
    for (let i = 0; i < crossfade; i++) {
      const t = i / (crossfade - 1);
      samples[endStart + i] = samples[endStart + i] * (1 - t) + start[i] * t;
    }
  }

  function encodeWav(samples, sampleRate) {
    const buffer = new ArrayBuffer(44 + samples.length * 2);
    const view = new DataView(buffer);
    const writeText = (offset, text) => {
      for (let i = 0; i < text.length; i++) view.setUint8(offset + i, text.charCodeAt(i));
    };
    writeText(0, 'RIFF');
    view.setUint32(4, 36 + samples.length * 2, true);
    writeText(8, 'WAVE');
    writeText(12, 'fmt ');
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true);
    view.setUint16(22, 1, true);
    view.setUint32(24, sampleRate, true);
    view.setUint32(28, sampleRate * 2, true);
    view.setUint16(32, 2, true);
    view.setUint16(34, 16, true);
    writeText(36, 'data');
    view.setUint32(40, samples.length * 2, true);
    let offset = 44;
    for (let i = 0; i < samples.length; i++, offset += 2) {
      const sample = clamp(samples[i], -1, 1);
      view.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true);
    }
    return new Blob([buffer], { type: 'audio/wav' });
  }

  function makeFocusSoundscape(kind, seed) {
    const sampleRate = 12000;
    const duration = 16;
    const length = sampleRate * duration;
    const samples = new Float32Array(length);
    const random = seededRandom(seed);
    let brown = 0;
    let pinkA = 0;
    let pinkB = 0;
    let rainSmooth = 0;

    for (let i = 0; i < length; i++) {
      const t = i / sampleRate;
      const white = random() * 2 - 1;

      if (kind === 'deep') {
        brown = clamp(brown + white * 0.018, -1, 1) * 0.995;
        const pad =
          Math.sin(2 * Math.PI * 110 * t) * 0.026 +
          Math.sin(2 * Math.PI * 165 * t) * 0.018 +
          Math.sin(2 * Math.PI * 220 * t) * 0.012;
        samples[i] = brown * 0.16 + pad;
      } else if (kind === 'reading') {
        pinkA = 0.997 * pinkA + white * 0.029;
        pinkB = 0.985 * pinkB + white * 0.055;
        const bed = (pinkA + pinkB + white * 0.06) * 0.12;
        const pad =
          Math.sin(2 * Math.PI * 98 * t) * 0.016 +
          Math.sin(2 * Math.PI * 196 * t) * 0.012;
        samples[i] = bed + pad;
      } else if (kind === 'calm') {
        const breathe = 0.65 + 0.35 * Math.sin(2 * Math.PI * 0.06 * t);
        const pad =
          Math.sin(2 * Math.PI * 131 * t) * 0.028 +
          Math.sin(2 * Math.PI * 196 * t) * 0.020 +
          Math.sin(2 * Math.PI * 262 * t) * 0.012;
        brown = clamp(brown + white * 0.012, -1, 1) * 0.996;
        samples[i] = pad * breathe + brown * 0.045;
      } else {
        rainSmooth = rainSmooth * 0.72 + white * 0.28;
        const hiss = white - rainSmooth;
        const slow = 0.75 + 0.25 * Math.sin(2 * Math.PI * 0.11 * t);
        let rain = (hiss * 0.10 + rainSmooth * 0.055) * slow;
        if (random() > 0.9993) rain += (random() * 2 - 1) * 0.12;
        samples[i] = rain;
      }
    }

    makeSeamless(samples, sampleRate);
    const url = URL.createObjectURL(encodeWav(samples, sampleRate));
    focusBlobUrls.push(url);
    return url;
  }

  function installFocusSoundscapes() {
    try {
      if (typeof MUSIC_TRACKS === 'undefined' || !Array.isArray(MUSIC_TRACKS)) return;
      const replacements = [
        { name: 'Deep Focus', url: makeFocusSoundscape('deep', 1103) },
        { name: 'Reading Flow', url: makeFocusSoundscape('reading', 2207) },
        { name: 'Calm Concentration', url: makeFocusSoundscape('calm', 3319) },
        { name: 'Soft Rain Focus', url: makeFocusSoundscape('rain', 4421) }
      ];
      MUSIC_TRACKS.splice(0, 4, ...replacements);

      try {
        if (localStorage.getItem('studylock_music_volume') === null) {
          localStorage.setItem('studylock_music_volume', '0.30');
          if (typeof savedMusicVolume !== 'undefined') savedMusicVolume = 0.30;
        }
      } catch (_) {}

      const title = document.getElementById('currentTrackName');
      if (title) title.textContent = replacements[0].name;
      const musicSection = title?.closest('.settings-section');
      const subtitle = musicSection?.querySelector('.settings-row-sub');
      if (subtitle) subtitle.textContent = 'Offline focus soundscapes · no lyrics · no internet';
      const slider = document.getElementById('musicVolumeSlider');
      const volumeValue = document.getElementById('musicVolumeValue');
      if (slider && localStorage.getItem('studylock_music_volume') === '0.30') slider.value = '30';
      if (volumeValue && slider) volumeValue.textContent = `${slider.value}%`;
    } catch (error) {
      console.warn('StudyLock focus soundscapes could not be installed.', error);
    }
  }

  function protectionSection() {
    let section = document.getElementById('uninstallProtectionSection');
    if (section) return section;

    const dataSection = Array.from(document.querySelectorAll('.settings-section')).find(item =>
      item.querySelector('.settings-label')?.textContent?.trim() === 'Data'
    );
    const parent = dataSection?.parentElement;
    if (!parent) return null;

    section = document.createElement('div');
    section.className = 'settings-section';
    section.id = 'uninstallProtectionSection';
    section.innerHTML = `
      <div class="settings-label">Uninstall protection</div>
      <div class="settings-row" style="cursor:default;">
        <div>
          <div class="settings-row-label" id="uninstallProtectionStatus">Off</div>
          <div class="settings-row-sub" id="uninstallProtectionSub">Protect StudyLock from being removed during study periods.</div>
        </div>
      </div>
      <div class="settings-hint" id="uninstallProtectionHint" style="margin-bottom:10px;">Standard mode uses Android Device Admin. Full system-level uninstall blocking is available when StudyLock is provisioned by Android as a managed device owner or profile owner.</div>
      <div class="api-key-btn-row">
        <button class="settings-btn primary" id="enableUninstallProtectionBtn">Enable protection</button>
      </div>
      <div id="disableUninstallProtectionWrap" style="display:none; margin-top:12px; border-top:1px solid var(--glass-border); padding-top:12px;">
        <div class="settings-row-label" style="margin-bottom:8px;">Parent password to release protection</div>
        <input type="password" class="settings-input" id="uninstallProtectionPassword" placeholder="Parent password" autocomplete="current-password" style="margin-bottom:8px;">
        <button class="settings-btn danger" id="disableUninstallProtectionBtn">Release uninstall protection</button>
      </div>`;
    parent.insertBefore(section, dataSection);

    section.querySelector('#enableUninstallProtectionBtn')?.addEventListener('click', () => {
      if (!native.hasParentPassword()) {
        showToast('Set a parent password first, then enable uninstall protection.');
        return;
      }
      const current = latestProtectionState || readProtectionState();
      if (!current?.adminActive) {
        try { sessionStorage.setItem('studylock_pending_uninstall_enable', '1'); } catch (_) {}
        native.requestDeviceAdminAccess();
        return;
      }
      enableProtectionNow();
    });

    section.querySelector('#disableUninstallProtectionBtn')?.addEventListener('click', () => {
      const password = section.querySelector('#uninstallProtectionPassword')?.value || '';
      if (!password) {
        showToast('Enter the parent password first.');
        return;
      }
      try {
        const result = JSON.parse(native.disableUninstallProtection(password));
        if (result.state) renderProtection(result.state);
        if (result.success) section.querySelector('#uninstallProtectionPassword').value = '';
        showToast(result.message || (result.success ? 'Protection released.' : 'Could not release protection.'));
      } catch (error) {
        showToast('StudyLock could not change uninstall protection.');
      }
    });

    return section;
  }

  function readProtectionState() {
    try { return JSON.parse(native.getProtectionState()); }
    catch (_) { return null; }
  }

  function enableProtectionNow() {
    try {
      const result = JSON.parse(native.enableUninstallProtection());
      if (result.state) renderProtection(result.state);
      showToast(result.message || (result.success ? 'Uninstall protection enabled.' : 'Could not enable protection.'));
    } catch (_) {
      showToast('StudyLock could not enable uninstall protection.');
    }
  }

  function renderProtection(state) {
    if (!state) return;
    latestProtectionState = state;
    const section = protectionSection();
    if (!section) return;
    const status = section.querySelector('#uninstallProtectionStatus');
    const sub = section.querySelector('#uninstallProtectionSub');
    const enable = section.querySelector('#enableUninstallProtectionBtn');
    const disableWrap = section.querySelector('#disableUninstallProtectionWrap');

    if (state.uninstallBlocked) {
      status.textContent = 'Full uninstall block enabled';
      sub.textContent = 'Android device policy is blocking removal of StudyLock.';
      enable.textContent = 'Protection active';
      enable.disabled = true;
      disableWrap.style.display = 'block';
    } else if (state.adminActive && state.protectionDesired) {
      status.textContent = 'Device Admin barrier enabled';
      sub.textContent = 'Removing StudyLock requires disabling its administrator protection first.';
      enable.textContent = 'Protection active';
      enable.disabled = true;
      disableWrap.style.display = 'block';
    } else if (state.adminActive) {
      status.textContent = state.managedOwner ? 'Managed protection ready' : 'Device Admin ready';
      sub.textContent = state.managedOwner
        ? 'StudyLock can enable Android’s full uninstall block.'
        : 'Administrator access is approved. Turn on StudyLock protection.';
      enable.textContent = 'Turn on protection';
      enable.disabled = false;
      disableWrap.style.display = 'none';
    } else {
      status.textContent = 'Off';
      sub.textContent = 'Enable Android protection to make StudyLock harder to remove.';
      enable.textContent = 'Enable protection';
      enable.disabled = false;
      disableWrap.style.display = 'none';
    }
  }

  function handleNativeState(rawState) {
    try {
      const nativeState = typeof rawState === 'string' ? JSON.parse(rawState) : rawState;
      renderProtection({
        adminActive: !!nativeState.deviceAdminActive,
        deviceOwner: !!nativeState.deviceOwner,
        profileOwner: !!nativeState.profileOwner,
        managedOwner: !!nativeState.deviceOwner || !!nativeState.profileOwner,
        uninstallBlocked: !!nativeState.uninstallBlocked,
        protectionDesired: !!nativeState.uninstallProtectionDesired,
        level: nativeState.uninstallProtectionLevel || 'off'
      });
      let pending = false;
      try {
        pending = sessionStorage.getItem('studylock_pending_uninstall_enable') === '1';
      } catch (_) {}
      if (pending && nativeState.deviceAdminActive) {
        try { sessionStorage.removeItem('studylock_pending_uninstall_enable'); } catch (_) {}
        setTimeout(enableProtectionNow, 250);
      }
    } catch (_) {}
  }

  installFocusSoundscapes();
  protectionSection();
  renderProtection(readProtectionState());

  if (window.StudyLockNativeHooks) {
    const previousNativeState = window.StudyLockNativeHooks.onNativeState;
    window.StudyLockNativeHooks.onNativeState = function(rawState) {
      if (typeof previousNativeState === 'function') previousNativeState(rawState);
      handleNativeState(rawState);
    };
  }

  try { handleNativeState(native.getNativeState()); } catch (_) {}
  window.addEventListener('beforeunload', () => {
    focusBlobUrls.forEach(url => URL.revokeObjectURL(url));
  }, { once: true });
})();
