const fs = require('fs');

const gradle = fs.readFileSync('app/build.gradle.kts', 'utf8');
const manifest = fs.readFileSync('app/src/main/AndroidManifest.xml', 'utf8');
const iconPayload = fs.readFileSync('app/icon/studylock_icon_proper.webp.b64', 'utf8').trim();
const sources = fs.readFileSync('app/src/main/assets/studylock-reference-sources.js', 'utf8');
const picker = fs.readFileSync('app/src/main/assets/studylock-app-picker.js', 'utf8');
const importer = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/CustomReferenceLibraryStore.kt', 'utf8');
const curriculum = fs.readFileSync('app/src/main/assets/studylock-term3-system.js', 'utf8');
const hardening = fs.readFileSync('app/src/main/assets/studylock-session-hardening.js', 'utf8');
const schedule = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/AutoStudyScheduler.kt', 'utf8');
const control = fs.readFileSync('app/src/main/assets/studylock-admin-control-listener.js', 'utf8');

if (!gradle.includes('versionCode = 20')) {
  throw new Error('StudyLock 1.1.0 versionCode is missing.');
}
if (!gradle.includes('versionName = "1.1.0-term3-auto-study-control"')) {
  throw new Error('StudyLock 1.1.0 versionName is missing.');
}
for (const token of ['OFFLINE_LIBRARY_STORAGE_PATH', 'OFFLINE_LIBRARY_VERSION', 'firebase-storage']) {
  if (!gradle.includes(token)) throw new Error(`StudyLock library config is missing: ${token}`);
}
for (const token of ['studylock-firebase-parent-config.js', '__STUDYLOCK_FIREBASE_PARENT_CONFIG']) {
  if (!gradle.includes(token)) throw new Error(`StudyLock Firebase parent config is missing: ${token}`);
}
for (const token of [
  'https://cyber-pulse-info.netlify.app',
  'https://cyber-learn-projects.netlify.app',
  'studylock://import-library',
  'studylock://libraries',
  'studylock-term3-system.js',
  'studylock-session-hardening.js',
  'studylock-admin-control-listener.js'
]) {
  if (!sources.includes(token)) throw new Error(`StudyLock reference/curriculum wiring is missing: ${token}`);
}
for (const token of ['studylock_blocked_sites', 'studyLockApplyPickedApps', 'packageName', 'window.location.reload()']) {
  if (!picker.includes(token)) throw new Error(`StudyLock installed-app picker fix is missing: ${token}`);
}
for (const token of ['ZipInputStream', 'JSONTokener', 'parseCsv', 'extractGenericDatabase', 'createCanonicalDatabase', 'looksLikeSqlite']) {
  if (!importer.includes(token)) throw new Error(`StudyLock flexible library import is missing: ${token}`);
}
for (const token of [
  'Student profile & curriculum',
  'Parent Auto Study schedule',
  'signupGrade',
  'TODAY',
  'FORMULAS',
  'startOfflineCurriculumQuiz',
  'offlineCurriculumAnswer',
  'aiUsageCount',
  'quizCount'
]) {
  if (!curriculum.includes(token)) throw new Error(`StudyLock Term 3 curriculum feature is missing: ${token}`);
}
for (const token of ['StudyLock sessions cannot be paused', 'parent-set end time', 'forceRunningState']) {
  if (!hardening.includes(token)) throw new Error(`StudyLock no-pause schedule hardening is missing: ${token}`);
}
for (const token of ['AlarmManager', 'ACTION_START', 'ACTION_END', 'activateIfInsideWindow', 'setAndAllowWhileIdle']) {
  if (!schedule.includes(token)) throw new Error(`StudyLock native Auto Study scheduler is missing: ${token}`);
}
for (const token of ['studylock_control_channels', 'getRandomValues', 'controlChannelToken', 'applyRemoteControl']) {
  if (!control.includes(token)) throw new Error(`StudyLock Control listener is missing: ${token}`);
}
if (!manifest.includes('android:icon="@drawable/studylock_icon_proper"') ||
    !manifest.includes('android:roundIcon="@drawable/studylock_icon_proper"')) {
  throw new Error('StudyLock proper launcher icon is not wired in the manifest.');
}
for (const token of ['.ReferenceLibraryImportActivity', '.ReferenceLibraryViewerActivity', '.AutoStudyConfigActivity', '.AutoStudyReceiver', 'android.permission.RECEIVE_BOOT_COMPLETED']) {
  if (!manifest.includes(token)) throw new Error(`StudyLock manifest feature is missing: ${token}`);
}
if (!iconPayload.startsWith('UklGR') || iconPayload.length < 10000) {
  throw new Error('StudyLock proper launcher icon payload is missing or invalid.');
}

console.log('StudyLock 1.1.0 Term 3 Auto Study and Control checks passed.');
