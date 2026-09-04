const fs = require('fs');

const performance = fs.readFileSync('app/src/main/assets/studylock-performance.js', 'utf8');
const bridge = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/StudyLockNativeBridge.kt', 'utf8');
const main = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/MainActivity.kt', 'utf8');
const focusStore = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/FocusStateStore.kt', 'utf8');

const requiredPerformanceTokens = [
  'viewport-fit=cover',
  'max-width: none !important',
  'min-height: 100dvh !important',
  'backdrop-filter: none !important',
  '.orb {',
  'display: none !important',
  'content-visibility: auto'
];

for (const token of requiredPerformanceTokens) {
  if (!performance.includes(token)) {
    throw new Error(`Missing fullscreen/performance token: ${token}`);
  }
}

const requiredBridgeTokens = [
  'fun enterImmersiveFullscreen()',
  'WindowInsetsControllerCompat',
  'WindowInsetsCompat.Type.systemBars()',
  'LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES',
  'cachedBlockedEntries',
  'cachedBlockedPackages',
  'if (active && !wasActive)'
];

for (const token of requiredBridgeTokens) {
  if (!bridge.includes(token)) {
    throw new Error(`Missing native fullscreen/performance token: ${token}`);
  }
}

if (!main.includes('setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)')) {
  throw new Error('Renderer priority is not set to IMPORTANT.');
}
if (!main.includes('onRenderProcessGone')) {
  throw new Error('WebView renderer crash recovery is missing.');
}
if (!main.includes('bridgeAttachInProgress')) {
  throw new Error('Staggered bridge startup protection is missing.');
}
if (!main.includes('fonts.googleapis.com') || !main.includes('fonts.gstatic.com')) {
  throw new Error('Remote font blocking for stable startup is missing.');
}
if (!focusStore.includes('END_TIME_DRIFT_TOLERANCE_MS')) {
  throw new Error('Redundant focus-state write suppression is missing.');
}

console.log('Fullscreen and ultra-performance integration checks passed.');
