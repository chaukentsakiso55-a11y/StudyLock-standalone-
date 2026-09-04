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
  'Allow unrestricted battery use',
  'Allow app blocking',
  'Allow device administrator',
  '.setPositiveButton("Allow")',
  'Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',
  'Settings.ACTION_ACCESSIBILITY_SETTINGS',
  'DeviceProtectionController.requestAdminActivation(activity)',
  'isIgnoringBatteryOptimizations',
  'isAdminActive',
  'ENABLED_ACCESSIBILITY_SERVICES',
  'Allow restricted settings',
  'Open App info',
  'setup_complete',
  'markSetupComplete(activity, true)',
  'showNextPermission(activity)'
];

for (const token of requiredFlowTokens) {
  if (!app.includes(token)) throw new Error(`Missing simple Allow wizard token: ${token}`);
}

if (!gradle.includes('versionCode = 14') || !gradle.includes('1.0.12-auto-ai-offline-dictionary')) {
  throw new Error('StudyLock 1.0.12 version bump is missing.');
}

if (app.includes('WRITE_SECURE_SETTINGS') || app.includes('pm grant') || app.includes('device_policy set-active-admin')) {
  throw new Error('Protection onboarding must not attempt to bypass Android approval screens.');
}

if (app.includes('.setPositiveButton("Start setup")')) {
  throw new Error('The old Start setup confirmation should not remain in the simple Allow wizard.');
}

console.log('Simple Allow protection wizard checks passed.');
