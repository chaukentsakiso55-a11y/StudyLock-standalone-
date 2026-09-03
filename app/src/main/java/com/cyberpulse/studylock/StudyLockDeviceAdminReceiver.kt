package com.cyberpulse.studylock

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class StudyLockDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        DeviceProtectionController.applyDesiredPolicy(context)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "StudyLock uninstall protection is enabled. Disable it only if you intend to remove StudyLock."
}
