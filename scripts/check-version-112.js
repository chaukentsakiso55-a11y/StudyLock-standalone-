const fs = require('fs');

const gradle = fs.readFileSync('app/build.gradle.kts', 'utf8');

if (!gradle.includes('versionCode = 14')) {
  throw new Error('StudyLock 1.0.12 versionCode is missing.');
}
if (!gradle.includes('versionName = "1.0.12-auto-ai-offline-dictionary"')) {
  throw new Error('StudyLock 1.0.12 versionName is missing.');
}
if (!gradle.includes('DICTIONARY_ASSET_VERSION')) {
  throw new Error('Offline dictionary asset version is missing.');
}

console.log('StudyLock 1.0.12 version check passed.');
