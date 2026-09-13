package com.cyberpulse.studylock.wear

import android.content.Context

object WearFocusStore {
    private const val PREFS = "studylock_wear_focus"
    private const val ACTIVE = "active"
    private const val PAUSED = "paused"
    private const val REMAINING = "remaining_seconds"
    private const val SENT_AT = "sent_at"

    data class Snapshot(
        val active: Boolean,
        val paused: Boolean,
        val remainingSeconds: Int
    )

    fun update(
        context: Context,
        active: Boolean,
        paused: Boolean,
        remainingSeconds: Int,
        sentAt: Long
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ACTIVE, active)
            .putBoolean(PAUSED, paused)
            .putInt(REMAINING, remainingSeconds.coerceAtLeast(0))
            .putLong(SENT_AT, sentAt)
            .apply()
    }

    fun snapshot(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val active = prefs.getBoolean(ACTIVE, false)
        val paused = prefs.getBoolean(PAUSED, false)
        val stored = prefs.getInt(REMAINING, 0).coerceAtLeast(0)
        val sentAt = prefs.getLong(SENT_AT, 0L)
        val elapsed = if (active && !paused && sentAt > 0L) {
            ((System.currentTimeMillis() - sentAt) / 1_000L).coerceAtLeast(0L).toInt()
        } else {
            0
        }
        val remaining = (stored - elapsed).coerceAtLeast(0)
        return Snapshot(active && remaining > 0 || active && paused, paused, remaining)
    }
}
