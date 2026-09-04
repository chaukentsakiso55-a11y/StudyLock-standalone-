const fs = require('fs');

const gate = fs.readFileSync('app/src/main/assets/studylock-quiz-gate.js', 'utf8');
const main = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/MainActivity.kt', 'utf8');
const bridge = fs.readFileSync('app/src/main/assets/native-bridge.js', 'utf8');
const performance = fs.readFileSync('app/src/main/assets/studylock-performance.js', 'utf8');

const requiredGateTokens = [
  'studylock_exit_quiz_required_v1',
  'completeSession = function mandatoryQuizCompleteSession',
  'showQuizResults = function mandatoryQuizShowResults',
  'state.isLocked = true',
  'state.remainingSeconds = 0',
  'Use offline mixed quiz',
  'originalCompleteSession()'
];

for (const token of requiredGateTokens) {
  if (!gate.includes(token)) throw new Error(`Missing quiz gate token: ${token}`);
}

if (!main.includes('studylock-quiz-gate.js')) {
  throw new Error('MainActivity does not load the mandatory quiz gate script.');
}

if (!main.includes('studylock-performance.js')) {
  throw new Error('MainActivity does not load the Android performance profile.');
}

if (!main.includes('setWebContentsDebuggingEnabled(false)')) {
  throw new Error('WebView debugging is still enabled in the phone build.');
}

if (!bridge.includes('FOCUS_SYNC_INTERVAL_MS = 10000')) {
  throw new Error('The 1.0.8 focus-state heartbeat is not enabled.');
}

if (!bridge.includes('CLOUD_SYNC_INTERVAL_MS = 30000')) {
  throw new Error('The 1.0.8 cloud-state throttling is not enabled.');
}

if (!performance.includes('backdrop-filter: none') || !performance.includes('animation: none !important')) {
  throw new Error('The reduced-GPU Android style profile is incomplete.');
}

console.log('Mandatory quiz gate and performance integration checks passed.');
