const fs = require('fs');

const gate = fs.readFileSync('app/src/main/assets/studylock-quiz-gate.js', 'utf8');
const main = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/MainActivity.kt', 'utf8');
const bridge = fs.readFileSync('app/src/main/assets/native-bridge.js', 'utf8');

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

if (!bridge.includes('FOCUS_SYNC_INTERVAL_MS = 5000')) {
  throw new Error('Focus-state throttling is not enabled.');
}

if (!bridge.includes('CLOUD_SYNC_INTERVAL_MS = 15000')) {
  throw new Error('Cloud-state throttling is not enabled.');
}

console.log('Mandatory quiz gate and performance integration checks passed.');
