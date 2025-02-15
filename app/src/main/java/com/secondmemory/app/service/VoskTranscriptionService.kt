package com.secondmemory.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.secondmemory.app.MainActivity
import com.secondmemory.app.R
import com.secondmemory.app.utils.AudioConverter
import com.secondmemory.app.utils.AudioFileManager
import com.secondmemory.app.utils.FileUtils
import com.secondmemory.app.utils.PreferencesManager
import kotlinx.coroutines.*
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import org.json.JSONObject

class VoskTranscriptionService : Service() {
    private var recognizer: Recognizer? = null
    private lateinit var audioFileManager: AudioFileManager
    private lateinit var preferencesManager: PreferencesManager
    private val transcriptionQueue = ConcurrentLinkedQueue<File>()
    private var isProcessing = false
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + 
        CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Coroutine error", throwable)
        }
    )
    private var currentJob: Job? = null
    private val autoTranscribeRunnable = object : Runnable {
        override fun run() {
            checkNewRecordings()
            handler.postDelayed(this, AUTO_CHECK_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioFileManager = AudioFileManager(this)
        preferencesManager = PreferencesManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("初始化语音识别服务..."))
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                initializeVosk()
                startAutoTranscribe()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing service", e)
                stopSelf()
            }
        }
    }

    private fun startAutoTranscribe() {
        handler.post(autoTranscribeRunnable)
    }

    private fun checkNewRecordings() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val recordings = audioFileManager.getRecordingsList()
                var hasNewRecordings = false
                
                recordings.forEach { file ->
                    if (!preferencesManager.hasTranscription(file.name)) {
                        transcriptionQueue.offer(file)
                        hasNewRecordings = true
                    }
                }
                
                if (hasNewRecordings && !isProcessing) {
                    processNextFile()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking new recordings", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "语音识别服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于显示语音识别进度"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("语音识别")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_transcribe)
            .setContentIntent(pendingIntent)
            .build()
    }

    private suspend fun updateNotification(text: String) = withContext(Dispatchers.Main) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private suspend fun initializeVosk() = withContext(Dispatchers.IO) {
        try {
            val modelDir = File(filesDir, "model")
            Log.d(TAG, "Starting Vosk initialization...")
            
            // 复制模型文件到内部存储
            Log.d(TAG, "Copying model files to: ${modelDir.absolutePath}")
            FileUtils.copyAssetToInternal(this@VoskTranscriptionService, "vosk-model-cn", "model")
            
            // 验证模型文件
            if (!modelDir.exists() || !modelDir.isDirectory) {
                throw IOException("Model directory not found or not a directory")
            }
            
            val modelFiles = modelDir.list()
            Log.d(TAG, "Model files copied: ${modelFiles?.joinToString(", ")}")
            
            if (modelFiles.isNullOrEmpty()) {
                throw IOException("No model files found in directory")
            }
            
            // 初始化模型
            Log.d(TAG, "Initializing Vosk model...")
            val model = Model(modelDir.absolutePath)
            Log.d(TAG, "Model initialized successfully")
            
            recognizer = Recognizer(model, 16000f)
            Log.d(TAG, "Recognizer created successfully")
            
            Log.d(TAG, "Vosk initialization completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Vosk", e)
            throw e
        }
    }

    private fun processNextFile() {
        val file = transcriptionQueue.poll()
        if (file == null) {
            isProcessing = false
            stopSelf()
            return
        }

        isProcessing = true
        currentJob = serviceScope.launch(Dispatchers.IO) {
            try {
                transcribeFile(file)
            } catch (e: Exception) {
                Log.e(TAG, "Error transcribing file", e)
                withContext(Dispatchers.Main) {
                    LocalBroadcastManager.getInstance(this@VoskTranscriptionService)
                        .sendBroadcast(Intent(ACTION_TRANSCRIPTION_COMPLETED).apply {
                            putExtra(EXTRA_FILE_NAME, file.name)
                            putExtra(EXTRA_TRANSCRIPTION, "语音识别失败: ${e.message}")
                        })
                }
            } finally {
                delay(1000) // 添加延迟以避免过快处理下一个文件
                processNextFile()
            }
        }
    }

    private suspend fun transcribeFile(file: File) = withContext(Dispatchers.IO) {
        var wavFile: File? = null
        try {
            updateNotification("正在识别: ${file.name}")
            if (!file.exists()) {
                throw IOException("Input file does not exist: ${file.absolutePath}")
            }
            
            updateNotification("正在转换音频格式: ${file.name}")
            wavFile = File(cacheDir, "${file.nameWithoutExtension}.wav")
            AudioConverter.convertToWav(file, wavFile)
            
            if (!wavFile.exists() || wavFile.length() == 0L) {
                throw IOException("WAV conversion failed")
            }
            
            // 验证识别器状态
            if (recognizer == null) {
                initializeVosk()
            }
            
            // 读取WAV文件
            updateNotification("正在识别文件: ${file.name}")
            val audioData = ByteArray(4096)
            var partialResult = StringBuilder()
            var hasRecognizedSomething = false
            
            FileInputStream(wavFile).use { input ->
                var bytesRead: Int
                while (input.read(audioData).also { bytesRead = it } != -1) {
                    ensureActive() // 检查协程是否被取消
                    
                    val accepted = recognizer?.acceptWaveForm(audioData, bytesRead) ?: false
                    if (accepted) {
                        val result = JSONObject(recognizer?.getResult() ?: "{}")
                        val text = result.optString("text", "")
                        if (text.isNotEmpty()) {
                            hasRecognizedSomething = true
                            partialResult.append(text).append(" ")
                            withContext(Dispatchers.Main) {
                                LocalBroadcastManager.getInstance(this@VoskTranscriptionService)
                                    .sendBroadcast(Intent(ACTION_TRANSCRIPTION_PARTIAL).apply {
                                        putExtra(EXTRA_FILE_NAME, file.name)
                                        putExtra(EXTRA_TRANSCRIPTION, partialResult.toString().trim())
                                    })
                            }
                        }
                    }
                }
                
                val finalResult = JSONObject(recognizer?.getFinalResult() ?: "{}")
                val finalText = finalResult.optString("text", "")
                
                if (!hasRecognizedSomething && finalText.isEmpty()) {
                    throw IllegalStateException("No speech recognized in the audio file")
                }
                
                val text = if (finalText.isNotEmpty()) finalText else partialResult.toString().trim()
                saveTranscription(file, text)
            }
        } finally {
            wavFile?.delete()
        }
    }

    private suspend fun saveTranscription(file: File, text: String) = withContext(Dispatchers.Default) {
        val processedText = processTranscriptionText(text)
        preferencesManager.saveTranscription(file.name, processedText)
        withContext(Dispatchers.Main) {
            LocalBroadcastManager.getInstance(this@VoskTranscriptionService)
                .sendBroadcast(Intent(ACTION_TRANSCRIPTION_COMPLETED).apply {
                    putExtra(EXTRA_FILE_NAME, file.name)
                    putExtra(EXTRA_TRANSCRIPTION, processedText)
                })
        }
    }

    private fun processTranscriptionText(text: String): String {
        val words = text.split(" ")
        val deduplicatedText = words.filterIndexed { index, word -> 
            index == 0 || word != words[index - 1]
        }.joinToString(" ")
        
        val sentences = deduplicatedText.split(Regex("[。？！]"))
            .filter { it.isNotBlank() }
            .map { it.trim() }
        
        return sentences.joinToString("。\n") { 
            if (it.endsWith("？") || it.endsWith("！")) it else "$it。"
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_TRANSCRIPTION -> {
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
                if (fileName != null) {
                    audioFileManager.getRecordingFile(fileName)?.let { file ->
                        transcriptionQueue.offer(file)
                        if (!isProcessing) {
                            processNextFile()
                        }
                    }
                }
            }
            ACTION_TRANSCRIBE_ALL -> {
                serviceScope.launch(Dispatchers.IO) {
                    val recordings = audioFileManager.getRecordingsList()
                    recordings.forEach { file ->
                        if (!preferencesManager.hasTranscription(file.name)) {
                            transcriptionQueue.offer(file)
                        }
                    }
                    if (!isProcessing) {
                        processNextFile()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoTranscribeRunnable)
        currentJob?.cancel()
        serviceScope.cancel()
        recognizer?.close()
        mediaPlayer?.release()
        mediaPlayer = null
        stopForeground(true)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        private const val TAG = "VoskTranscriptionService"
        private const val AUTO_CHECK_INTERVAL = 60000L // 每分钟检查一次新录音
        private const val CHANNEL_ID = "transcription_service"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START_TRANSCRIPTION = "com.secondmemory.app.START_TRANSCRIPTION"
        const val ACTION_TRANSCRIBE_ALL = "com.secondmemory.app.TRANSCRIBE_ALL"
        const val ACTION_TRANSCRIPTION_COMPLETED = "com.secondmemory.app.TRANSCRIPTION_COMPLETED"
        const val ACTION_TRANSCRIPTION_PARTIAL = "com.secondmemory.app.TRANSCRIPTION_PARTIAL"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_TRANSCRIPTION = "transcription"
    }
}
