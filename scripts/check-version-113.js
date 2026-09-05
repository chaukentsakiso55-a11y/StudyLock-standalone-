const fs = require('fs');

const gradle = fs.readFileSync('app/build.gradle.kts', 'utf8');
const manifest = fs.readFileSync('app/src/main/AndroidManifest.xml', 'utf8');
const iconPayload = fs.readFileSync('app/icon/studylock_icon_proper.webp.b64', 'utf8').trim();
const sources = fs.readFileSync('app/src/main/assets/studylock-reference-sources.js', 'utf8');

if (!gradle.includes('versionCode = 18')) {
  throw new Error('StudyLock 1.0.16 versionCode is missing.');
}
if (!gradle.includes('versionName = "1.0.16-reference-websites"')) {
  throw new Error('StudyLock 1.0.16 versionName is missing.');
}
for (const token of ['OFFLINE_LIBRARY_STORAGE_PATH', 'OFFLINE_LIBRARY_VERSION', 'firebase-storage']) {
  if (!gradle.includes(token)) throw new Error(`StudyLock 1.0.16 library config is missing: ${token}`);
}
for (const token of ['studylock-firebase-parent-config.js', '__STUDYLOCK_FIREBASE_PARENT_CONFIG']) {
  if (!gradle.includes(token)) throw new Error(`StudyLock Firebase parent config is missing: ${token}`);
}
for (const token of ['https://cyber-pulse-info.netlify.app', 'https://cyber-learn-projects.netlify.app', 'studylock://import-library', 'studylock://libraries']) {
  if (!sources.includes(token)) throw new Error(`StudyLock reference source wiring is missing: ${token}`);
}
if (!manifest.includes('android:icon="@drawable/studylock_icon_proper"') ||
    !manifest.includes('android:roundIcon="@drawable/studylock_icon_proper"')) {
  throw new Error('StudyLock proper launcher icon is not wired in the manifest.');
}
if (!manifest.includes('.ReferenceLibraryImportActivity') || !manifest.includes('.ReferenceLibraryViewerActivity')) {
  throw new Error('StudyLock custom reference library activities are missing.');
}
if (!iconPayload.startsWith('UklGR') || iconPayload.length < 10000) {
  throw new Error('StudyLock proper launcher icon payload is missing or invalid.');
}

console.log('StudyLock 1.0.16 reference website checks passed.');
