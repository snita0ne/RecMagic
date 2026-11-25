package com.magiccall.recorder.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class CallRecordAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Экранный контент не читается (canRetrieveWindowContent=false).
        // Сервис используется исключительно для разрешения фоновой записи звонков.
    }

    override fun onInterrupt() {
        isConnected = false
    }

    override fun onDestroy() {
        isConnected = false
        super.onDestroy()
    }

    companion object {
        @Volatile
        var isConnected: Boolean = false
            private set
    }
}
