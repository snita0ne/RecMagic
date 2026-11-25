package com.magiccall.recorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.magiccall.recorder.data.AudioSourceMode
import com.magiccall.recorder.data.RecordingPrefs
import com.magiccall.recorder.data.RecordingRepository
import com.magiccall.recorder.ui.MainActivity

class CallRecorderService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var currentPfd: ParcelFileDescriptor? = null
    private var currentUri: Uri? = null
    private lateinit var repository: RecordingRepository
    private lateinit var prefs: RecordingPrefs

    override fun onCreate() {
        super.onCreate()
        repository = RecordingRepository(this)
        prefs = RecordingPrefs(this)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val number = intent.getStringExtra(EXTRA_PHONE_NUMBER)
                val direction = intent.getStringExtra(EXTRA_DIRECTION) ?: "CALL"
                startForeground(NOTIFICATION_ID, buildActiveNotification(number))
                startRecording(number, direction)
            }
            ACTION_STOP -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(phoneNumber: String?, direction: String) {
        if (mediaRecorder != null) return

        val pending = repository.createPendingRecording(phoneNumber, direction) ?: run {
            stopSelf()
            return
        }
        currentUri = pending.first

        val pfd = try {
            contentResolver.openFileDescriptor(pending.first, "w")
        } catch (_: Exception) {
            null
        }
        if (pfd == null) {
            repository.deleteRecording(pending.first)
            currentUri = null
            stopSelf()
            return
        }
        currentPfd = pfd

        val preferred = prefs.audioSourceMode.source
        val candidateSources = listOf(
            preferred,
            AudioSourceMode.VOICE_RECOGNITION.source,
            AudioSourceMode.VOICE_COMMUNICATION.source,
            AudioSourceMode.MIC.source
        ).distinct()

        var started = false
        for (source in candidateSources) {
            try {
                val recorder = createMediaRecorder().apply {
                    setAudioSource(source)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128_000)
                    setAudioSamplingRate(44_100)
                    setOutputFile(pfd.fileDescriptor)
                    prepare()
                    start()
                }
                mediaRecorder = recorder
                started = true
                break
            } catch (_: Exception) {
                mediaRecorder?.release()
                mediaRecorder = null
            }
        }

        if (!started) {
            cleanupFailedRecording()
            stopSelf()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
            mediaRecorder?.release()
        } finally {
            mediaRecorder = null
        }

        try {
            currentPfd?.close()
        } catch (_: Exception) {
        } finally {
            currentPfd = null
        }

        currentUri?.let { uri ->
            repository.finalizePendingRecording(uri)
            currentUri = null
        }
    }

    private fun cleanupFailedRecording() {
        try {
            currentPfd?.close()
        } catch (_: Exception) {
        } finally {
            currentPfd = null
        }
        currentUri?.let { repository.deleteRecording(it) }
        currentUri = null
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }
    }

    private fun createChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val activeChannel = NotificationChannel(
            CHANNEL_ID,
            "Запись разговора",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Уведомление во время активной записи звонка"
        }
        manager.createNotificationChannel(activeChannel)
    }

    private fun buildActiveNotification(phoneNumber: String?): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CallRecorderService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val subtitle = if (!phoneNumber.isNullOrBlank()) "Абонент: $phoneNumber" else "Идёт запись разговора"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Запись звонка")
            .setContentText(subtitle)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Остановить", stopIntent)
            .build()
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.magiccall.recorder.ACTION_START"
        const val ACTION_STOP = "com.magiccall.recorder.ACTION_STOP"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_DIRECTION = "extra_direction"

        private const val CHANNEL_ID = "call_recording_active"
        private const val NOTIFICATION_ID = 1001
    }
}
