package com.cyberpulse.studylock

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

object WearSync {
    const val FOCUS_PATH = "/studylock/focus"
    const val REQUEST_STATE_PATH = "/studylock/request-state"

    fun pushFocusState(context: Context) {
        val appContext = context.applicationContext
        val active = FocusStateStore.isActive(appContext)
        val paused = FocusStateStore.isPaused(appContext)
        val remaining = FocusStateStore.remainingSeconds(appContext)

        val request = PutDataMapRequest.create(FOCUS_PATH).apply {
            dataMap.putBoolean("active", active)
            dataMap.putBoolean("paused", paused)
            dataMap.putInt("remainingSeconds", remaining)
            dataMap.putLong("sentAt", System.currentTimeMillis())
            dataMap.putString("source", "StudyLock")
        }.asPutDataRequest().setUrgent()

        runCatching {
            Wearable.getDataClient(appContext)
                .putDataItem(request)
                .addOnFailureListener { /* Phone works normally without a watch. */ }
        }
    }
}
