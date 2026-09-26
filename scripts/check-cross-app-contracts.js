const fs = require('fs');
const assert = require('assert');

const student = fs.readFileSync('app/src/main/assets/studylock-teacher-classroom.js', 'utf8');
const teacher = fs.readFileSync('teacher/src/main/java/com/cyberpulse/studylock/teacher/TeacherMainActivity.kt', 'utf8');
const rules = fs.readFileSync('firestore.rules', 'utf8');
const html = fs.readFileSync('app/src/main/assets/studylock-exact.html', 'utf8');
const firebase = fs.readFileSync('app/src/main/assets/studylock-firebase-parent.js', 'utf8');

const checks = [
  ['Teacher and Student use the same classroom collection',
    student.includes("studylock_teacher_classes") && teacher.includes('studylock_teacher_classes')],
  ['Student classroom presence has a recurring heartbeat',
    student.includes('setInterval(()=>presence(),25000)')],
  ['Teacher emits system ping with a ping identifier',
    teacher.includes('"kind" to "system_ping"') && teacher.includes('"pingId" to lastPingId')],
  ['Student acknowledges Teacher ping in its presence document',
    student.includes("lastPingAckId:x.pingId")],
  ['Student questions use the classroom message collection',
    student.includes("senderRole:'student'") && student.includes("kind:'question'")],
  ['Teacher receives Student questions',
    teacher.includes('data["senderRole"] != "student"') && teacher.includes('data["kind"] != "question"')],
  ['Teacher authority stays separate from Parent controls',
    teacher.includes('Parent security') && rules.includes('studylock_teacher_classes')],
  ['Firestore rules require signed-in Teacher classroom access',
    rules.includes('teacherUid == request.auth.uid')],
  ['Student exposes a scoped trusted Parent bridge',
    html.includes('window.StudyLockParentControl = {')],
  ['Remote blocked-list bridge replaces the live list before native sync',
    html.includes('blockedSites.splice(0, blockedSites.length, ...next)') &&
      html.includes("new CustomEvent('studylock:blocklist-changed')")],
  ['Firebase fallback binds through the scoped Student bridge',
    firebase.includes('installParentTransport') && firebase.includes('completePairing')],
  ['Firebase command listener replays recent queued command history',
    firebase.includes(".orderBy('createdAtMs', 'desc')") && firebase.includes('.limit(100)')],
  ['Student cloud acknowledgements are exposed to the command layer',
    firebase.includes('sendToParent')]
];

checks.forEach(([name, ok], index) => {
  assert.equal(ok, true, name);
  console.log('PASS ' + (index + 1) + ': ' + name);
});
console.log('Cross-app source contract matrix passed: ' + checks.length + ' cases.');
