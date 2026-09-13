package com.cyberpulse.studylock.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class StudyLockWearListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (
                event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == FOCUS_PATH
            ) {
                val data = DataMapItem.fromDataItem(event.dataItem).dataMap
                WearFocusStore.update(
                    applicationContext,
                    active = data.getBoolean("active", false),
                    paused = data.getBoolean("paused", false),
                    remainingSeconds = data.getInt("remainingSeconds", 0),
                    sentAt = data.getLong("sentAt", System.currentTimeMillis())
                )
            }
        }
    }

    companion object {
        const val FOCUS_PATH = "/studylock/focus"
    }
}
