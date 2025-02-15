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
    }

    fun setAutoStartEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }

    fun getAutoStartEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTO_START, false)
    }
}
