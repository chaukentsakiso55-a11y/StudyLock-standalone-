package com.cyberpulse.studylock.wear

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.wearable.Wearable

class WearMainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var timerView: TextView
    private lateinit var syncView: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            renderState()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(7, 17, 26))
        }

        val title = TextView(this).apply {
            text = "StudyLock"
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
        }

        statusView = TextView(this).apply {
            setTextColor(Color.rgb(142, 235, 255))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }

        timerView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 26f
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(8))
        }

        syncView = TextView(this).apply {
            text = "Synced from phone"
            setTextColor(Color.LTGRAY)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }

        val refresh = Button(this).apply {
            text = "Refresh from phone"
            isAllCaps = false
            setOnClickListener {
                syncView.text = "Requesting StudyLock state…"
                requestPhoneState()
            }
        }

        root.addView(title, fullWidthWrap())
        root.addView(statusView, fullWidthWrap())
        root.addView(timerView, fullWidthWrap())
        root.addView(syncView, fullWidthWrap())
        root.addView(refresh, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)
        renderState()
        requestPhoneState()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun renderState() {
        val snapshot = WearFocusStore.snapshot(this)
        statusView.text = when {
            snapshot.active && snapshot.paused -> "Focus paused"
            snapshot.active -> "Focus active"
            else -> "Ready to study"
        }
        timerView.text = if (snapshot.active) {
            val minutes = snapshot.remainingSeconds / 60
            val seconds = snapshot.remainingSeconds % 60
            String.format("%02d:%02d", minutes, seconds)
        } else {
            "--:--"
        }
    }

    private fun requestPhoneState() {
        Wearable.getNodeClient(this)
            .connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    syncView.text = "Phone not connected"
                    return@addOnSuccessListener
                }
                nodes.forEach { node ->
                    Wearable.getMessageClient(this)
                        .sendMessage(node.id, REQUEST_STATE_PATH, ByteArray(0))
                        .addOnSuccessListener { syncView.text = "StudyLock state requested" }
                        .addOnFailureListener { syncView.text = "Could not reach phone" }
                }
            }
            .addOnFailureListener {
                syncView.text = "Could not find phone"
            }
    }

    private fun fullWidthWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_STATE_PATH = "/studylock/request-state"
    }
}
