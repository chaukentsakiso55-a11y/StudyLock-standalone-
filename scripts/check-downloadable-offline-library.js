const fs = require('fs');

const manifest = fs.readFileSync('app/src/main/AndroidManifest.xml', 'utf8');
const bridge = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/StudyLockNativeBridge.kt', 'utf8');
const service = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/OfflineLibraryDownloadService.kt', 'utf8');
const store = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/OfflineTutorLibraryStore.kt', 'utf8');
const gateway = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/OfflineTutorReferenceGateway.kt', 'utf8');
const customStore = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/CustomReferenceLibraryStore.kt', 'utf8');
const ui = fs.readFileSync('app/src/main/assets/studylock-offline-library-ui.js', 'utf8');
const dictionaryJs = fs.readFileSync('app/src/main/assets/studylock-offline-dictionary.js', 'utf8');
const sourceUi = fs.readFileSync('app/src/main/assets/studylock-reference-sources.js', 'utf8');

for (const token of [
  'android.permission.ACCESS_NETWORK_STATE',
  'android.permission.FOREGROUND_SERVICE_DATA_SYNC',
  '.OfflineLibraryDownloadService',
  'foregroundServiceType="dataSync"'
]) {
  if (!manifest.includes(token)) throw new Error(`Missing offline library manifest token: ${token}`);
}

for (const token of [
  'getOfflineLibraryState',
  'refreshOfflineLibraryMetadata',
  'startOfflineLibraryDownload',
  'cancelOfflineLibraryDownload',
  'removeOfflineLibrary',
  'OfflineTutorLibraryStore.isInstalled',
  'requestOfflineTutor',
  'NET_CAPABILITY_VALIDATED'
]) {
  if (!bridge.includes(token)) throw new Error(`Missing offline tutor bridge token: ${token}`);
}

for (const token of [
  'FirebaseStorage.getInstance',
  'reference.getFile',
  'MIN_FREE_SPACE_BYTES',
  'SQLite format 3',
  'reference_entries',
  'MessageDigest.getInstance("SHA-256")',
  'startForeground'
]) {
  if (!service.includes(token)) throw new Error(`Missing safe library download token: ${token}`);
}

for (const token of [
  'filesDir',
  'studylock_reference_library.db',
  'installedVersion',
  'freeBytes',
  'ensureStarterInstalled'
]) {
  if (!store.includes(token)) throw new Error(`Missing library store token: ${token}`);
}

for (const token of [
  'SQLiteDatabase.OPEN_READONLY',
  'reference_fts',
  'References:',
  'generated only from installed StudyLock reference libraries',
  'CustomReferenceLibraryStore.libraryFiles'
]) {
  if (!gateway.includes(token)) throw new Error(`Missing offline reference retrieval token: ${token}`);
}

for (const token of [
  'ZipInputStream',
  'reference_entries',
  'MAX_DATABASES_PER_ZIP',
  'MAX_ZIP_TOTAL_BYTES',
  'importUri'
]) {
  if (!customStore.includes(token)) throw new Error(`Missing custom reference import token: ${token}`);
}

for (const token of [
  'Offline Study Libraries',
  'Download Full Library',
  'Download Update',
  'Cancel',
  'Remove Full Library',
  'getOfflineLibraryState',
  'startOfflineLibraryDownload',
  'English Dictionary',
  'Offline Tutor Reference Library'
]) {
  if (!ui.includes(token)) throw new Error(`Missing offline library menu token: ${token}`);
}

for (const token of [
  'https://cyber-pulse-info.netlify.app',
  'https://cyber-learn-projects.netlify.app',
  'studylock://import-library',
  'studylock://libraries'
]) {
  if (!sourceUi.includes(token)) throw new Error(`Missing reference website source token: ${token}`);
}

if (!dictionaryJs.includes('studylock-offline-library-ui.js') || !dictionaryJs.includes('studylock-reference-sources.js')) {
  throw new Error('Offline Tutor Library/reference website scripts are not loaded by StudyLock.');
}

console.log('Downloadable and custom Offline Tutor Library checks passed.');
