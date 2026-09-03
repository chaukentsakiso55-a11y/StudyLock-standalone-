package com.cyberpulse.studylock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class AppBlockAccessibilityService : AccessibilityService() {
    private var lastRedirectAt = 0L
    private var lastRedirectedPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        if (!FocusStateStore.isActive(this)) return
        if (packageName !in FocusStateStore.blockedPackages(this)) return

        val now = SystemClock.elapsedRealtime()
        if (packageName == lastRedirectedPackage && now - lastRedirectAt < 900L) return
        lastRedirectedPackage = packageName
        lastRedirectAt = now

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra("blocked_package", packageName)
        }
        startActivity(intent)
    }

    override fun onInterrupt() = Unit
}
