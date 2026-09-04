const fs = require('fs');

const policyJs = fs.readFileSync('app/src/main/assets/studylock-blocklist-policy.js', 'utf8');
const pickerJs = fs.readFileSync('app/src/main/assets/studylock-app-picker.js', 'utf8');
const bridgeJs = fs.readFileSync('app/src/main/assets/native-bridge.js', 'utf8');
const performanceJs = fs.readFileSync('app/src/main/assets/studylock-performance.js', 'utf8');
const mainKt = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/MainActivity.kt', 'utf8');
const bridgeKt = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/StudyLockNativeBridge.kt', 'utf8');
const storeKt = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/BlockedListPolicyStore.kt', 'utf8');

const requiredPolicyTokens = [
  '7L * 24L * 60L * 60L * 1000L',
  'EDIT_WINDOW_MS = 10L * 60L * 1000L',
  'ParentPasswordStore.verify',
  'getBlockedListPolicyState',
  'authorizeBlockedListOverride',
  'Weekly edit protection',
  'StudyLockBlockListPolicy'
];

for (const token of requiredPolicyTokens) {
  const combined = `${storeKt}\n${bridgeKt}\n${policyJs}`;
  if (!combined.includes(token)) throw new Error(`Missing weekly block-list token: ${token}`);
}

if (!pickerJs.includes('StudyLockBlockListPolicy.canEditNow')) {
  throw new Error('Installed-app picker does not enforce the weekly blocked-list policy.');
}

const policyIndex = mainKt.indexOf('studylock-blocklist-policy.js');
const pickerIndex = mainKt.indexOf('studylock-app-picker.js');
if (policyIndex < 0 || pickerIndex < 0 || policyIndex > pickerIndex) {
  throw new Error('Blocked-list policy must load before the installed-app picker.');
}

if (!bridgeJs.includes('FOCUS_SYNC_INTERVAL_MS = 10000')) {
  throw new Error('Focus synchronization was not reduced to the 10-second heartbeat.');
}
if (!bridgeJs.includes('CLOUD_SYNC_INTERVAL_MS = 30000')) {
  throw new Error('Cloud synchronization was not reduced to the 30-second cadence.');
}
if (bridgeJs.includes('structuralObserver.observe(timerEl')) {
  throw new Error('The one-second timer DOM is still attached to the structural MutationObserver.');
}
if (!performanceJs.includes('backdrop-filter: none')) {
  throw new Error('Android performance profile still uses expensive live backdrop blur.');
}
if (!mainKt.includes('setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)')) {
  throw new Error('WebView renderer priority protection is missing.');
}

console.log('Weekly blocked-list policy and performance integration checks passed.');
