const fs = require('fs');

const gradle = fs.readFileSync('app/build.gradle.kts', 'utf8');

if (!gradle.includes('versionCode = 13')) {
  throw new Error('StudyLock 1.0.11 versionCode is missing.');
}
if (!gradle.includes('versionName = "1.0.11-simple-allow-wizard"')) {
  throw new Error('StudyLock 1.0.11 versionName is missing.');
}

console.log('StudyLock 1.0.11 version check passed.');
