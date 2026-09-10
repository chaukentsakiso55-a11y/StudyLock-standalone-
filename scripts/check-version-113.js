const fs = require('fs');

const gradle = fs.readFileSync('app/build.gradle.kts', 'utf8');
const manifest = fs.readFileSync('app/src/main/AndroidManifest.xml', 'utf8');
const iconPayload = fs.readFileSync('app/icon/studylock_icon_proper.webp.b64', 'utf8').trim();
const sources = fs.readFileSync('app/src/main/assets/studylock-reference-sources.js', 'utf8');
const picker = fs.readFileSync('app/src/main/assets/studylock-app-picker.js', 'utf8');
const importer = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/CustomReferenceLibraryStore.kt', 'utf8');

if (!gradle.includes('versionCode = 20')) {
  throw new Error('StudyLock 1.0.18 versionCode is missing.');
}
if (!gradle.includes('versionName = "1.0.18-widget-wear"')) {
  throw new Error('StudyLock 1.0.18 versionName is missing.');
}
for (const token of ['OFFLINE_LIBRARY_STORAGE_PATH', 'OFFLINE_LIBRARY_VERSION', 'firebase-storage']) {
  if (!gradle.includes(token)) throw new Error(`StudyLock 1.0.18 library config is missing: ${token}`);
}
for (const token of ['studylock-firebase-parent-config.js', '__STUDYLOCK_FIREBASE_PARENT_CONFIG']) {
  if (!gradle.includes(token)) throw new Error(`StudyLock Firebase parent config is missing: ${token}`);
}
for (const token of ['https://cyber-pulse-info.netlify.app', 'https://cyber-learn-projects.netlify.app', 'studylock://import-library', 'studylock://libraries']) {
  if (!sources.includes(token)) throw new Error(`StudyLock reference source wiring is missing: ${token}`);
}
for (const token of ['studylock_blocked_sites', 'studyLockApplyPickedApps', 'packageName', 'window.location.reload()']) {
  if (!picker.includes(token)) throw new Error(`StudyLock installed-app picker fix is missing: ${token}`);
}
for (const token of ['ZipInputStream', 'JSONTokener', 'parseCsv', 'extractGenericDatabase', 'createCanonicalDatabase', 'looksLikeSqlite']) {
  if (!importer.includes(token)) throw new Error(`StudyLock flexible library import is missing: ${token}`);
}
if (!manifest.includes('android:icon="@drawable/studylock_icon_proper"') ||
    !manifest.includes('android:roundIcon="@drawable/studylock_icon_proper"')) {
  throw new Error('StudyLock proper launcher icon is not wired in the manifest.');
}
if (!manifest.includes('.ReferenceLibraryImportActivity') || !manifest.includes('.ReferenceLibraryViewerActivity')) {
  throw new Error('StudyLock custom reference library activities are missing.');
}
if (!manifest.includes('.StudyLockWidgetProvider') || !manifest.includes('.StudyLockWearMessageService')) {
  throw new Error('StudyLock widget/Wear OS native components are missing.');
}
if (!iconPayload.startsWith('UklGR') || iconPayload.length < 10000) {
  throw new Error('StudyLock proper launcher icon payload is missing or invalid.');
}

console.log('StudyLock 1.0.18 widget/Wear and existing app/library checks passed.');
