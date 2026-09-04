const fs = require('fs');
const gradle = fs.readFileSync('app/build.gradle.kts', 'utf8');
if (!gradle.includes('versionCode = 11')) throw new Error('StudyLock 1.0.9 versionCode is missing.');
if (!gradle.includes('versionName = "1.0.9-fullscreen-ultra-performance"')) throw new Error('StudyLock 1.0.9 versionName is missing.');
console.log('StudyLock 1.0.9 version check passed.');
