package com.cyberpulse.studylock

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class StudyLockWearMessageService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        if (messageEvent.path == WearSync.REQUEST_STATE_PATH) {
            StudyLockWidgetProvider.refreshAll(applicationContext)
            WearSync.pushFocusState(applicationContext)
        }
    }
}
