package com.secondmemory.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import com.secondmemory.app.service.RecordingService

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val PREFS_NAME = "SecondMemoryPrefs"
        private const val KEY_AUTO_START = "auto_start_on_boot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean(KEY_AUTO_START, false)

            if (autoStart) {
                // 启动录音服务
                val serviceIntent = Intent(context, RecordingService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
