const fs = require('fs');

const manifest = fs.readFileSync('app/src/main/AndroidManifest.xml', 'utf8');
const app = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/StudyLockApplication.kt', 'utf8');
const gradle = fs.readFileSync('app/build.gradle.kts', 'utf8');

const requiredManifestTokens = [
  'android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',
  'android:name=".StudyLockApplication"',
  'android.permission.BIND_DEVICE_ADMIN',
  'android.permission.BIND_ACCESSIBILITY_SERVICE'
];

for (const token of requiredManifestTokens) {
  if (!manifest.includes(token)) throw new Error(`Missing protection manifest token: ${token}`);
}

const requiredFlowTokens = [
  'Set up StudyLock protection',
  'Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',
  'Settings.ACTION_ACCESSIBILITY_SETTINGS',
  'DeviceProtectionController.requestAdminActivation(activity)',
  'isIgnoringBatteryOptimizations',
  'isAdminActive',
  'ENABLED_ACCESSIBILITY_SERVICES',
  'Allow restricted settings',
  'Open App info',
  'Try again'
];

for (const token of requiredFlowTokens) {
  if (!app.includes(token)) throw new Error(`Missing protection onboarding token: ${token}`);
}

if (!gradle.includes('versionCode = 12') || !gradle.includes('1.0.10-first-run-protection-setup')) {
  throw new Error('StudyLock 1.0.10 version bump is missing.');
}

if (app.includes('WRITE_SECURE_SETTINGS') || app.includes('pm grant') || app.includes('device_policy set-active-admin')) {
  throw new Error('Protection onboarding must not attempt to bypass Android approval screens.');
}

console.log('First-run protection onboarding checks passed.');
