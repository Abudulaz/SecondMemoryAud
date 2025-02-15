package com.secondmemory.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.secondmemory.app.service.RecordingService
import com.secondmemory.app.service.TranscriptionService
import com.secondmemory.app.utils.PreferencesManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val preferencesManager = PreferencesManager(context)
            if (preferencesManager.getAutoStartEnabled()) {
                // 启动录音服务
                val recordingIntent = Intent(context, RecordingService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, recordingIntent)
                } else {
                    context.startService(recordingIntent)
                }

                // 启动语音识别服务
                val transcriptionIntent = Intent(context, TranscriptionService::class.java).apply {
                    action = TranscriptionService.ACTION_TRANSCRIBE_ALL
                }
                context.startService(transcriptionIntent)
            }
        }
    }
}
