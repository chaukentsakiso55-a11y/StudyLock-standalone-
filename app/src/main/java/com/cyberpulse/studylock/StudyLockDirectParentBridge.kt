package com.cyberpulse.studylock

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import org.json.JSONObject

class StudyLockDirectParentBridge(
    private val activity: MainActivity
) : StudentDirectParentClient.Listener {
    private val appContext = activity.applicationContext
    private val client = StudentDirectParentClient(appContext, this)

    init {
        Handler(Looper.getMainLooper()).postDelayed({ client.reconnectSaved() }, 1_200L)
    }

    @JavascriptInterface
    fun pair(code: String) {
        client.pair(code)
    }

    @JavascriptInterface
    fun disconnect() {
        client.disconnect(clearSaved = true)
    }

    @JavascriptInterface
    fun sendState(json: String): Boolean = client.sendState(json)

    @JavascriptInterface
    fun getState(): String = client.stateJson()

    @JavascriptInterface
    fun drainCommands(): String = ParentCommandStore.drain(appContext).toString()

    override fun onPairingResult(success: Boolean, message: String) {
        activity.runJavascript(
            "window.StudyLockDirectParentHooks?.onPairingResult(" +
                "$success,${JSONObject.quote(message)});"
        )
    }

    override fun onCommand(command: JSONObject) {
        activity.runJavascript("window.StudyLockDirectParentHooks?.onCommandAvailable?.();")
    }

    override fun onConnectionChanged(connected: Boolean, message: String) {
        activity.runJavascript(
            "window.StudyLockDirectParentHooks?.onConnectionChanged(" +
                "$connected,${JSONObject.quote(message)});"
        )
    }

    fun close() {
        client.close()
    }
}
