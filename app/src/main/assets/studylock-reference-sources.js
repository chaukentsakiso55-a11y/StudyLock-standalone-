(() => {
  if (window.__studyLockReferenceSourcesAttached) return;
  window.__studyLockReferenceSourcesAttached = true;

  const SOURCES = [
    {
      name: 'Cyber Pulse Info',
      url: 'https://cyber-pulse-info.netlify.app'
    },
    {
      name: 'Cyber Learn Projects',
      url: 'https://cyber-learn-projects.netlify.app'
    }
  ];

  function settingsContainer() {
    const offline = document.getElementById('offlineTutorLibrarySection');
    if (offline?.parentElement) return offline.parentElement;
    const api = document.getElementById('apiKeySection');
    if (api?.parentElement) return api.parentElement;
    const existing = document.querySelector('.settings-section');
    return existing?.parentElement || document.querySelector('[id*="settings" i]') || null;
  }

  function button(label, href, primary = false) {
    const a = document.createElement('a');
    a.href = href;
    a.textContent = label;
    a.style.cssText = [
      'display:flex',
      'align-items:center',
      'justify-content:center',
      'min-height:42px',
      'padding:10px 12px',
      'border-radius:13px',
      'text-decoration:none',
      'font-size:12.5px',
      'font-weight:800',
      'flex:1',
      'min-width:150px',
      primary
        ? 'background:linear-gradient(135deg,#ff9a45,#ff7a1f);color:white;border:1px solid rgba(255,122,31,.22)'
        : 'background:rgba(255,255,255,.78);color:#6b3a18;border:1px solid rgba(255,122,31,.20)'
    ].join(';');
    return a;
  }

  function ensureUi() {
    if (document.getElementById('referenceWebsiteLibrarySection')) return true;
    const parent = settingsContainer();
    if (!parent) return false;

    const section = document.createElement('div');
    section.className = 'settings-section';
    section.id = 'referenceWebsiteLibrarySection';
    section.innerHTML = `
      <div class="settings-label">Reference Library Websites</div>
      <div class="settings-row" style="display:block;">
        <div class="settings-row-label">StudyLock library sources</div>
        <div class="settings-row-sub">Browse the official Cyber Pulse reference websites, download StudyLock-compatible library files, then import and view them inside StudyLock.</div>
      </div>
      <div id="studylockReferenceSourceButtons" style="display:flex;gap:8px;flex-wrap:wrap;margin-top:10px;"></div>
      <div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:10px;" id="studylockReferenceLibraryActions"></div>
      <div class="settings-hint">Supported imports: StudyLock SQLite reference databases (.db/.sqlite/.sqlite3) and ZIP files containing compatible databases. Imported libraries are searched automatically by the offline AI Tutor.</div>
    `;

    const offline = document.getElementById('offlineTutorLibrarySection');
    if (offline?.nextSibling) parent.insertBefore(section, offline.nextSibling);
    else parent.appendChild(section);

    const sourceButtons = section.querySelector('#studylockReferenceSourceButtons');
    SOURCES.forEach(source => {
      sourceButtons?.appendChild(button(`Open ${source.name}`, source.url));
    });

    const actions = section.querySelector('#studylockReferenceLibraryActions');
    actions?.appendChild(button('Import downloaded library', 'studylock://import-library', true));
    actions?.appendChild(button('View installed libraries', 'studylock://libraries'));

    return true;
  }

  function attachWhenReady() {
    if (ensureUi()) return;
    setTimeout(attachWhenReady, 350);
  }

  attachWhenReady();
  window.studyLockReferenceSources = { sources: SOURCES.slice() };
})();
