package com.cyberpulse.studylock

import android.app.Activity
import android.os.Bundle

class AutoStudyConfigActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        val enabled = uri?.getQueryParameter("enabled") == "1"
        val start = uri?.getQueryParameter("start")?.toIntOrNull()?.coerceIn(0, 1439) ?: 18 * 60
        val end = uri?.getQueryParameter("end")?.toIntOrNull()?.coerceIn(0, 1439) ?: 20 * 60
        val minutes = uri?.getQueryParameter("minutes")?.toIntOrNull()?.coerceIn(25, 300) ?: 60
        AutoStudyScheduler.configure(applicationContext, enabled, start, end, minutes)
        finish()
    }
}
