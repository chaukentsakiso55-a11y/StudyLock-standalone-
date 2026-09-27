package com.cyberpulse.studylock.teacher

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.security.SecureRandom
import java.util.UUID

class TeacherMainActivity : ComponentActivity() {
    private val bg = Color.rgb(19, 12, 3)
    private val card = Color.rgb(43, 28, 8)
    private val card2 = Color.rgb(58, 36, 7)
    private val accent = Color.rgb(255, 177, 0)
    private val accent2 = Color.rgb(255, 133, 27)
    private val mainText = Color.rgb(255, 249, 233)
    private val muted = Color.rgb(225, 202, 153)
    private val good = Color.rgb(93, 214, 135)

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var uid = ""
    private var classCode = ""
    private var studentsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    private var lastPingId = ""
    private val seenQuestions = mutableSetOf<String>()

    private lateinit var codeView: TextView
    private lateinit var cloudView: TextView
    private lateinit var studentsView: TextView
    private lateinit var questionsView: TextView
    private lateinit var deliveryView: TextView
    private lateinit var messageInput: EditText
    private lateinit var dueInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        setContentView(buildUi())
        initialiseFirebase()
    }

    override fun onDestroy() {
        studentsListener?.remove()
        messagesListener?.remove()
        super.onDestroy()
    }

    private fun initialiseFirebase() {
        if (BuildConfig.FIREBASE_API_KEY.isBlank() || BuildConfig.FIREBASE_PROJECT_ID.isBlank()) {
            cloudView.text = "Firebase configuration missing"
            return
        }
        runCatching {
            val options = FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .build()
            val app = FirebaseApp.getApps(this).firstOrNull { it.name == FIREBASE_APP_NAME }
                ?: FirebaseApp.initializeApp(this, options, FIREBASE_APP_NAME)
            auth = FirebaseAuth.getInstance(app)
            db = FirebaseFirestore.getInstance(app)
            val current = auth.currentUser
            if (current != null) {
                onSignedIn(current.uid)
            } else {
                cloudView.text = "Connecting StudyLock classroom..."
                auth.signInAnonymously()
                    .addOnSuccessListener { result ->
                        val id = result.user?.uid.orEmpty()
                        if (id.isNotBlank()) {
                            onSignedIn(id)
                        } else {
                            cloudView.text = "Could not create Teacher session"
                        }
                    }
                    .addOnFailureListener {
                        cloudView.text = "Teacher cloud unavailable: " + friendly(it)
                    }
            }
        }.onFailure {
            cloudView.text = "Teacher startup error: " + friendly(it)
        }
    }

    private fun onSignedIn(id: String) {
        uid = id
        cloudView.text = "Firebase ready"
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        classCode = prefs.getString(CODE_KEY, null)?.takeIf { it.matches(Regex("\\d{6}")) }
            ?: newCode().also { prefs.edit().putString(CODE_KEY, it).apply() }
        codeView.text = classCode.chunked(3).joinToString(" ")
        createOrOpenClass()
    }

    private fun createOrOpenClass() {
        if (uid.isBlank() || classCode.isBlank()) return
        val now = System.currentTimeMillis()
        classRef().set(
            mapOf(
                "teacherUid" to uid,
                "active" to true,
                "updatedAtMs" to now,
                "createdAtMs" to now
            ),
            SetOptions.merge()
        ).addOnSuccessListener {
            cloudView.text = "Classroom online - share code " + classCode
            listenClassroom()
        }.addOnFailureListener {
            cloudView.text = "Could not open classroom: " + friendly(it)
        }
    }

    private fun listenClassroom() {
        studentsListener?.remove()
        messagesListener?.remove()
        studentsListener = classRef().collection("students").addSnapshotListener { snapshot, error ->
            if (error != null) {
                studentsView.text = "Student presence error: " + friendly(error)
                return@addSnapshotListener
            }
            val now = System.currentTimeMillis()
            val rows = snapshot?.documents.orEmpty().map { doc ->
                val data = doc.data.orEmpty()
                val name = data["displayName"]?.toString()?.takeIf { it.isNotBlank() } ?: "Student"
                val last = (data["lastSeenMs"] as? Number)?.toLong() ?: 0L
                val online = now - last < 90_000L
                val ack = data["lastPingAckId"]?.toString().orEmpty()
                Triple(name, online, ack)
            }
            val onlineCount = rows.count { it.second }
            studentsView.text = if (rows.isEmpty()) {
                "No students joined yet."
            } else {
                buildString {
                    append(onlineCount).append("/").append(rows.size).append(" students online")
                    rows.forEach { row ->
                        append("\n").append(if (row.second) "● " else "○ ").append(row.first)
                    }
                }
            }
            if (lastPingId.isNotBlank()) {
                val acks = rows.count { it.third == lastPingId }
                deliveryView.text = "Connection test: " + acks + "/" + rows.size + " students acknowledged"
                deliveryView.setTextColor(if (acks > 0) good else muted)
            }
        }

        messagesListener = classRef().collection("messages").addSnapshotListener { snapshot, error ->
            if (error != null) {
                questionsView.text = "Question sync error: " + friendly(error)
                return@addSnapshotListener
            }
            val newQuestions = snapshot?.documentChanges.orEmpty()
                .filter { it.type == DocumentChange.Type.ADDED }
                .mapNotNull { change ->
                    val data = change.document.data
                    if (data["senderRole"] != "student" || data["kind"] != "question") return@mapNotNull null
                    if (!seenQuestions.add(change.document.id)) return@mapNotNull null
                    data["text"]?.toString()?.takeIf { it.isNotBlank() }
                }
            if (newQuestions.isNotEmpty()) {
                val existing = questionsView.text.toString()
                    .takeIf { it != "No student questions yet." }
                    .orEmpty()
                val fresh = newQuestions.joinToString("\n\n") { "Student: " + it }
                questionsView.text = (fresh + if (existing.isBlank()) "" else "\n\n" + existing).take(5000)
            }
        }
    }

    private fun regenerateClass() {
        if (uid.isBlank()) return
        classCode = newCode()
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(CODE_KEY, classCode).apply()
        codeView.text = classCode.chunked(3).joinToString(" ")
        deliveryView.text = "New class code created"
        createOrOpenClass()
    }

    private fun send(kind: String) {
        val body = messageInput.text.toString().trim()
        if (body.isBlank()) {
            deliveryView.text = "Type classroom content first."
            return
        }
        if (uid.isBlank() || classCode.isBlank()) {
            deliveryView.text = "Teacher is not connected yet."
            return
        }
        val now = System.currentTimeMillis()
        val message = mutableMapOf<String, Any>(
            "senderUid" to uid,
            "senderRole" to "teacher",
            "kind" to kind,
            "text" to body,
            "createdAtMs" to now,
            "fallbackId" to "teacher-" + now + "-" + SecureRandom().nextInt(9999)
        )
        val due = dueInput.text.toString().trim()
        if (due.isNotBlank()) message["when"] = due
        classRef().collection("messages").add(message)
            .addOnSuccessListener {
                messageInput.text.clear()
                deliveryView.text = kind.replace('_', ' ') + " sent to classroom"
                deliveryView.setTextColor(good)
            }
            .addOnFailureListener {
                deliveryView.text = "Not sent: " + friendly(it)
                deliveryView.setTextColor(muted)
            }
    }

    private fun pingStudents() {
        if (uid.isBlank()) return
        lastPingId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        classRef().collection("messages").add(
            mapOf(
                "senderUid" to uid,
                "senderRole" to "teacher",
                "kind" to "system_ping",
                "text" to "",
                "pingId" to lastPingId,
                "createdAtMs" to now,
                "fallbackId" to "ping-" + now
            )
        ).addOnSuccessListener {
            deliveryView.text = "Connection test sent - waiting for Student acknowledgement..."
            deliveryView.setTextColor(muted)
        }.addOnFailureListener {
            deliveryView.text = "Connection test failed: " + friendly(it)
        }
    }

    private fun classRef() = db.collection(CLASS_COLLECTION).document(classCode)

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(36))
        }
        scroll.addView(
            root,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        root.addView(label("StudyLock Teacher", 28f, mainText, true))
        root.addView(label("Yellow Liquid Classroom", 13f, accent, true).top(3))

        codeView = label("--- ---", 38f, accent, true).apply { gravity = Gravity.CENTER }
        val codeCard = cardView()
        codeCard.addView(label("CLASS CODE", 11f, muted, true))
        codeCard.addView(codeView)
        codeCard.addView(button("Generate new class code", false) { regenerateClass() }.top(10))
        root.addView(codeCard.top(18))

        cloudView = label("Starting Firebase...", 12f, muted, false)
        root.addView(cloudView.top(8))

        val studentsCard = cardView()
        studentsCard.addView(label("Students", 20f, mainText, true))
        studentsView = label("Waiting for classroom...", 14f, muted, false)
        studentsCard.addView(studentsView.top(8))
        studentsCard.addView(button("Test connection", true) { pingStudents() }.top(12))
        deliveryView = label("", 12f, muted, false)
        studentsCard.addView(deliveryView.top(8))
        root.addView(studentsCard.top(16))

        val sendCard = cardView()
        sendCard.addView(label("Send to students", 20f, mainText, true))
        messageInput = input("Message, assignment, resource or study note")
        dueInput = input("Due / when (optional)")
        sendCard.addView(messageInput.top(10))
        sendCard.addView(dueInput.top(8))
        sendCard.addView(button("Send announcement", true) { send("announcement") }.top(10))
        sendCard.addView(button("Send assignment", false) { send("assignment") }.top(7))
        sendCard.addView(button("Send resource / study guide", false) { send("resource") }.top(7))
        root.addView(sendCard.top(16))

        val questionCard = cardView()
        questionCard.addView(label("Student questions", 20f, mainText, true))
        questionsView = label("No student questions yet.", 14f, muted, false)
        questionCard.addView(questionsView.top(10))
        root.addView(questionCard.top(16))

        root.addView(
            label(
                "Teacher controls classroom learning only. Parent security, app blocking and protected focus controls stay with StudyLock Parent.",
                12f,
                muted,
                false
            ).top(14)
        )
        return scroll
    }

    private fun cardView() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(card, 24f, accent, 1)
    }

    private fun button(title: String, strong: Boolean, action: () -> Unit) = Button(this).apply {
        text = title
        isAllCaps = false
        setTextColor(if (strong) Color.rgb(35, 21, 2) else mainText)
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(if (strong) accent else card2, 18f, if (strong) accent2 else accent, 1)
        setOnClickListener { action() }
    }

    private fun input(hintText: String) = EditText(this).apply {
        hint = hintText
        setHintTextColor(Color.rgb(167, 144, 102))
        setTextColor(mainText)
        textSize = 14f
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = rounded(Color.rgb(31, 20, 7), 16f, Color.rgb(104, 69, 15), 1)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        minLines = 2
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(fill: Int, radius: Float, stroke: Int, strokeWidth: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radius.toInt()).toFloat()
            setStroke(dp(strokeWidth), stroke)
        }

    private fun View.top(value: Int): View = apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(value) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun friendly(error: Throwable) =
        error.localizedMessage?.takeIf { it.isNotBlank() } ?: "Firebase request failed"

    private fun newCode() =
        SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')

    companion object {
        private const val FIREBASE_APP_NAME = "studylock-teacher-native"
        private const val CLASS_COLLECTION = "studylock_teacher_classes"
        private const val PREFS = "studylock_teacher"
        private const val CODE_KEY = "class_code"
    }
}
