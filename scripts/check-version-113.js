const fs = require('fs');

const gradle = fs.readFileSync('app/build.gradle.kts', 'utf8');
const manifest = fs.readFileSync('app/src/main/AndroidManifest.xml', 'utf8');
const iconPayload = fs.readFileSync('app/icon/studylock_icon_proper.webp.b64', 'utf8').trim();

if (!gradle.includes('versionCode = 17')) {
  throw new Error('StudyLock 1.0.15 versionCode is missing.');
}
if (!gradle.includes('versionName = "1.0.15-firebase-parent-controls"')) {
  throw new Error('StudyLock 1.0.15 versionName is missing.');
}
for (const token of ['OFFLINE_LIBRARY_STORAGE_PATH', 'OFFLINE_LIBRARY_VERSION', 'firebase-storage']) {
  if (!gradle.includes(token)) throw new Error(`StudyLock 1.0.15 library config is missing: ${token}`);
}
for (const token of ['studylock-firebase-parent-config.js', '__STUDYLOCK_FIREBASE_PARENT_CONFIG']) {
  if (!gradle.includes(token)) throw new Error(`StudyLock Firebase parent config is missing: ${token}`);
}
if (!manifest.includes('android:icon="@drawable/studylock_icon_proper"') ||
    !manifest.includes('android:roundIcon="@drawable/studylock_icon_proper"')) {
  throw new Error('StudyLock proper launcher icon is not wired in the manifest.');
}
if (!iconPayload.startsWith('UklGR') || iconPayload.length < 10000) {
  throw new Error('StudyLock proper launcher icon payload is missing or invalid.');
}

console.log('StudyLock 1.0.15 Firebase-parent version checks passed.');
