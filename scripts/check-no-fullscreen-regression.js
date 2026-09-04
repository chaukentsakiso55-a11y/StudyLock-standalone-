const fs = require('fs');
const html = fs.readFileSync('app/src/main/assets/studylock-exact.html', 'utf8');
const performance = fs.readFileSync('app/src/main/assets/studylock-performance.js', 'utf8');
if (!html.includes('max-width:440px')) throw new Error('Exact HTML reference unexpectedly changed.');
if (!performance.includes('max-width: none !important')) throw new Error('Android fullscreen override is missing.');
console.log('Exact HTML preserved and Android fullscreen override present.');
