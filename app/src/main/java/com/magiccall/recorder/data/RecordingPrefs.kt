package com.magiccall.recorder.data

import android.content.Context
import android.media.MediaRecorder

enum class AudioSourceMode(val source: Int, val title: String, val subtitle: String) {
    VOICE_RECOGNITION(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        "Распознавание речи (рекомендуется)",
        "Лучше всего работает с AccessibilityService на MagicOS без root"
    ),
    VOICE_COMMUNICATION(
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        "Голосовая связь",
        "Подходит при использовании громкой связи или гарнитуры"
    ),
    MIC(
        MediaRecorder.AudioSource.MIC,
        "Основной микрофон",
        "Стандартный захват с микрофона телефона"
    )
}

class RecordingPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("magic_call_recorder_prefs", Context.MODE_PRIVATE)

    var autoRecordEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECORD, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RECORD, value).apply()

    var audioSourceMode: AudioSourceMode
        get() {
            val name = prefs.getString(KEY_AUDIO_SOURCE, AudioSourceMode.VOICE_RECOGNITION.name)
            return AudioSourceMode.entries.firstOrNull { it.name == name }
                ?: AudioSourceMode.VOICE_RECOGNITION
        }
        set(value) = prefs.edit().putString(KEY_AUDIO_SOURCE, value.name).apply()

    var excludedNumbers: Set<String>
        get() = prefs.getStringSet(KEY_EXCLUDED, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_EXCLUDED, value).apply()

    fun isNumberExcluded(rawNumber: String?): Boolean {
        if (rawNumber.isNullOrBlank()) return false
        val normalized = normalizePhone(rawNumber)
        return excludedNumbers.any { normalizePhone(it) == normalized }
    }

    fun addExcludedNumber(rawNumber: String) {
        val clean = rawNumber.trim()
        if (clean.isEmpty()) return
        excludedNumbers = excludedNumbers + clean
    }

    fun removeExcludedNumber(rawNumber: String) {
        excludedNumbers = excludedNumbers - rawNumber
    }

    companion object {
        private const val KEY_AUTO_RECORD = "auto_record_enabled"
        private const val KEY_AUDIO_SOURCE = "audio_source_mode"
        private const val KEY_EXCLUDED = "excluded_numbers"

        fun normalizePhone(phone: String): String {
            val digits = phone.filter { it.isDigit() }
            return if (digits.length >= 10) digits.takeLast(10) else digits
        }
    }
}
