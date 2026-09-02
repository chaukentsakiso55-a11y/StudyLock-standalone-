package com.cyberpulse.studylock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import java.util.Locale

class StudyLockBlockerService : AccessibilityService() {
    private val prefs by lazy { getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE) }
    private var lastBlockedPackage: String? = null
    private var lastBlockedAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !prefs.getBoolean(MainActivity.KEY_FOCUS_ACTIVE, false)) return
        val packageName = event.packageName?.toString()?.trim().orEmpty()
        if (packageName.isEmpty() || packageName == applicationContext.packageName || packageName == "com.android.systemui") return
        val entries = prefs.getStringSet(MainActivity.KEY_BLOCKED_ENTRIES, emptySet()) ?: emptySet()
        if (entries.isEmpty() || !matchesBlockedApp(packageName, entries)) return

        val now = SystemClock.elapsedRealtime()
        if (lastBlockedPackage == packageName && now - lastBlockedAt < 900) return
        lastBlockedPackage = packageName
        lastBlockedAt = now

        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("blocked_package", packageName)
        })
        Toast.makeText(this, "Blocked during your StudyLock focus session 🔒", Toast.LENGTH_SHORT).show()
    }

    private fun matchesBlockedApp(packageName: String, entries: Set<String>): Boolean {
        val lowerPackage = packageName.lowercase(Locale.ROOT)
        val label = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString().lowercase(Locale.ROOT)
        } catch (_: Exception) {
            ""
        }
        val normalizedLabel = label.replace(Regex("[^a-z0-9]+"), "")

        for (entry in entries) {
            val raw = entry.lowercase(Locale.ROOT).trim()
            if (raw.isEmpty()) continue
            if (lowerPackage == raw || label == raw) return true

            if ((raw.contains("twitter.com") || raw.contains("x.com") || raw == "x") &&
                (lowerPackage == "com.twitter.android" || label == "x" || label.contains("twitter"))) return true

            val tokens = Regex("[a-z0-9]+")
                .findAll(raw.replace("www.", ""))
                .map { it.value }
                .filter { it.length >= 3 && it !in setOf("com", "www", "http", "https") }
                .toList()

            for (token in tokens) {
                val normalizedToken = token.replace(Regex("[^a-z0-9]+"), "")
                if (normalizedToken.isEmpty()) continue
                if (lowerPackage.contains(normalizedToken) || normalizedLabel.contains(normalizedToken)) return true
            }
        }
        return false
    }

    override fun onInterrupt() = Unit
}
