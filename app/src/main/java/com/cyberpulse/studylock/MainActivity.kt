package com.cyberpulse.studylock

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
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
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.zip.GZIPInputStream

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    companion object {
        const val PREFS_NAME = "studylock_native"
        const val KEY_FOCUS_ACTIVE = "focus_active"
        const val KEY_BLOCKED_ENTRIES = "blocked_entries"
        private const val CHANNEL_ID = "studylock_alerts"
    }

    private lateinit var webView: WebView
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechRequestId: String? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs.edit().putBoolean(KEY_FOCUS_ACTIVE, false).apply()
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        createNotificationChannel()
        tts = TextToSpeech(this, this)
        requestCorePermissions()
        setupWebView()
        setupBackHandling()
        loadBundledStudyLock()
        webView.postDelayed({ maybePromptForAccessibility() }, 900)
    }

    @Suppress("SetJavaScriptEnabled", "DEPRECATION")
    private fun setupWebView() {
        webView = WebView(this)
        webView.setBackgroundColor(Color.WHITE)
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
        }
        webView.addJavascriptInterface(StudyLockBridge(), "StudyLockAndroid")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                syncNativeStateToPage()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val allowed = request.resources.filter { resource ->
                        when (resource) {
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> hasPermission(Manifest.permission.CAMERA)
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> hasPermission(Manifest.permission.RECORD_AUDIO)
                            else -> false
                        }
                    }.toTypedArray()
                    if (allowed.isNotEmpty()) request.grant(allowed) else request.deny()
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return try {
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = "audio/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    fileChooserLauncher.launch(intent)
                    true
                } catch (_: Exception) {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = null
                    false
                }
            }
        }
        setContentView(webView)
    }

    private fun loadBundledStudyLock() {
        val encoded = buildString {
            for (name in arrayOf("html_00.b64", "html_01.b64", "html_02.b64", "html_03.b64", "html_04.b64", "html_05.b64")) {
                append(assets.open(name).bufferedReader().use { it.readText() })
            }
        }
        val compressed = Base64.decode(encoded, Base64.DEFAULT)
        val html = GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        webView.loadDataWithBaseURL("https://studylock.local/", html, "text/html", "UTF-8", null)
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (prefs.getBoolean(KEY_FOCUS_ACTIVE, false)) {
                    webView.evaluateJavascript("try{showToast('Focus session is active 🔒')}catch(e){}", null)
                    return
                }
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun requestCorePermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        val missing = permissions.filterNot(::hasPermission)
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun maybePromptForAccessibility() {
        if (isAccessibilityEnabled() || isFinishing) return
        AlertDialog.Builder(this)
            .setTitle("Enable StudyLock app blocking")
            .setMessage("StudyLock needs its Accessibility Service to detect when a blocked app is opened during a Focus session. You can enable it now and return to StudyLock.")
            .setPositiveButton("Enable") { _, _ -> openAccessibilitySettings() }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, StudyLockBlockerService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun setFocusNative(active: Boolean) {
        prefs.edit().putBoolean(KEY_FOCUS_ACTIVE, active).apply()
        runOnUiThread {
            try {
                if (active) startLockTask() else stopLockTask()
            } catch (_: Exception) { }
        }
    }

    private fun syncNativeStateToPage() {
        val enabled = isAccessibilityEnabled()
        webView.evaluateJavascript(
            "window.__studylockNativeAccessibilityEnabled=${if (enabled) "true" else "false"};",
            null
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "StudyLock alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Study sessions, tasks, levels and badges"
                }
            )
        }
    }

    private fun postNativeNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= 33 && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) return
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val id = (System.currentTimeMillis() and 0x7fffffff).toInt()
        getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    private fun startNativeSpeech(requestId: String) {
        runOnUiThread {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                sendSpeechResult(requestId, "", true, "Speech recognition isn't available on this device")
                return@runOnUiThread
            }
            if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                requestCorePermissions()
                sendSpeechResult(requestId, "", true, "Microphone permission is required")
                return@runOnUiThread
            }
            speechRecognizer?.destroy()
            speechRequestId = requestId
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    override fun onPartialResults(partialResults: Bundle?) {
                        val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        sendSpeechResult(requestId, text, false, "")
                    }
                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        sendSpeechResult(requestId, text, true, "")
                    }
                    override fun onError(error: Int) {
                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "I couldn't catch that. Try again."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Try again."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network error"
                            SpeechRecognizer.ERROR_CLIENT -> "cancelled"
                            else -> "Speech recognition stopped"
                        }
                        sendSpeechResult(requestId, "", true, message)
                    }
                })
                recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                })
            }
        }
    }

    private fun sendSpeechResult(requestId: String, text: String, isFinal: Boolean, error: String) {
        runOnUiThread {
            val js = "window.__studylockNativeSpeechResult(" +
                JSONObject.quote(requestId) + "," +
                JSONObject.quote(text) + "," +
                (if (isFinal) "true" else "false") + "," +
                JSONObject.quote(error) + ");"
            webView.evaluateJavascript(js, null)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts?.language = Locale.getDefault()
        }
    }

    override fun onDestroy() {
        prefs.edit().putBoolean(KEY_FOCUS_ACTIVE, false).apply()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        webView.removeJavascriptInterface("StudyLockAndroid")
        webView.destroy()
        super.onDestroy()
    }

    inner class StudyLockBridge {
        @JavascriptInterface
        fun setFocusActive(active: Boolean) = setFocusNative(active)

        @JavascriptInterface
        fun setBlockList(json: String) {
            val entries = LinkedHashSet<String>()
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    array.optString(i).trim().takeIf { it.isNotEmpty() }?.let(entries::add)
                }
            } catch (_: Exception) { }
            prefs.edit().putStringSet(KEY_BLOCKED_ENTRIES, entries).apply()
        }

        @JavascriptInterface
        fun openAccessibilitySettings() = runOnUiThread { this@MainActivity.openAccessibilitySettings() }

        @JavascriptInterface
        fun isAccessibilityEnabled(): Boolean = this@MainActivity.isAccessibilityEnabled()

        @JavascriptInterface
        fun postNotification(title: String, body: String) = runOnUiThread { postNativeNotification(title, body) }

        @JavascriptInterface
        fun requestNotificationPermission() = runOnUiThread { requestCorePermissions() }

        @JavascriptInterface
        fun startSpeechRecognition(requestId: String) = startNativeSpeech(requestId)

        @JavascriptInterface
        fun cancelSpeechRecognition() = runOnUiThread { speechRecognizer?.cancel() }

        @JavascriptInterface
        fun speak(text: String) = runOnUiThread {
            if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "studylock_tutor")
        }

        @JavascriptInterface
        fun stopSpeaking() = runOnUiThread { tts?.stop() }
    }
}
