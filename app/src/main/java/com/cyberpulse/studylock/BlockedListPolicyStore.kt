package com.cyberpulse.studylock

import android.content.Context
import org.json.JSONObject

object BlockedListPolicyStore {
    private const val PREFS = "studylock_blocked_list_policy_v1"
    private const val KEY_LAST_CHANGE_AT = "last_change_at"
    private const val KEY_EDIT_WINDOW_UNTIL = "edit_window_until"
    private const val KEY_PARENT_OVERRIDE_UNTIL = "parent_override_until"

    private const val WEEK_MS = 7L * 24L * 60L * 60L * 1000L
    private const val EDIT_WINDOW_MS = 10L * 60L * 1000L
    private const val PARENT_OVERRIDE_MS = 10L * 60L * 1000L

    fun canEdit(context: Context): Boolean =
        state(context).optBoolean("canEdit", true)

    fun state(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastChangeAt = prefs.getLong(KEY_LAST_CHANGE_AT, 0L)
        val editWindowUntil = prefs.getLong(KEY_EDIT_WINDOW_UNTIL, 0L)
        val parentOverrideUntil = prefs.getLong(KEY_PARENT_OVERRIDE_UNTIL, 0L)
        val nextStudentEditAt = if (lastChangeAt > 0L) lastChangeAt + WEEK_MS else 0L

        val initialConfiguration = lastChangeAt <= 0L
        val weeklyWindowAvailable = lastChangeAt > 0L && now >= nextStudentEditAt
        val editWindowActive = editWindowUntil > now
        val parentOverrideActive = parentOverrideUntil > now
        val canEdit = initialConfiguration || weeklyWindowAvailable || editWindowActive || parentOverrideActive
        val remainingMs = if (!canEdit && nextStudentEditAt > now) {
            nextStudentEditAt - now
        } else {
            0L
        }

        return JSONObject().apply {
            put("canEdit", canEdit)
            put("locked", !canEdit)
            put("initialConfiguration", initialConfiguration)
            put("weeklyWindowAvailable", weeklyWindowAvailable)
            put("editWindowActive", editWindowActive)
            put("parentOverrideActive", parentOverrideActive)
            put("lastChangeAt", lastChangeAt)
            put("nextStudentEditAt", nextStudentEditAt)
            put("editWindowUntil", editWindowUntil)
            put("parentOverrideUntil", parentOverrideUntil)
            put("remainingMs", remainingMs)
            put("editWindowRemainingMs", (editWindowUntil - now).coerceAtLeast(0L))
            put("parentOverrideRemainingMs", (parentOverrideUntil - now).coerceAtLeast(0L))
            put("cooldownDays", 7)
            put("editWindowMinutes", EDIT_WINDOW_MS / 60_000L)
        }
    }

    fun recordChange(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastChangeAt = prefs.getLong(KEY_LAST_CHANGE_AT, 0L)
        val nextStudentEditAt = if (lastChangeAt > 0L) lastChangeAt + WEEK_MS else 0L
        val editWindowUntil = prefs.getLong(KEY_EDIT_WINDOW_UNTIL, 0L)
        val parentOverrideUntil = prefs.getLong(KEY_PARENT_OVERRIDE_UNTIL, 0L)

        when {
            parentOverrideUntil > now -> {
                if (lastChangeAt <= 0L) {
                    prefs.edit()
                        .putLong(KEY_LAST_CHANGE_AT, now)
                        .putLong(KEY_EDIT_WINDOW_UNTIL, now + EDIT_WINDOW_MS)
                        .apply()
                }
            }
            lastChangeAt <= 0L || now >= nextStudentEditAt -> {
                prefs.edit()
                    .putLong(KEY_LAST_CHANGE_AT, now)
                    .putLong(KEY_EDIT_WINDOW_UNTIL, now + EDIT_WINDOW_MS)
                    .apply()
            }
            editWindowUntil > now -> Unit
            else -> return state(context)
        }

        return state(context)
    }

    fun authorizeParentOverride(context: Context, password: String): JSONObject {
        if (!ParentPasswordStore.verify(context, password)) {
            return JSONObject().apply {
                put("success", false)
                put("message", "Incorrect parent password.")
                put("state", state(context))
            }
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_PARENT_OVERRIDE_UNTIL, System.currentTimeMillis() + PARENT_OVERRIDE_MS)
            .apply()

        return JSONObject().apply {
            put("success", true)
            put("message", "Parent override enabled for 10 minutes.")
            put("state", state(context))
        }
    }
}
