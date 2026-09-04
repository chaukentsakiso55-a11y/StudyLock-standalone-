const fs = require('fs');

const gradle = fs.readFileSync('app/build.gradle.kts', 'utf8');

if (!gradle.includes('versionCode = 15')) {
  throw new Error('StudyLock 1.0.13 versionCode is missing.');
}
if (!gradle.includes('versionName = "1.0.13-downloadable-offline-library"')) {
  throw new Error('StudyLock 1.0.13 versionName is missing.');
}
for (const token of ['OFFLINE_LIBRARY_STORAGE_PATH', 'OFFLINE_LIBRARY_VERSION', 'firebase-storage']) {
  if (!gradle.includes(token)) throw new Error(`StudyLock 1.0.13 library config is missing: ${token}`);
}

console.log('StudyLock 1.0.13 version check passed.');
