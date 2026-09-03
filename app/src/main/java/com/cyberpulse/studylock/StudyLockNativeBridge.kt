package com.cyberpulse.studylock

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.webkit.JavascriptInterface
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class StudyLockNativeBridge(
    private val activity: MainActivity,
    private val firebaseGateway: FirebaseGateway
) {
    private val appContext: Context = activity.applicationContext
    private var accessibilityPromptShown = false

    @JavascriptInterface
    fun isFirebaseConfigured(): Boolean = firebaseGateway.isConfigured

    @JavascriptInterface
    fun signUp(name: String, email: String, password: String) {
        firebaseGateway.signUp(name, email, password, ::emitAuthResult)
    }

    @JavascriptInterface
    fun signIn(email: String, password: String) {
        firebaseGateway.signIn(email, password, ::emitAuthResult)
    }

    @JavascriptInterface
    fun continueAsGuest() {
        if (!firebaseGateway.isConfigured) return
        firebaseGateway.signInAnonymously(::emitAuthResult)
    }

    @JavascriptInterface
    fun syncState(json: String) {
        firebaseGateway.syncState(json)
    }

    @JavascriptInterface
    fun onFocusState(
        active: Boolean,
        paused: Boolean,
        remainingSeconds: Int,
        blockedEntriesJson: String
    ) {
        val entries = runCatching {
            val array = JSONArray(blockedEntriesJson)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optString(index).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        }.getOrDefault(emptyList())

        val wasActive = FocusStateStore.isActive(appContext)
        FocusStateStore.update(
            context = appContext,
            active = active,
            paused = paused,
            remainingSeconds = remainingSeconds.coerceAtLeast(0),
            blockedPackages = BlockedAppResolver.resolve(entries)
        )

        if (
            active &&
            !wasActive &&
            !accessibilityPromptShown &&
            !isAccessibilityServiceEnabled()
        ) {
            accessibilityPromptShown = true
            activity.emitToast("Enable StudyLock app blocking on the next screen.")
            activity.openAccessibilitySettings()
        }
    }

    @JavascriptInterface
    fun onMusicState(playing: Boolean, trackName: String) {
        MusicStateStore.update(appContext, playing, trackName)
        if (playing) {
            activity.requestNotificationPermissionIfNeeded()
            val intent = Intent(appContext, MusicKeepAliveService::class.java)
                .setAction(MusicKeepAliveService.ACTION_START)
            ContextCompat.startForegroundService(appContext, intent)
        } else {
            appContext.stopService(Intent(appContext, MusicKeepAliveService::class.java))
        }
    }

    @JavascriptInterface
    fun startSpeech(target: String) {
        activity.runOnUiThread { activity.startVoiceInput(target) }
    }

    @JavascriptInterface
    fun openAccessibilitySettings() {
        activity.runOnUiThread { activity.openAccessibilitySettings() }
    }

    @JavascriptInterface
    fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val expected = "${appContext.packageName}/${AppBlockAccessibilityService::class.java.name}"
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    @JavascriptInterface
    fun getNativeState(): String = JSONObject().apply {
        put("firebaseConfigured", firebaseGateway.isConfigured)
        put("firebaseProject", BuildConfig.FIREBASE_PROJECT_ID)
        put("accessibilityEnabled", isAccessibilityServiceEnabled())
        put("focusActive", FocusStateStore.isActive(appContext))
        put("focusRemainingSeconds", FocusStateStore.remainingSeconds(appContext))
        put("musicPlaying", MusicStateStore.isPlaying(appContext))
        put("androidVersion", Build.VERSION.SDK_INT)
    }.toString()

    fun emitNativeStatus() {
        activity.runJavascript(
            "window.StudyLockNativeHooks?.onNativeState(" +
                "${JSONObject.quote(getNativeState())});"
        )
    }

    private fun emitAuthResult(result: FirebaseGateway.AuthResult) {
        activity.runJavascript(
            "window.StudyLockNativeHooks?.onAuthResult(" +
                "${result.success}," +
                "${JSONObject.quote(result.message)}," +
                "${JSONObject.quote(result.name.orEmpty())}," +
                "${JSONObject.quote(result.email.orEmpty())});"
        )
    }
}
