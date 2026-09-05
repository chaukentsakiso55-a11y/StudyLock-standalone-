package com.cyberpulse.studylock

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class ParentFirebaseSync(
    private val activity: MainActivity,
    firebaseApp: FirebaseApp?
) {
    private val appContext = activity.applicationContext
    private val auth = firebaseApp?.let { FirebaseAuth.getInstance(it) }
    private val firestore = firebaseApp?.let { FirebaseFirestore.getInstance(it) }
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val messageListeners = ConcurrentHashMap<String, ListenerRegistration>()
    private val channelListeners = ConcurrentHashMap<String, ListenerRegistration>()
    private val seenMessages = ConcurrentHashMap.newKeySet<String>()

    fun publish(topic: String, payload: String) {
        val code = codeFromTopic(topic) ?: return
        val database = firestore ?: return
        ensureIdentity { uid ->
            if (uid == null) return@ensureIdentity
            val message = runCatching { JSONObject(payload) }.getOrElse { JSONObject() }
            val channel = database.collection(CHANNELS).document(code)
            when (message.optString("type")) {
                "hello" -> {
                    val now = System.currentTimeMillis()
                    database.runTransaction { transaction ->
                        val snapshot = transaction.get(channel)
                        if (!snapshot.exists()) error("Pairing code not found")
                        val expiresAt = snapshot.getLong("expiresAtMs") ?: 0L
                        if (expiresAt in 1 until now) error("Pairing code expired")
                        val existingStudent = snapshot.getString("studentUid")
                        if (!existingStudent.isNullOrBlank() && existingStudent != uid) {
                            error("Pairing code already connected")
                        }
                        transaction.set(
                            channel,
                            mapOf(
                                "studentUid" to uid,
                                "connected" to true,
                                "studentOnline" to true,
                                "lastStudentSeenMs" to now
                            ),
                            SetOptions.merge()
                        )
                    }.addOnSuccessListener {
                        updateStudentState(channel, message)
                        addMessage(channel, uid, "student", payload)
                    }.addOnFailureListener { error ->
                        activity.emitToast(error.localizedMessage ?: "Could not connect to the parent dashboard.")
                    }
                }
                "state" -> {
                    channel.get().addOnSuccessListener { snapshot ->
                        if (snapshot.getString("studentUid") == uid) {
                            updateStudentState(channel, message)
                        }
                    }
                }
                "bye" -> {
                    channel.get().addOnSuccessListener { snapshot ->
                        if (snapshot.getString("studentUid") == uid) {
                            channel.set(
                                mapOf(
                                    "studentOnline" to false,
                                    "lastStudentSeenMs" to System.currentTimeMillis()
                                ),
                                SetOptions.merge()
                            )
                            addMessage(channel, uid, "student", payload)
                        }
                    }
                }
                else -> addMessage(channel, uid, "student", payload)
            }
        }
    }

    fun subscribe(topic: String) {
        val code = codeFromTopic(topic) ?: return
        if (messageListeners.containsKey(topic)) return
        val database = firestore ?: return
        ensureIdentity { uid ->
            if (uid == null || messageListeners.containsKey(topic)) return@ensureIdentity
            val channel = database.collection(CHANNELS).document(code)
            val startedAt = System.currentTimeMillis() - 1_500L

            val messageListener = channel.collection("messages")
                .whereGreaterThanOrEqualTo("createdAtMs", startedAt)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    snapshot.documentChanges.forEach { change ->
                        if (change.type != DocumentChange.Type.ADDED) return@forEach
                        if (!seenMessages.add(change.document.id)) return@forEach
                        if (change.document.getString("senderRole") != "parent") return@forEach
                        val payload = change.document.getString("payload").orEmpty()
                        if (payload.isBlank()) return@forEach
                        applyCommandPayload(payload)
                        emitRelayMessage(topic, payload)
                    }
                }
            messageListeners[topic] = messageListener

            val channelListener = channel.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                if (snapshot.getString("studentUid") == uid) applyChannelControls(snapshot)
            }
            channelListeners[topic] = channelListener
        }
    }

    fun unsubscribe(topic: String) {
        messageListeners.remove(topic)?.remove()
        channelListeners.remove(topic)?.remove()
    }

    fun maybeApplyAutoStudy() {
        if (!prefs.getBoolean(KEY_AUTO_ENABLED, false)) return
        if (FocusStateStore.isActive(appContext)) return
        val scheduledMinute = prefs.getInt(KEY_AUTO_START_MINUTE, -1)
        if (scheduledMinute !in 0..1439) return

        val calendar = Calendar.getInstance()
        val currentMinute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        if (currentMinute < scheduledMinute) return

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (prefs.getString(KEY_LAST_AUTO_DATE, "") == today) return
        prefs.edit().putString(KEY_LAST_AUTO_DATE, today).apply()
        remoteStart(prefs.getInt(KEY_AUTO_MINUTES, 25), "Auto Study started from the parent schedule.")
    }

    fun stateJson(): String = JSONObject().apply {
        put("enabled", prefs.getBoolean(KEY_AUTO_ENABLED, false))
        put("minutes", prefs.getInt(KEY_AUTO_MINUTES, 25))
        put("startMinuteOfDay", prefs.getInt(KEY_AUTO_START_MINUTE, -1))
        put("lastAutoDate", prefs.getString(KEY_LAST_AUTO_DATE, ""))
    }.toString()

    fun close() {
        messageListeners.values.forEach { it.remove() }
        channelListeners.values.forEach { it.remove() }
        messageListeners.clear()
        channelListeners.clear()
        seenMessages.clear()
    }

    private fun ensureIdentity(callback: (String?) -> Unit) {
        val firebaseAuth = auth ?: return callback(null)
        firebaseAuth.currentUser?.uid?.let { return callback(it) }
        firebaseAuth.signInAnonymously()
            .addOnSuccessListener { callback(it.user?.uid) }
            .addOnFailureListener { callback(null) }
    }

    private fun updateStudentState(
        channel: com.google.firebase.firestore.DocumentReference,
        message: JSONObject
    ) {
        val state = message.optJSONObject("state") ?: return
        channel.set(
            mapOf(
                "studentState" to jsonObjectToMap(state),
                "studentOnline" to true,
                "lastStudentSeenMs" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        )
    }

    private fun addMessage(
        channel: com.google.firebase.firestore.DocumentReference,
        uid: String,
        role: String,
        payload: String
    ) {
        val type = runCatching { JSONObject(payload).optString("type") }.getOrDefault("")
        channel.collection("messages").add(
            mapOf(
                "senderUid" to uid,
                "senderRole" to role,
                "type" to type,
                "payload" to payload,
                "createdAtMs" to System.currentTimeMillis()
            )
        )
    }

    private fun applyChannelControls(snapshot: DocumentSnapshot) {
        val enabled = snapshot.getBoolean("autoStudyEnabled") ?: false
        val minutes = (snapshot.getLong("autoStudyMinutes") ?: 25L).toInt().coerceIn(25, 300)
        val startMinute = (snapshot.getLong("autoStudyStartMinuteOfDay") ?: -1L).toInt()
        prefs.edit()
            .putBoolean(KEY_AUTO_ENABLED, enabled)
            .putInt(KEY_AUTO_MINUTES, minutes)
            .putInt(KEY_AUTO_START_MINUTE, startMinute)
            .apply()

        val startRequestId = snapshot.getString("startRequestId").orEmpty()
        if (startRequestId.isNotBlank() && startRequestId != prefs.getString(KEY_LAST_START_REQUEST, "")) {
            prefs.edit().putString(KEY_LAST_START_REQUEST, startRequestId).apply()
            val requestedMinutes = (snapshot.getLong("startRequestMinutes") ?: minutes.toLong()).toInt()
            remoteStart(requestedMinutes, "Focus session started from the parent dashboard.")
        }

        val endRequestId = snapshot.getString("endRequestId").orEmpty()
        if (endRequestId.isNotBlank() && endRequestId != prefs.getString(KEY_LAST_END_REQUEST, "")) {
            prefs.edit().putString(KEY_LAST_END_REQUEST, endRequestId).apply()
            remoteEnd()
        }

        maybeApplyAutoStudy()
    }

    private fun applyCommandPayload(payload: String) {
        val message = runCatching { JSONObject(payload) }.getOrNull() ?: return
        if (message.optString("type") != "cmd") return
        when (message.optString("action")) {
            "start" -> remoteStart(message.optInt("minutes", 25), "Focus session started from the parent dashboard.")
            "end" -> remoteEnd()
            "set_auto" -> {
                prefs.edit()
                    .putBoolean(KEY_AUTO_ENABLED, message.optBoolean("enabled", false))
                    .putInt(KEY_AUTO_MINUTES, message.optInt("minutes", 25).coerceIn(25, 300))
                    .putInt(KEY_AUTO_START_MINUTE, message.optInt("startMinuteOfDay", -1))
                    .apply()
                maybeApplyAutoStudy()
            }
        }
    }

    private fun remoteStart(minutes: Int, toast: String) {
        if (FocusStateStore.isActive(appContext)) return
        val safeMinutes = minutes.coerceIn(25, 300)
        activity.runJavascript(
            "(function(){" +
                "var mins=$safeMinutes;" +
                "var buttons=Array.prototype.slice.call(document.querySelectorAll('.preset-btn'));" +
                "var button=buttons.find(function(b){return parseInt(b.dataset.mins||'0',10)===mins;})||buttons[0];" +
                "if(button){button.click();}" +
                "if(typeof startSession==='function'){startSession();}" +
            "})();"
        )
        activity.emitToast(toast)
    }

    private fun remoteEnd() {
        if (!FocusStateStore.isActive(appContext)) return
        activity.runJavascript("if(typeof endSessionEarly==='function'){endSessionEarly();}")
        activity.emitToast("Focus session ended from the parent dashboard.")
    }

    private fun emitRelayMessage(topic: String, payload: String) {
        activity.runJavascript(
            "window.StudyLockFirebaseRelayHooks?.onMessage(" +
                "${JSONObject.quote(topic)},${JSONObject.quote(payload)});"
        )
    }

    private fun codeFromTopic(topic: String): String? =
        Regex("^studylock-pair-(\\d{6})$").matchEntire(topic)?.groupValues?.getOrNull(1)

    private fun jsonObjectToMap(value: JSONObject): Map<String, Any?> = buildMap {
        val keys = value.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            put(key, jsonValue(value.opt(key)))
        }
    }

    private fun jsonArrayToList(value: JSONArray): List<Any?> = buildList {
        for (index in 0 until value.length()) add(jsonValue(value.opt(index)))
    }

    private fun jsonValue(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> jsonArrayToList(value)
        is Boolean, is Number, is String -> value
        else -> value.toString()
    }

    private companion object {
        const val CHANNELS = "studylock_parent_channels"
        const val PREFS = "studylock_parent_controls_v2"
        const val KEY_AUTO_ENABLED = "auto_enabled"
        const val KEY_AUTO_MINUTES = "auto_minutes"
        const val KEY_AUTO_START_MINUTE = "auto_start_minute"
        const val KEY_LAST_AUTO_DATE = "last_auto_date"
        const val KEY_LAST_START_REQUEST = "last_start_request"
        const val KEY_LAST_END_REQUEST = "last_end_request"
    }
}
