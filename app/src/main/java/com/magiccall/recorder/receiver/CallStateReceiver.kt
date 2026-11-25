package com.magiccall.recorder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.magiccall.recorder.data.RecordingPrefs
import com.magiccall.recorder.service.CallRecorderService

class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        @Suppress("DEPRECATION")
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        val prefs = RecordingPrefs(context)
        if (!prefs.autoRecordEnabled) return

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                wasRinging = true
                if (!incomingNumber.isNullOrBlank()) {
                    lastNumber = incomingNumber
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (!incomingNumber.isNullOrBlank()) {
                    lastNumber = incomingNumber
                }
                if (prefs.isNumberExcluded(lastNumber)) return

                val direction = if (wasRinging) "IN" else "OUT"
                val serviceIntent = Intent(context, CallRecorderService::class.java).apply {
                    action = CallRecorderService.ACTION_START
                    putExtra(CallRecorderService.EXTRA_PHONE_NUMBER, lastNumber)
                    putExtra(CallRecorderService.EXTRA_DIRECTION, direction)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                wasRinging = false
                lastNumber = null
                val stopIntent = Intent(context, CallRecorderService::class.java).apply {
                    action = CallRecorderService.ACTION_STOP
                }
                context.startService(stopIntent)
            }
        }
    }

    companion object {
        @Volatile
        private var wasRinging = false

        @Volatile
        private var lastNumber: String? = null
    }
}
