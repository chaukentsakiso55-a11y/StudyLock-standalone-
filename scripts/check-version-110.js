const fs = require('fs');

const gradle = fs.readFileSync('app/build.gradle.kts', 'utf8');

if (!gradle.includes('versionCode = 12')) {
  throw new Error('StudyLock 1.0.10 versionCode is missing.');
}
if (!gradle.includes('versionName = "1.0.10-first-run-protection-setup"')) {
  throw new Error('StudyLock 1.0.10 versionName is missing.');
}

console.log('StudyLock 1.0.10 version check passed.');
