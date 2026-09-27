const fs = require('fs');
const vm = require('vm');
const assert = require('assert');

const source = fs.readFileSync('app/src/main/assets/studylock-direct-parent.js', 'utf8');
const storage = new Map();
const localStorage = {
  getItem: key => storage.has(key) ? storage.get(key) : null,
  setItem: (key, value) => storage.set(key, String(value)),
  removeItem: key => storage.delete(key)
};

const student = {
  active: false,
  paused: false,
  accessibility: true,
  blocked: [],
  minutes: 0,
  endCount: 0,
  startCount: 0
};
const directAcks = [];
const cloudAcks = [];
let scheduleChecks = 0;
let settingsLocks = 0;

const control = {
  snapshot() {
    return {
      focusActive: student.active,
      focusPaused: student.paused,
      remainingSeconds: student.active ? student.minutes * 60 : 0,
      blockedEntries: student.blocked.slice(),
      protection: { accessibilityEnabled: student.accessibility }
    };
  },
  startFocus(minutes) {
    student.startCount += 1;
    if (!student.accessibility) return false;
    student.active = true;
    student.paused = false;
    student.minutes = Number(minutes || 25);
    return true;
  },
  pauseFocus() {
    if (!student.active) return false;
    student.paused = true;
    return true;
  },
  resumeFocus() {
    if (!student.active || !student.paused) return false;
    student.paused = false;
    return true;
  },
  endFocus() {
    student.endCount += 1;
    student.active = false;
    student.paused = false;
    return true;
  },
  setBlocked(entries) {
    student.blocked = entries.slice();
    return student.blocked.slice();
  },
  pushParentState() {}
};

const native = {
  getState: () => JSON.stringify({ connected: true }),
  drainCommands: () => '[]',
  sendState: () => true,
  pair: () => {},
  ackCommand: (requestId, action, ok, message, state) => {
    directAcks.push({ requestId, action, ok, message, state: JSON.parse(state) });
    return true;
  }
};

const window = {
  StudyLockParentDirect: native,
  StudyLockParentControl: control,
  StudyLockNativeHooks: { showToast() {} },
  StudyLockNative: { getNativeState: () => JSON.stringify({ accessibilityEnabled: student.accessibility }) },
  StudyLockSettingsLock: { lockNow: () => { settingsLocks += 1; } },
  studyLockFirebaseParent: {
    maybeApplyAutoStudy: () => { scheduleChecks += 1; },
    sendToParent: message => { cloudAcks.push(message); return Promise.resolve(true); }
  }
};

const document = {
  getElementById: () => null,
  querySelector: () => null
};

const context = {
  window,
  document,
  localStorage,
  console,
  CustomEvent: function CustomEvent(type) { this.type = type; },
  setInterval: () => 0,
  clearInterval: () => {},
  setTimeout: fn => { fn(); return 0; },
  clearTimeout: () => {}
};
context.globalThis = context;
vm.runInNewContext(source, context, { filename: 'studylock-direct-parent.js' });
const api = window.StudyLockDirectParent;
assert(api, 'Parent control API did not initialize');

let seq = 0;
const command = (action, payload = {}, id) => {
  const requestId = id || ('test-' + (++seq));
  api.applyCommand({ type: 'cmd', action, requestId, payload });
  return requestId;
};
const lastAck = () => directAcks[directAcks.length - 1];
const tests = [];
function test(name, fn) {
  fn();
  tests.push(name);
  process.stdout.write('PASS ' + tests.length + ': ' + name + '\n');
}

test('remote 25-minute focus starts on Student and acknowledges execution', () => {
  const id = command('start_focus', { minutes: 25, subject: 'Geography' });
  assert.equal(student.active, true);
  assert.equal(student.minutes, 25);
  assert.equal(lastAck().requestId, id);
  assert.equal(lastAck().ok, true);
});

test('remote pause changes actual Student focus state', () => {
  command('pause_focus');
  assert.equal(student.paused, true);
  assert.equal(lastAck().ok, true);
});

test('remote resume changes actual Student focus state', () => {
  command('resume_focus');
  assert.equal(student.paused, false);
  assert.equal(lastAck().ok, true);
});

test('remote block list replaces the live Student list', () => {
  command('set_blocked', { entries: ['YouTube', 'Chrome', 'com.zhiliaoapp.musically'] });
  assert.deepEqual(student.blocked, ['YouTube', 'Chrome', 'com.zhiliaoapp.musically']);
  assert.equal(lastAck().ok, true);
});

test('execution acknowledgement is also offered to cloud fallback', () => {
  assert(cloudAcks.length > 0);
  assert.equal(cloudAcks[cloudAcks.length - 1].type, 'command_ack');
  assert.equal(cloudAcks[cloudAcks.length - 1].ok, true);
});

test('duplicate request ID is not executed twice', () => {
  const before = student.startCount;
  const id = command('start_focus', { minutes: 45 }, 'duplicate-id');
  assert.equal(id, 'duplicate-id');
  command('start_focus', { minutes: 60 }, 'duplicate-id');
  assert.equal(student.startCount, before + 1);
  assert.equal(student.minutes, 45);
});

test('remote end stops the actual Student focus state', () => {
  command('end_focus');
  assert.equal(student.active, false);
  assert.equal(lastAck().ok, true);
});

test('unknown command returns a failed execution acknowledgement', () => {
  command('does_not_exist');
  assert.equal(lastAck().ok, false);
  assert.match(lastAck().message, /Unknown/i);
});

test('focus start fails truthfully when Accessibility enforcement is unavailable', () => {
  student.accessibility = false;
  command('start_focus', { minutes: 25 });
  assert.equal(student.active, false);
  assert.equal(lastAck().ok, false);
  assert.match(lastAck().message, /Accessibility/i);
  student.accessibility = true;
});

test('schedule update persists and invokes Auto Study evaluation', () => {
  const before = scheduleChecks;
  command('set_schedule', { enabled: true, minutes: 60, startMinuteOfDay: 960 });
  const saved = JSON.parse(localStorage.getItem('studylock_parent_auto_v2'));
  assert.equal(saved.minutes, 60);
  assert.equal(saved.startMinuteOfDay, 960);
  assert.equal(scheduleChecks, before + 1);
});

test('parent goal persists across command handling', () => {
  command('set_goal', { text: 'Revise climate' });
  assert.equal(localStorage.getItem('studylock_parent_goal'), 'Revise climate');
  assert.equal(lastAck().ok, true);
});

test('settings-lock command executes the real settings-lock hook', () => {
  const before = settingsLocks;
  command('lock_settings');
  assert.equal(settingsLocks, before + 1);
  assert.equal(lastAck().ok, true);
});

test('refresh-state command acknowledges and includes current Student state', () => {
  command('refresh_state');
  assert.equal(lastAck().ok, true);
  assert.equal(typeof lastAck().state.focusActive, 'boolean');
  assert(Array.isArray(lastAck().state.blockedEntries));
});

test('rapid sequential commands preserve ordered final Student state', () => {
  command('start_focus', { minutes: 30 });
  command('pause_focus');
  command('resume_focus');
  command('end_focus');
  assert.equal(student.active, false);
  assert.equal(student.paused, false);
});

console.log('Parent command protocol matrix passed: ' + tests.length + ' functional cases.');
