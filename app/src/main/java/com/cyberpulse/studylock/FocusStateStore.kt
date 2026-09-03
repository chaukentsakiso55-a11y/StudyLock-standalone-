package com.cyberpulse.studylock

import android.content.Context

object FocusStateStore {
    private const val PREFERENCES = "studylock_native_focus"
    private const val ACTIVE = "active"
    private const val PAUSED = "paused"
    private const val REMAINING_SECONDS = "remaining_seconds"
    private const val END_EPOCH_MILLIS = "end_epoch_millis"
    private const val BLOCKED_PACKAGES = "blocked_packages"
    private const val BLOCKED_ENTRIES = "blocked_entries"

    fun update(
        context: Context,
        active: Boolean,
        paused: Boolean,
        remainingSeconds: Int,
        blockedPackages: Set<String>,
        blockedEntries: Set<String>
    ) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ACTIVE, active)
            .putBoolean(PAUSED, paused)
            .putInt(REMAINING_SECONDS, remainingSeconds)
            .putLong(
                END_EPOCH_MILLIS,
                if (active && !paused) {
                    System.currentTimeMillis() + remainingSeconds * 1_000L
                } else {
                    0L
                }
            )
            .putStringSet(BLOCKED_PACKAGES, blockedPackages)
            .putStringSet(BLOCKED_ENTRIES, blockedEntries)
            .apply()
    }

    fun isActive(context: Context): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(ACTIVE, false)) return false
        if (preferences.getBoolean(PAUSED, false)) return true
        val end = preferences.getLong(END_EPOCH_MILLIS, 0L)
        if (end > System.currentTimeMillis()) return true
        preferences.edit().putBoolean(ACTIVE, false).apply()
        return false
    }

    fun isPaused(context: Context): Boolean {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return preferences.getBoolean(ACTIVE, false) &&
            preferences.getBoolean(PAUSED, false)
    }

    fun remainingSeconds(context: Context): Int {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(ACTIVE, false)) return 0
        if (preferences.getBoolean(PAUSED, false)) {
            return preferences.getInt(REMAINING_SECONDS, 0)
        }
        return ((preferences.getLong(END_EPOCH_MILLIS, 0L) - System.currentTimeMillis()) /
            1_000L).coerceAtLeast(0L).toInt()
    }

    fun blockedPackages(context: Context): Set<String> =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getStringSet(BLOCKED_PACKAGES, emptySet())
            ?.toSet()
            .orEmpty()

    fun blockedEntries(context: Context): Set<String> =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getStringSet(BLOCKED_ENTRIES, emptySet())
            ?.toSet()
            .orEmpty()
}
