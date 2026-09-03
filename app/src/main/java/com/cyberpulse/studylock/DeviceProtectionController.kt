package com.cyberpulse.studylock

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.json.JSONObject

object DeviceProtectionController {
    private const val PREFERENCES = "studylock_device_protection"
    private const val PROTECTION_DESIRED = "protection_desired"

    data class Result(val success: Boolean, val message: String) {
        fun toJson(context: Context): String = JSONObject().apply {
            put("success", success)
            put("message", message)
            put("state", status(context))
        }.toString()
    }

    fun requestAdminActivation(activity: Activity) {
        val manager = manager(activity)
        if (manager.isAdminActive(admin(activity))) return
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(activity))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "StudyLock uses device administrator access to add an uninstall barrier. Full uninstall blocking is only available when Android has provisioned StudyLock as a managed device owner or profile owner."
            )
        }
        activity.startActivity(intent)
    }

    fun enable(context: Context): Result {
        if (!ParentPasswordStore.hasPassword(context)) {
            return Result(false, "Set a StudyLock parent password before enabling uninstall protection.")
        }

        val manager = manager(context)
        val component = admin(context)
        if (!manager.isAdminActive(component)) {
            return Result(false, "Enable StudyLock device administrator access first.")
        }

        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PROTECTION_DESIRED, true)
            .commit()

        return if (isManagedOwner(context)) {
            runCatching {
                manager.setUninstallBlocked(component, context.packageName, true)
            }.fold(
                onSuccess = {
                    Result(true, "Full uninstall protection is enabled by Android device policy.")
                },
                onFailure = { error ->
                    Result(false, error.localizedMessage ?: "Android could not enable the uninstall block.")
                }
            )
        } else {
            Result(
                true,
                "Device-admin uninstall protection is enabled. Full system-level blocking requires StudyLock to be provisioned as a managed device owner or profile owner."
            )
        }
    }

    fun disable(context: Context, password: String): Result {
        if (!ParentPasswordStore.verify(context, password)) {
            return Result(false, "The parent password is incorrect.")
        }

        val manager = manager(context)
        val component = admin(context)
        val managedOwner = isManagedOwner(context)

        if (managedOwner) {
            val cleared = runCatching {
                manager.setUninstallBlocked(component, context.packageName, false)
            }
            if (cleared.isFailure) {
                return Result(
                    false,
                    cleared.exceptionOrNull()?.localizedMessage
                        ?: "Android could not release the uninstall block."
                )
            }
        } else if (manager.isAdminActive(component)) {
            @Suppress("DEPRECATION")
            manager.removeActiveAdmin(component)
        }

        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PROTECTION_DESIRED, false)
            .commit()

        return Result(true, "StudyLock uninstall protection has been released.")
    }

    fun applyDesiredPolicy(context: Context) {
        if (!isProtectionDesired(context) || !isManagedOwner(context)) return
        runCatching {
            manager(context).setUninstallBlocked(admin(context), context.packageName, true)
        }
    }

    fun isProtectionDesired(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(PROTECTION_DESIRED, false)

    fun status(context: Context): JSONObject {
        val manager = manager(context)
        val component = admin(context)
        val adminActive = manager.isAdminActive(component)
        val deviceOwner = manager.isDeviceOwnerApp(context.packageName)
        val profileOwner = manager.isProfileOwnerApp(context.packageName)
        val managedOwner = deviceOwner || profileOwner
        val blocked = if (managedOwner) {
            runCatching {
                manager.isUninstallBlocked(component, context.packageName)
            }.getOrDefault(false)
        } else {
            false
        }

        val level = when {
            blocked -> "full"
            adminActive -> "admin"
            else -> "off"
        }

        return JSONObject().apply {
            put("adminActive", adminActive)
            put("deviceOwner", deviceOwner)
            put("profileOwner", profileOwner)
            put("managedOwner", managedOwner)
            put("uninstallBlocked", blocked)
            put("protectionDesired", isProtectionDesired(context))
            put("level", level)
        }
    }

    private fun isManagedOwner(context: Context): Boolean {
        val manager = manager(context)
        return manager.isDeviceOwnerApp(context.packageName) ||
            manager.isProfileOwnerApp(context.packageName)
    }

    private fun manager(context: Context): DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)

    private fun admin(context: Context): ComponentName =
        ComponentName(context, StudyLockDeviceAdminReceiver::class.java)
}
