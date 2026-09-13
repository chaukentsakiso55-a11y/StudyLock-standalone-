package com.cyberpulse.studylock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class StudyLockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            refreshAll(context)
            WearSync.pushFocusState(context)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.studylock.student.action.WIDGET_REFRESH"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, StudyLockWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { id ->
                updateWidget(context, manager, id)
            }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val active = FocusStateStore.isActive(context)
            val paused = FocusStateStore.isPaused(context)
            val remaining = FocusStateStore.remainingSeconds(context)
            val minutes = remaining / 60
            val seconds = remaining % 60

            val status = when {
                active && paused -> "Focus paused"
                active -> "Focus active"
                else -> "Ready to study"
            }
            val timer = if (active) {
                String.format("%02d:%02d remaining", minutes, seconds)
            } else {
                "Open StudyLock to begin"
            }

            val views = RemoteViews(context.packageName, R.layout.studylock_widget).apply {
                setTextViewText(R.id.widget_status, status)
                setTextViewText(R.id.widget_timer, timer)
                setViewVisibility(R.id.widget_focus_dot, if (active) View.VISIBLE else View.INVISIBLE)

                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("studylock_widget", true)
                }
                val openPendingIntent = PendingIntent.getActivity(
                    context,
                    1001,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
                setOnClickPendingIntent(R.id.widget_open, openPendingIntent)

                val refreshIntent = Intent(context, StudyLockWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    context,
                    1002,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent)
            }

            manager.updateAppWidget(widgetId, views)
        }
    }
}
