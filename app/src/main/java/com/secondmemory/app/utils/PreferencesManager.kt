package com.secondmemory.app.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFERENCES_NAME = "SecondMemoryPrefs"
        private const val KEY_AUTO_START = "auto_start_enabled"
        private const val KEY_TRANSCRIPTION_PREFIX = "transcription_"
    }

    fun setAutoStartEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }

    fun getAutoStartEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTO_START, false)
    }

    fun saveTranscription(fileName: String, text: String?) {
        sharedPreferences.edit().putString(KEY_TRANSCRIPTION_PREFIX + fileName, text).apply()
    }

    fun getTranscription(fileName: String): String? {
        return sharedPreferences.getString(KEY_TRANSCRIPTION_PREFIX + fileName, null)
    }

    fun hasTranscription(fileName: String): Boolean {
        return sharedPreferences.contains(KEY_TRANSCRIPTION_PREFIX + fileName)
    }

    fun removeTranscription(fileName: String) {
        sharedPreferences.edit().remove(KEY_TRANSCRIPTION_PREFIX + fileName).apply()
    }
}
