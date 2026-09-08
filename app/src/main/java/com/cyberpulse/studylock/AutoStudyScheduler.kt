package com.cyberpulse.studylock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

object AutoStudyScheduler {
    private const val PREFS = "studylock_auto_study_v3"
    private const val ENABLED = "enabled"
    private const val START_MINUTE = "start_minute"
    private const val END_MINUTE = "end_minute"
    private const val DEFAULT_MINUTES = "default_minutes"

    const val ACTION_START = "com.cyberpulse.studylock.AUTO_STUDY_START"
    const val ACTION_END = "com.cyberpulse.studylock.AUTO_STUDY_END"
    const val ACTION_RESCHEDULE = "com.cyberpulse.studylock.AUTO_STUDY_RESCHEDULE"

    data class Settings(
        val enabled: Boolean,
        val startMinute: Int,
        val endMinute: Int,
        val defaultMinutes: Int
    )

    fun settings(context: Context): Settings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Settings(
            enabled = prefs.getBoolean(ENABLED, false),
            startMinute = prefs.getInt(START_MINUTE, 18 * 60).coerceIn(0, 1439),
            endMinute = prefs.getInt(END_MINUTE, 20 * 60).coerceIn(0, 1439),
            defaultMinutes = prefs.getInt(DEFAULT_MINUTES, 60).coerceIn(25, 300)
        )
    }

    fun configure(
        context: Context,
        enabled: Boolean,
        startMinute: Int,
        endMinute: Int,
        defaultMinutes: Int
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED, enabled)
            .putInt(START_MINUTE, startMinute.coerceIn(0, 1439))
            .putInt(END_MINUTE, endMinute.coerceIn(0, 1439))
            .putInt(DEFAULT_MINUTES, defaultMinutes.coerceIn(25, 300))
            .apply()
        if (enabled) {
            activateIfInsideWindow(context)
            scheduleNext(context)
        } else {
            cancel(context)
        }
    }

    fun scheduleNext(context: Context) {
        val settings = settings(context)
        if (!settings.enabled) {
            cancel(context)
            return
        }
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        setAlarm(manager, context, ACTION_START, REQUEST_START, nextOccurrence(settings.startMinute))
        setAlarm(manager, context, ACTION_END, REQUEST_END, nextOccurrence(settings.endMinute))
    }

    fun activateIfInsideWindow(context: Context) {
        val settings = settings(context)
        if (!settings.enabled || !insideWindow(settings)) return
        startScheduledSession(context)
    }

    fun startScheduledSession(context: Context) {
        val settings = settings(context)
        if (!settings.enabled) return
        val remainingToEnd = minutesUntil(settings.endMinute).coerceAtLeast(1)
        val entries = FocusStateStore.blockedEntries(context)
        val packages = if (FocusStateStore.blockedPackages(context).isNotEmpty()) {
            FocusStateStore.blockedPackages(context)
        } else {
            BlockedAppResolver.resolve(context, entries.toList())
        }
        FocusStateStore.update(
            context = context,
            active = true,
            paused = false,
            remainingSeconds = remainingToEnd * 60,
            blockedPackages = packages,
            blockedEntries = entries
        )
        scheduleNext(context)
    }

    fun endScheduledSession(context: Context) {
        val entries = FocusStateStore.blockedEntries(context)
        val packages = FocusStateStore.blockedPackages(context)
        FocusStateStore.update(
            context = context,
            active = false,
            paused = false,
            remainingSeconds = 0,
            blockedPackages = packages,
            blockedEntries = entries
        )
        scheduleNext(context)
    }

    private fun insideWindow(settings: Settings): Boolean {
        val now = Calendar.getInstance()
        val current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        if (settings.startMinute == settings.endMinute) return true
        return if (settings.startMinute < settings.endMinute) {
            current >= settings.startMinute && current < settings.endMinute
        } else {
            current >= settings.startMinute || current < settings.endMinute
        }
    }

    private fun setAlarm(
        manager: AlarmManager,
        context: Context,
        action: String,
        requestCode: Int,
        triggerAtMillis: Long
    ) {
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AutoStudyReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
    }

    private fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        listOf(ACTION_START to REQUEST_START, ACTION_END to REQUEST_END).forEach { (action, code) ->
            val pending = PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, AutoStudyReceiver::class.java).setAction(action),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pending != null) manager.cancel(pending)
        }
    }

    private fun nextOccurrence(minuteOfDay: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    private fun minutesUntil(minuteOfDay: Int): Int {
        val now = Calendar.getInstance()
        val current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        var difference = minuteOfDay - current
        if (difference <= 0) difference += 1440
        return difference
    }

    private const val REQUEST_START = 4011
    private const val REQUEST_END = 4012
}

class AutoStudyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            AutoStudyScheduler.ACTION_START -> AutoStudyScheduler.startScheduledSession(context.applicationContext)
            AutoStudyScheduler.ACTION_END -> AutoStudyScheduler.endScheduledSession(context.applicationContext)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AutoStudyScheduler.ACTION_RESCHEDULE -> {
                AutoStudyScheduler.activateIfInsideWindow(context.applicationContext)
                AutoStudyScheduler.scheduleNext(context.applicationContext)
            }
        }
    }
}
