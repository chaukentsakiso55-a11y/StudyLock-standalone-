package com.cyberpulse.studylock

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class MainActivity : ComponentActivity(), RecognitionListener {
    private lateinit var webView: WebView
    private lateinit var nativeBridge: StudyLockNativeBridge
    private lateinit var firebaseGateway: FirebaseGateway
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var pendingSpeechTarget: String? = null
    private var bridgeAttached = false

    private val fileChooser = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { arrayOf(it) }
        } else {
            null
        }
        fileChooserCallback?.onReceiveValue(uris)
        fileChooserCallback = null
    }

    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val target = pendingSpeechTarget
        pendingSpeechTarget = null
        if (granted && target != null) beginSpeechRecognition(target)
        else emitSpeechError("Microphone permission is required for voice input.")
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.rgb(7, 10, 18)

        firebaseGateway = FirebaseGateway(applicationContext)
        nativeBridge = StudyLockNativeBridge(this, firebaseGateway)
        webView = WebView(this)

        webView.setBackgroundColor(Color.rgb(7, 10, 18))
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler(
                "/assets/",
                WebViewAssetLoader.AssetsPathHandler(this)
            )
            .build()

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
        }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webView.addJavascriptInterface(nativeBridge, "StudyLockNative")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                if (uri.host == "appassets.androidplatform.net") return false
                return runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                }.getOrDefault(false)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                attachNativeBridge()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                val requestedType = fileChooserParams
                    ?.acceptTypes
                    ?.firstOrNull { it.isNotBlank() }
                    ?: "audio/*"
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = if (requestedType == "*/*") "audio/*" else requestedType
                }
                fileChooser.launch(intent)
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                val requestsAudio = request.resources.contains(
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE
                )
                if (
                    requestsAudio &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                } else {
                    request.deny()
                }
            }
        }

        setContentView(webView)
        webView.loadUrl(
            "https://appassets.androidplatform.net/assets/studylock-exact.html"
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (FocusStateStore.isActive(this@MainActivity)) {
                    emitToast("Focus session is active. End it inside StudyLock first.")
                } else if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finishAfterTransition()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        DeviceProtectionController.applyDesiredPolicy(applicationContext)
        if (bridgeAttached) nativeBridge.emitNativeStatus()
    }

    private fun attachNativeBridge() {
        val bridgeScript = assets.open("native-bridge.js")
            .bufferedReader()
            .use { it.readText() }
        val enhancementsScript = assets.open("studylock-enhancements.js")
            .bufferedReader()
            .use { it.readText() }
        webView.evaluateJavascript("$bridgeScript\n$enhancementsScript") {
            bridgeAttached = true
            nativeBridge.emitNativeStatus()
        }
    }

    fun runJavascript(script: String) {
        runOnUiThread {
            if (::webView.isInitialized) webView.evaluateJavascript(script, null)
        }
    }

    fun emitToast(message: String) {
        runJavascript(
            "window.StudyLockNativeHooks?.showToast(${JSONObject.quote(message)});"
        )
    }

    fun openAccessibilitySettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }.onFailure {
            emitToast("Open Android Settings and enable StudyLock app blocking.")
        }
    }

    fun openAppPicker(existingEntriesJson: String) {
        data class PickableApp(val label: String, val packageName: String)

        val existing = runCatching {
            val array = JSONArray(existingEntriesJson)
            buildSet {
                for (index in 0 until array.length()) {
                    val value = array.optString(index).trim().lowercase(Locale.ROOT)
                    if (value.isNotEmpty()) add(value)
                }
            }
        }.getOrDefault(emptySet())

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        }

        val apps = activities.mapNotNull { info ->
            val packageName = info.activityInfo?.packageName
                ?.takeIf { it.isNotBlank() && it != this@MainActivity.packageName }
                ?: return@mapNotNull null
            val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                .ifBlank { packageName }
            PickableApp(label, packageName)
        }.distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        if (apps.isEmpty()) {
            emitToast("No launchable apps were found on this device.")
            return
        }

        val labels = apps.map { it.label }.toTypedArray()
        val checked = BooleanArray(apps.size) { index ->
            val app = apps[index]
            val label = app.label.lowercase(Locale.ROOT)
            val packageName = app.packageName.lowercase(Locale.ROOT)
            existing.any { entry ->
                entry == label ||
                    entry == packageName ||
                    entry.contains(packageName) ||
                    entry.contains(label)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Select apps to block")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Add selected") { _, _ ->
                val selected = JSONArray()
                apps.forEachIndexed { index, app ->
                    if (checked[index]) {
                        selected.put(
                            JSONObject()
                                .put("name", app.label)
                                .put("packageName", app.packageName)
                        )
                    }
                }
                runJavascript(
                    "window.StudyLockNativeHooks?.onAppsPicked(" +
                        "${JSONObject.quote(selected.toString())});"
                )
            }
            .show()
    }

    fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun startVoiceInput(target: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            emitSpeechError("Voice recognition is not available on this device.")
            return
        }
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingSpeechTarget = target
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        beginSpeechRecognition(target)
    }

    private fun beginSpeechRecognition(target: String) {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(this)
        }
        pendingSpeechTarget = target
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
        runJavascript(
            "window.StudyLockNativeHooks?.onSpeechListening(${JSONObject.quote(target)});"
        )
    }

    private fun emitSpeechResult(text: String) {
        val target = pendingSpeechTarget ?: "chat"
        runJavascript(
            "window.StudyLockNativeHooks?.onSpeechResult(" +
                "${JSONObject.quote(target)},${JSONObject.quote(text)});"
        )
        pendingSpeechTarget = null
    }

    private fun emitSpeechError(message: String) {
        runJavascript(
            "window.StudyLockNativeHooks?.onSpeechError(${JSONObject.quote(message)});"
        )
        pendingSpeechTarget = null
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit

    override fun onError(error: Int) {
        emitSpeechError("Voice input could not be completed. Please try again.")
    }

    override fun onResults(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
        if (text.isNullOrBlank()) emitSpeechError("No speech was detected.")
        else emitSpeechResult(text)
    }

    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    override fun onDestroy() {
        speechRecognizer?.destroy()
        if (::nativeBridge.isInitialized) nativeBridge.close()
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("StudyLockNative")
            webView.destroy()
        }
        super.onDestroy()
    }
}
