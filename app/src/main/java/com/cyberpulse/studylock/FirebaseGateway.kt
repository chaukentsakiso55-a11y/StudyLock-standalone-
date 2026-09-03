package com.cyberpulse.studylock

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.json.JSONArray
import org.json.JSONObject

class FirebaseGateway(context: Context) {
    data class AuthResult(
        val success: Boolean,
        val message: String,
        val name: String? = null,
        val email: String? = null
    )

    val isConfigured: Boolean =
        BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()

    val firebaseApp: FirebaseApp? = if (isConfigured) {
        val options = FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
            .build()
        FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }
            ?: FirebaseApp.initializeApp(context, options, APP_NAME)
    } else {
        null
    }

    private val auth: FirebaseAuth? = firebaseApp?.let { FirebaseAuth.getInstance(it) }
    private val firestore: FirebaseFirestore? = firebaseApp?.let {
        FirebaseFirestore.getInstance(it)
    }

    init {
        firebaseApp?.let { app ->
            runCatching {
                val factory = if (BuildConfig.DEBUG) {
                    DebugAppCheckProviderFactory.getInstance()
                } else {
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                }
                FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(factory)
            }
        }
    }

    fun signUp(
        name: String,
        email: String,
        password: String,
        callback: (AuthResult) -> Unit
    ) {
        val firebaseAuth = auth ?: return callback(missingConfiguration())
        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    callback(AuthResult(false, "Firebase did not return a user account."))
                    return@addOnSuccessListener
                }
                val profile = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()
                user.updateProfile(profile).addOnCompleteListener {
                    saveProfile(user.uid, name, email)
                    callback(
                        AuthResult(
                            true,
                            "Account connected to StudyLock Firebase.",
                            name,
                            email
                        )
                    )
                }
            }
            .addOnFailureListener { error ->
                callback(AuthResult(false, friendlyMessage(error)))
            }
    }

    fun signIn(
        email: String,
        password: String,
        callback: (AuthResult) -> Unit
    ) {
        val firebaseAuth = auth ?: return callback(missingConfiguration())
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                callback(
                    AuthResult(
                        true,
                        "Signed in to StudyLock Firebase.",
                        user?.displayName,
                        user?.email
                    )
                )
            }
            .addOnFailureListener { error ->
                callback(AuthResult(false, friendlyMessage(error)))
            }
    }

    fun signInAnonymously(callback: (AuthResult) -> Unit) {
        val firebaseAuth = auth ?: return callback(missingConfiguration())
        firebaseAuth.signInAnonymously()
            .addOnSuccessListener {
                callback(AuthResult(true, "Guest session connected to Firebase."))
            }
            .addOnFailureListener { error ->
                callback(AuthResult(false, friendlyMessage(error)))
            }
    }

    fun syncState(json: String) {
        val uid = auth?.currentUser?.uid ?: return
        val database = firestore ?: return
        val objectValue = runCatching { JSONObject(json) }.getOrNull() ?: return
        val state = jsonObjectToMap(objectValue).toMutableMap()
        state["updatedAt"] = FieldValue.serverTimestamp()
        state["schemaVersion"] = 1
        database.collection("users")
            .document(uid)
            .collection("studylock")
            .document("current")
            .set(state, SetOptions.merge())
    }

    private fun saveProfile(uid: String, name: String, email: String) {
        val database = firestore ?: return
        database.collection("users").document(uid).set(
            mapOf(
                "name" to name,
                "email" to email,
                "product" to "StudyLock Exact",
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
    }

    private fun missingConfiguration() = AuthResult(
        false,
        "Firebase is not configured for this StudyLock build."
    )

    private fun friendlyMessage(error: Throwable): String {
        val raw = error.localizedMessage.orEmpty().trim()
        return if (raw.isBlank()) "Firebase could not complete that request." else raw
    }

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
        const val APP_NAME = "studylock-exact"
    }
}
