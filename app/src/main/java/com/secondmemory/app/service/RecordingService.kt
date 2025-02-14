package com.secondmemory.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.secondmemory.app.MainActivity
import com.secondmemory.app.R
import com.secondmemory.app.utils.AudioFileManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecordingService : Service() {
    private var mediaRecorder: MediaRecorder? = null
    private lateinit var audioFileManager: AudioFileManager
    private var currentRecordingFile: File? = null
    private var recordingStartTime: Long = 0

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "RecordingServiceChannel"
        const val ACTION_SAVE_RECORDING = "com.secondmemory.app.action.SAVE_RECORDING"
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        audioFileManager = AudioFileManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SAVE_RECORDING -> {
                saveCurrentRecording()
            }
            else -> {
                startForeground(NOTIFICATION_ID, createNotification())
                startRecording()
                isRunning = true
                sendBroadcast(Intent("com.secondmemory.app.RECORDING_STATE_CHANGED"))
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRecording() {
        try {
            currentRecordingFile = audioFileManager.createNewAudioFile()
            recordingStartTime = System.currentTimeMillis()
            
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000) // 16kHz采样率
                setAudioEncodingBitRate(32000) // 32kbps比特率
                setOutputFile(currentRecordingFile?.absolutePath)
                prepare()
                start()
            }

            // 启动定时器检查和管理录音文件
            startFileManagementTimer()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun saveCurrentRecording() {
        if (isRunning && currentRecordingFile != null) {
            stopRecording()
            startRecording()
        }
    }

    private fun startFileManagementTimer() {
        Timer().scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                // 每小时检查一次录音文件
                audioFileManager.manageRecordingFiles()
                
                // 如果当前录音超过24小时，自动保存并开始新录音
                if (System.currentTimeMillis() - recordingStartTime > 24 * 3600000) { // 24小时
                    saveCurrentRecording()
                }
            }
        }, 3600000, 3600000) // 每小时执行一次
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mediaRecorder = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.recording_active))
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        isRunning = false
        sendBroadcast(Intent("com.secondmemory.app.RECORDING_STATE_CHANGED"))
    }
}
