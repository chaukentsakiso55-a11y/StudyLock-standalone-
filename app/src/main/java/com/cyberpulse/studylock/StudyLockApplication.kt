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

    private var waitingForReturn = false
    private var currentStep: SetupStep? = null
    private var activeDialog: AlertDialog? = null
    private var dismissedThisProcess = false
    private var promptScheduled = false
    private var accessibilityAttempts = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is MainActivity) return

        if (isProtectionReady(activity)) {
            markSetupComplete(activity, true)
            activeDialog?.dismiss()
            activeDialog = null
            waitingForReturn = false
            currentStep = null
            return
        }

        markSetupComplete(activity, false)

        if (waitingForReturn) {
            waitingForReturn = false
            handler.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed) {
                    handleReturnFromApproval(activity)
                }
            }, RETURN_CHECK_DELAY_MS)
            return
        }

        if (
            !dismissedThisProcess &&
            !promptScheduled &&
            activeDialog?.isShowing != true
        ) {
            promptScheduled = true
            handler.postDelayed({
                promptScheduled = false
                if (!activity.isFinishing && !activity.isDestroyed) {
                    showNextPermission(activity)
                }
            }, FIRST_PROMPT_DELAY_MS)
        }
    }

    private fun handleReturnFromApproval(activity: MainActivity) {
        val step = currentStep ?: firstMissingStep(activity)
        if (step == null) {
            finishSetup(activity)
            return
        }

        if (isStepGranted(activity, step)) {
            if (step == SetupStep.ACCESSIBILITY) accessibilityAttempts = 0
            currentStep = null
            showNextPermission(activity)
            return
        }

        if (step == SetupStep.ACCESSIBILITY) {
            accessibilityAttempts += 1
            showAccessibilityHelp(activity)
        } else {
            showPermissionDialog(activity, step)
        }
    }

    private fun showNextPermission(activity: MainActivity) {
        val step = firstMissingStep(activity)
        if (step == null) {
            finishSetup(activity)
            return
        }
        showPermissionDialog(activity, step)
    }

    private fun showPermissionDialog(activity: MainActivity, step: SetupStep) {
        currentStep = step
        activeDialog?.dismiss()

        val title: String
        val message: String
        when (step) {
            SetupStep.BATTERY -> {
                title = "Allow unrestricted battery use"
                message =
                    "Allow StudyLock to keep focus sessions running reliably in the background. " +
                        "Android will show its own confirmation screen."
            }
            SetupStep.ACCESSIBILITY -> {
                title = "Allow app blocking"
                message =
                    "Allow StudyLock app blocking. Android will open Accessibility so you can enable " +
                        "‘StudyLock app blocking’."
            }
            SetupStep.ADMIN -> {
                title = "Allow device administrator"
                message =
                    "Allow StudyLock device administrator protection. Android will show the official " +
                        "activation screen before anything is enabled."
            }
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Allow") { _, _ ->
                launchApproval(activity, step)
            }
            .create()

        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener {
            dismissedThisProcess = true
            activeDialog = null
        }
        dialog.setOnDismissListener {
            if (activeDialog === dialog) activeDialog = null
        }

        activeDialog = dialog
        dialog.show()
    }

    private fun showAccessibilityHelp(activity: MainActivity) {
        currentStep = SetupStep.ACCESSIBILITY
        activeDialog?.dismiss()

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Allow app blocking")
            .setMessage(
                "StudyLock still does not have app blocking access. Tap Allow to open Accessibility again.\n\n" +
                    "If Android says this is a restricted setting because StudyLock was sideloaded, " +
                    "open App info, use the menu and choose ‘Allow restricted settings’, then return here."
            )
            .setPositiveButton("Allow") { _, _ ->
                launchApproval(activity, SetupStep.ACCESSIBILITY)
            }
            .setNeutralButton("Open App info") { _, _ ->
                waitingForReturn = true
                openAppInfo(activity)
            }
            .create()

        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnCancelListener {
            dismissedThisProcess = true
            activeDialog = null
        }
        dialog.setOnDismissListener {
            if (activeDialog === dialog) activeDialog = null
        }

        activeDialog = dialog
        dialog.show()
    }

    private fun launchApproval(activity: MainActivity, step: SetupStep) {
        currentStep = step
        waitingForReturn = true

        when (step) {
            SetupStep.BATTERY -> launchBatteryApproval(activity)
            SetupStep.ACCESSIBILITY -> launchAccessibilityApproval(activity)
            SetupStep.ADMIN -> DeviceProtectionController.requestAdminActivation(activity)
        }
    }

    private fun finishSetup(activity: MainActivity) {
        activeDialog?.dismiss()
        activeDialog = null
        waitingForReturn = false
        currentStep = null

        if (!isProtectionReady(activity)) {
            markSetupComplete(activity, false)
            showNextPermission(activity)
            return
        }

        markSetupComplete(activity, true)
        dismissedThisProcess = false

        if (isDeviceAdminActive(activity) && ParentPasswordStore.hasPassword(activity)) {
            DeviceProtectionController.enable(activity.applicationContext)
        }

        Toast.makeText(
            activity,
            "StudyLock protection setup complete ✓",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun launchBatteryApproval(activity: MainActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            waitingForReturn = false
            showNextPermission(activity)
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
            Toast.makeText(
                activity,
                "Android could not open battery access settings.",
                Toast.LENGTH_LONG
            ).show()
            showPermissionDialog(activity, SetupStep.BATTERY)
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
            showPermissionDialog(activity, SetupStep.ACCESSIBILITY)
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
        }.onFailure {
            waitingForReturn = false
        }
    }

    private fun firstMissingStep(context: Context): SetupStep? = when {
        !isBatteryUnrestricted(context) -> SetupStep.BATTERY
        !isAccessibilityEnabled(context) -> SetupStep.ACCESSIBILITY
        !isDeviceAdminActive(context) -> SetupStep.ADMIN
        else -> null
    }

    private fun isStepGranted(context: Context, step: SetupStep): Boolean = when (step) {
        SetupStep.BATTERY -> isBatteryUnrestricted(context)
        SetupStep.ACCESSIBILITY -> isAccessibilityEnabled(context)
        SetupStep.ADMIN -> isDeviceAdminActive(context)
    }

    private fun isProtectionReady(context: Context): Boolean =
        firstMissingStep(context) == null

    private fun markSetupComplete(context: Context, complete: Boolean) {
        context.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SETUP_COMPLETE, complete)
            .apply()
    }

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

    private enum class SetupStep {
        BATTERY,
        ACCESSIBILITY,
        ADMIN
    }

    companion object {
        private const val FIRST_PROMPT_DELAY_MS = 500L
        private const val RETURN_CHECK_DELAY_MS = 300L
        private const val SETUP_PREFS = "studylock_protection_setup"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
    }
}
