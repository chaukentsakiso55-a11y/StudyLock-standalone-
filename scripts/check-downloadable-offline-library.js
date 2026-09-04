const fs = require('fs');

const manifest = fs.readFileSync('app/src/main/AndroidManifest.xml', 'utf8');
const bridge = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/StudyLockNativeBridge.kt', 'utf8');
const service = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/OfflineLibraryDownloadService.kt', 'utf8');
const store = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/OfflineTutorLibraryStore.kt', 'utf8');
const gateway = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/OfflineTutorReferenceGateway.kt', 'utf8');
const ui = fs.readFileSync('app/src/main/assets/studylock-offline-library-ui.js', 'utf8');
const dictionaryJs = fs.readFileSync('app/src/main/assets/studylock-offline-dictionary.js', 'utf8');

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
  'freeBytes'
]) {
  if (!store.includes(token)) throw new Error(`Missing library store token: ${token}`);
}

for (const token of [
  'SQLiteDatabase.OPEN_READONLY',
  'reference_fts',
  'References:',
  'generated only from the installed StudyLock reference library'
]) {
  if (!gateway.includes(token)) throw new Error(`Missing offline reference retrieval token: ${token}`);
}

for (const token of [
  'Offline Tutor Library',
  'Download Library',
  'Download Update',
  'Cancel',
  'Remove',
  'getOfflineLibraryState',
  'startOfflineLibraryDownload'
]) {
  if (!ui.includes(token)) throw new Error(`Missing offline library menu token: ${token}`);
}

if (!dictionaryJs.includes('studylock-offline-library-ui.js')) {
  throw new Error('Offline Tutor Library menu script is not loaded by StudyLock.');
}

console.log('Downloadable Offline Tutor Library checks passed.');
