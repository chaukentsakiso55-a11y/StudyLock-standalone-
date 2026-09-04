package com.cyberpulse.studylock

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast

class StudyLockApplication : Application(), Application.ActivityLifecycleCallbacks {
    private val handler = Handler(Looper.getMainLooper())

    private var promptShownThisProcess = false
    private var setupRunning = false
    private var waitingForReturn = false
    private var batteryAttempted = false
    private var accessibilityAttempted = false
    private var adminAttempted = false

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is MainActivity) return

        if (setupRunning && waitingForReturn) {
            waitingForReturn = false
            handler.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed) {
                    advanceSetup(activity)
                }
            }, RETURN_CHECK_DELAY_MS)
            return
        }

        if (!promptShownThisProcess && !isProtectionReady(activity)) {
            promptShownThisProcess = true
            handler.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed && !setupRunning) {
                    showSetupOffer(activity)
                }
            }, FIRST_PROMPT_DELAY_MS)
        }
    }

    private fun showSetupOffer(activity: MainActivity) {
        val missing = missingProtections(activity)
        if (missing.isEmpty()) return

        AlertDialog.Builder(activity)
            .setTitle("Set up StudyLock protection")
            .setMessage(
                "StudyLock can guide you through the Android approvals it needs for reliable focus sessions. " +
                    "You will only need to approve the system screens that Android shows.\n\n" +
                    "Setup covers:\n• Unrestricted battery access\n• App blocking (Accessibility)\n• Device administrator protection\n\n" +
                    "Android does not allow StudyLock to approve these permissions for itself."
            )
            .setNegativeButton("Not now", null)
            .setPositiveButton("Start setup") { _, _ ->
                startSetup(activity)
            }
            .show()
    }

    fun startSetup(activity: MainActivity) {
        if (setupRunning) return
        setupRunning = true
        waitingForReturn = false
        batteryAttempted = false
        accessibilityAttempted = false
        adminAttempted = false
        advanceSetup(activity)
    }

    private fun advanceSetup(activity: MainActivity) {
        if (!isBatteryUnrestricted(activity) && !batteryAttempted) {
            batteryAttempted = true
            waitingForReturn = true
            launchBatteryApproval(activity)
            return
        }

        if (!isAccessibilityEnabled(activity) && !accessibilityAttempted) {
            accessibilityAttempted = true
            waitingForReturn = true
            launchAccessibilityApproval(activity)
            return
        }

        if (!isDeviceAdminActive(activity) && !adminAttempted) {
            adminAttempted = true
            waitingForReturn = true
            DeviceProtectionController.requestAdminActivation(activity)
            return
        }

        finishSetup(activity)
    }

    private fun finishSetup(activity: MainActivity) {
        setupRunning = false
        waitingForReturn = false

        if (isDeviceAdminActive(activity) && ParentPasswordStore.hasPassword(activity)) {
            DeviceProtectionController.enable(activity.applicationContext)
        }

        val missing = missingProtections(activity)
        if (missing.isEmpty()) {
            Toast.makeText(
                activity,
                "StudyLock protection setup is complete.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val accessibilityMissing = !isAccessibilityEnabled(activity)
        val message = buildString {
            append("Android still needs approval for: ")
            append(missing.joinToString(", "))
            append(".\n\n")
            if (accessibilityMissing) {
                append(
                    "If Android says that StudyLock is a restricted setting, Android requires one manual step: " +
                        "open StudyLock App info, use the menu, choose ‘Allow restricted settings’, then run setup again."
                )
            } else {
                append("You can run the setup again to finish any permission you skipped.")
            }
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Protection setup needs one more approval")
            .setMessage(message)
            .setNegativeButton("Done for now", null)
            .setPositiveButton("Try again") { _, _ ->
                startSetup(activity)
            }

        if (accessibilityMissing) {
            dialog.setNeutralButton("Open App info") { _, _ ->
                openAppInfo(activity)
            }
        }

        dialog.show()
    }

    private fun launchBatteryApproval(activity: MainActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            waitingForReturn = false
            advanceSetup(activity)
            return
        }

        val directRequest = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${activity.packageName}")
        )
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

        runCatching {
            if (directRequest.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(directRequest)
            } else {
                activity.startActivity(fallback)
            }
        }.onFailure {
            waitingForReturn = false
            advanceSetup(activity)
        }
    }

    private fun launchAccessibilityApproval(activity: MainActivity) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        runCatching {
            activity.startActivity(intent)
        }.onFailure {
            waitingForReturn = false
            Toast.makeText(
                activity,
                "Android could not open Accessibility settings.",
                Toast.LENGTH_LONG
            ).show()
            advanceSetup(activity)
        }
    }

    private fun openAppInfo(activity: Activity) {
        runCatching {
            activity.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        }
    }

    private fun missingProtections(context: Context): List<String> = buildList {
        if (!isBatteryUnrestricted(context)) add("battery access")
        if (!isAccessibilityEnabled(context)) add("app blocking")
        if (!isDeviceAdminActive(context)) add("device administrator")
    }

    private fun isProtectionReady(context: Context): Boolean =
        missingProtections(context).isEmpty()

    private fun isBatteryUnrestricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val manager = context.getSystemService(PowerManager::class.java)
        return manager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    private fun isDeviceAdminActive(context: Context): Boolean {
        val manager = context.getSystemService(DevicePolicyManager::class.java)
        val component = ComponentName(context, StudyLockDeviceAdminReceiver::class.java)
        return manager?.isAdminActive(component) == true
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val enabledByManager = manager
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?.any { service ->
                val info = service.resolveInfo.serviceInfo
                info.packageName == context.packageName &&
                    info.name == AppBlockAccessibilityService::class.java.name
            } == true
        if (enabledByManager) return true

        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val expected = "${context.packageName}/${AppBlockAccessibilityService::class.java.name}"
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    companion object {
        private const val FIRST_PROMPT_DELAY_MS = 700L
        private const val RETURN_CHECK_DELAY_MS = 350L
    }
}
