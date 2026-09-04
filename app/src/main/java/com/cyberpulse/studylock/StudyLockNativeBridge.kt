package com.cyberpulse.studylock

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.webkit.JavascriptInterface
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONArray
import org.json.JSONObject

class StudyLockNativeBridge(
    private val activity: MainActivity,
    private val firebaseGateway: FirebaseGateway
) {
    private val appContext: Context = activity.applicationContext
    private val aiTutorGateway = AiTutorGateway(firebaseGateway.firebaseApp)
    private val geminiAuthTutorGateway = GeminiAuthTutorGateway()
    private val offlineDictionaryGateway = OfflineDictionaryGateway(appContext)
    private var accessibilityPromptShown = false
    private var cachedBlockedEntries: Set<String> = emptySet()
    private var cachedBlockedPackages: Set<String> = emptySet()

    @JavascriptInterface
    fun isFirebaseConfigured(): Boolean = firebaseGateway.isConfigured

    @JavascriptInterface
    fun enterImmersiveFullscreen() {
        activity.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activity.window.statusBarColor = Color.TRANSPARENT
            activity.window.navigationBarColor = Color.TRANSPARENT

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val attributes = activity.window.attributes
                attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                activity.window.attributes = attributes
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activity.window.isNavigationBarContrastEnforced = false
                activity.window.isStatusBarContrastEnforced = false
            }

            WindowInsetsControllerCompat(
                activity.window,
                activity.window.decorView
            ).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

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
    fun hasParentPassword(): Boolean = ParentPasswordStore.hasPassword(appContext)

    @JavascriptInterface
    fun saveParentPassword(password: String): Boolean =
        ParentPasswordStore.save(appContext, password)

    @JavascriptInterface
    fun verifyParentPassword(password: String): Boolean =
        ParentPasswordStore.verify(appContext, password)

    @JavascriptInterface
    fun requestDeviceAdminAccess() {
        activity.runOnUiThread {
            DeviceProtectionController.requestAdminActivation(activity)
        }
    }

    @JavascriptInterface
    fun enableUninstallProtection(): String =
        DeviceProtectionController.enable(appContext).toJson(appContext)

    @JavascriptInterface
    fun disableUninstallProtection(password: String): String =
        DeviceProtectionController.disable(appContext, password).toJson(appContext)

    @JavascriptInterface
    fun getProtectionState(): String =
        DeviceProtectionController.status(appContext).toString()

    @JavascriptInterface
    fun getBlockedListPolicyState(): String =
        BlockedListPolicyStore.state(appContext).toString()

    @JavascriptInterface
    fun canChangeBlockedList(): Boolean =
        BlockedListPolicyStore.canEdit(appContext)

    @JavascriptInterface
    fun recordBlockedListChange(): String =
        BlockedListPolicyStore.recordChange(appContext).toString()

    @JavascriptInterface
    fun authorizeBlockedListOverride(password: String): String =
        BlockedListPolicyStore.authorizeParentOverride(appContext, password).toString()

    @JavascriptInterface
    fun openAppPicker(existingEntriesJson: String) {
        activity.runOnUiThread {
            activity.openAppPicker(existingEntriesJson)
        }
    }

    @JavascriptInterface
    fun requestTutor(requestId: String, payload: String) {
        val request = runCatching { JSONObject(payload) }.getOrElse { JSONObject() }
        val apiKey = request.optString("apiKey").trim()
        val managedPayload = JSONObject(request.toString())
            .put("preferPersonal", false)
            .toString()

        firebaseGateway.ensureTutorIdentity { authResult ->
            if (!authResult.success) {
                if (apiKey.startsWith("AQ.")) {
                    geminiAuthTutorGateway.request(payload) { fallback ->
                        emitTutorResult(requestId, fallback)
                    }
                } else {
                    emitTutorResult(
                        requestId,
                        AiTutorGateway.Result(
                            success = false,
                            message = authResult.message
                        )
                    )
                }
                return@ensureTutorIdentity
            }

            aiTutorGateway.request(managedPayload) { managedResult ->
                if (!managedResult.success && apiKey.startsWith("AQ.")) {
                    geminiAuthTutorGateway.request(payload) { fallback ->
                        emitTutorResult(
                            requestId,
                            if (fallback.success) fallback else managedResult
                        )
                    }
                } else {
                    emitTutorResult(requestId, managedResult)
                }
            }
        }
    }

    @JavascriptInterface
    fun lookupOfflineDictionary(requestId: String, word: String) {
        offlineDictionaryGateway.lookup(word) { result ->
            activity.runJavascript(
                "window.StudyLockNativeHooks?.onDictionaryResult(" +
                    "${JSONObject.quote(requestId)}," +
                    "${result.success}," +
                    "${JSONObject.quote(result.payload)}," +
                    "${JSONObject.quote(result.message)});"
            )
        }
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

        val entrySet = entries.toSet()
        val resolvedPackages = if (entrySet == cachedBlockedEntries) {
            cachedBlockedPackages
        } else {
            BlockedAppResolver.resolve(appContext, entries).also { resolved ->
                cachedBlockedEntries = entrySet
                cachedBlockedPackages = resolved
            }
        }

        val wasActive = FocusStateStore.isActive(appContext)
        FocusStateStore.update(
            context = appContext,
            active = active,
            paused = paused,
            remainingSeconds = remainingSeconds.coerceAtLeast(0),
            blockedPackages = resolvedPackages,
            blockedEntries = entrySet
        )

        if (active && !wasActive) {
            DeviceProtectionController.applyDesiredPolicy(appContext)
        }

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
        val manager = appContext.getSystemService(AccessibilityManager::class.java)
        val enabledByManager = manager
            ?.getEnabledAccessibilityServiceList(
                android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            ?.any { service ->
                val info = service.resolveInfo.serviceInfo
                info.packageName == appContext.packageName &&
                    info.name == AppBlockAccessibilityService::class.java.name
            } == true
        if (enabledByManager) return true

        val enabled = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val expected = "${appContext.packageName}/${AppBlockAccessibilityService::class.java.name}"
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    @JavascriptInterface
    fun getNativeState(): String {
        val protection = DeviceProtectionController.status(appContext)
        return JSONObject().apply {
            put("firebaseConfigured", firebaseGateway.isConfigured)
            put("firebaseAuthenticated", firebaseGateway.isAuthenticated)
            put("managedAiConnected", firebaseGateway.isConfigured && firebaseGateway.isAuthenticated)
            put("firebaseProject", BuildConfig.FIREBASE_PROJECT_ID)
            put("accessibilityEnabled", isAccessibilityServiceEnabled())
            put("focusActive", FocusStateStore.isActive(appContext))
            put("focusPaused", FocusStateStore.isPaused(appContext))
            put("focusRemainingSeconds", FocusStateStore.remainingSeconds(appContext))
            put("musicPlaying", MusicStateStore.isPlaying(appContext))
            put("deviceAdminActive", protection.optBoolean("adminActive"))
            put("deviceOwner", protection.optBoolean("deviceOwner"))
            put("profileOwner", protection.optBoolean("profileOwner"))
            put("uninstallBlocked", protection.optBoolean("uninstallBlocked"))
            put("uninstallProtectionDesired", protection.optBoolean("protectionDesired"))
            put("uninstallProtectionLevel", protection.optString("level", "off"))
            put("blockedListPolicy", BlockedListPolicyStore.state(appContext))
            put("offlineDictionary", true)
            put("androidVersion", Build.VERSION.SDK_INT)
        }.toString()
    }

    fun emitNativeStatus() {
        enterImmersiveFullscreen()
        activity.runJavascript(
            "window.StudyLockNativeHooks?.onNativeState(" +
                "${JSONObject.quote(getNativeState())});"
        )
    }

    fun close() {
        aiTutorGateway.close()
        geminiAuthTutorGateway.close()
        offlineDictionaryGateway.close()
    }

    private fun emitAuthResult(result: FirebaseGateway.AuthResult) {
        activity.runJavascript(
            "window.StudyLockNativeHooks?.onAuthResult(" +
                "${result.success}," +
                "${JSONObject.quote(result.message)}," +
                "${JSONObject.quote(result.name.orEmpty())}," +
                "${JSONObject.quote(result.email.orEmpty())});"
        )
        if (result.success) emitNativeStatus()
    }

    private fun emitTutorResult(requestId: String, result: AiTutorGateway.Result) {
        activity.runJavascript(
            "window.StudyLockNativeHooks?.onTutorResult(" +
                "${JSONObject.quote(requestId)}," +
                "${result.success}," +
                "${JSONObject.quote(result.text)}," +
                "${JSONObject.quote(result.message)});"
        )
    }
}
