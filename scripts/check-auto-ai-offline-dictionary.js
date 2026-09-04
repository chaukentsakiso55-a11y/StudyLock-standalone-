const fs = require('fs');

const firebase = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/FirebaseGateway.kt', 'utf8');
const bridge = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/StudyLockNativeBridge.kt', 'utf8');
const dictionary = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/OfflineDictionaryGateway.kt', 'utf8');
const activity = fs.readFileSync('app/src/main/java/com/cyberpulse/studylock/MainActivity.kt', 'utf8');
const dictionaryJs = fs.readFileSync('app/src/main/assets/studylock-offline-dictionary.js', 'utf8');
const builder = fs.readFileSync('scripts/build_offline_dictionary.py', 'utf8');
const workflow = fs.readFileSync('.github/workflows/android.yml', 'utf8');

for (const token of [
  'ensureTutorIdentity',
  'signInAnonymously()',
  'AI Tutor'
]) {
  if (!firebase.includes(token)) throw new Error(`Missing automatic tutor identity token: ${token}`);
}

for (const token of [
  'ensureTutorIdentity',
  'lookupOfflineDictionary',
  'offlineDictionaryGateway',
  'firebaseAuthenticated',
  'managedAiConnected'
]) {
  if (!bridge.includes(token)) throw new Error(`Missing AI/dictionary bridge token: ${token}`);
}

for (const token of [
  'SQLiteDatabase.OPEN_READONLY',
  'Executors.newSingleThreadExecutor()',
  'FROM entries',
  'FROM aliases',
  'FROM lexicon'
]) {
  if (!dictionary.includes(token)) throw new Error(`Missing native offline dictionary token: ${token}`);
}

if (!activity.includes('studylock-offline-dictionary.js')) {
  throw new Error('Offline dictionary JavaScript is not injected into StudyLock.');
}
for (const token of ['lookupOfflineDictionary', 'originalLookup', 'onDictionaryResult']) {
  if (!dictionaryJs.includes(token)) throw new Error(`Missing offline dictionary JavaScript token: ${token}`);
}
for (const token of ['Princeton WordNet 3.1', 'MOBY_URL', 'lexicon']) {
  if (!builder.includes(token)) throw new Error(`Missing dictionary builder token: ${token}`);
}
for (const token of ['Build offline dictionary', 'build_offline_dictionary.py', 'check_offline_dictionary.py']) {
  if (!workflow.includes(token)) throw new Error(`CI does not verify the offline dictionary: ${token}`);
}

const combined = [firebase, bridge, dictionary, dictionaryJs].join('\n');
for (const forbidden of ['createApiKey(', 'generateApiKey(', 'WRITE_SECURE_SETTINGS', 'pm grant']) {
  if (combined.includes(forbidden)) throw new Error(`Unsafe or unwanted automatic credential/permission behavior found: ${forbidden}`);
}

console.log('Automatic managed AI and offline dictionary wiring checks passed.');
