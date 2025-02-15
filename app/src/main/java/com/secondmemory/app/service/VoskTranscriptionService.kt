package com.secondmemory.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.secondmemory.app.MainActivity
import com.secondmemory.app.R
import com.secondmemory.app.utils.AudioConverter
import com.secondmemory.app.utils.AudioFileManager
import com.secondmemory.app.utils.FileUtils
import com.secondmemory.app.utils.PreferencesManager
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

    override fun onCreate() {
        super.onCreate()
        audioFileManager = AudioFileManager(this)
        preferencesManager = PreferencesManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("初始化语音识别服务..."))
        initializeVosk()
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

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun initializeVosk() {
        try {
            val modelDir = File(filesDir, "model")
            Log.d(TAG, "Starting Vosk initialization...")
            
            // 复制模型文件到内部存储
            Log.d(TAG, "Copying model files to: ${modelDir.absolutePath}")
            FileUtils.copyAssetToInternal(this, "vosk-model-cn", "model")
            
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
            throw e  // 重新抛出异常以便上层处理
        }
    }

    private fun queuePendingRecordings() {
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

    private fun processNextFile() {
        val file = transcriptionQueue.poll()
        if (file == null) {
            isProcessing = false
            stopSelf()
            return
        }

        isProcessing = true
        transcribeFile(file)
    }

    private fun transcribeFile(file: File) {
        var wavFile: File? = null
        try {
            updateNotification("正在识别: ${file.name}")
            if (!file.exists()) {
                val error = "Input file does not exist: ${file.absolutePath}"
                Log.e(TAG, error)
                throw IOException(error)
            }
            Log.d(TAG, "Starting transcription for file: ${file.name} (size: ${file.length()} bytes)")
            
            updateNotification("正在转换音频格式: ${file.name}")
            Log.d(TAG, "Converting audio file to WAV format...")
            wavFile = File(cacheDir, "${file.nameWithoutExtension}.wav")
            try {
                Log.d(TAG, "Starting audio conversion for file: ${file.absolutePath}")
                Log.d(TAG, "Input file size: ${file.length()} bytes")
                Log.d(TAG, "Input file format: ${file.extension}")
                
                AudioConverter.convertToWav(file, wavFile)
                
                if (!wavFile.exists()) {
                    val error = "WAV conversion failed - output file not created"
                    Log.e(TAG, error)
                    throw IOException(error)
                }
                
                if (wavFile.length() == 0L) {
                    val error = "WAV conversion failed - output file is empty"
                    Log.e(TAG, error)
                    throw IOException(error)
                }
                
                Log.d(TAG, "Audio conversion completed successfully")
                Log.d(TAG, "WAV file size: ${wavFile.length()} bytes")
                Log.d(TAG, "WAV file path: ${wavFile.absolutePath}")
            } catch (e: Exception) {
                val error = "Error converting audio to WAV: ${e.message}"
                Log.e(TAG, error, e)
                throw IOException(error, e)
            }
            
            // 验证识别器状态
            if (recognizer == null) {
                Log.e(TAG, "Recognizer is null - attempting to reinitialize Vosk")
                try {
                    initializeVosk()
                } catch (e: Exception) {
                    val error = "Failed to reinitialize Vosk: ${e.message}"
                    Log.e(TAG, error, e)
                    throw IllegalStateException(error, e)
                }
                
                if (recognizer == null) {
                    val error = "Failed to initialize recognizer after reinitialization attempt"
                    Log.e(TAG, error)
                    throw IllegalStateException(error)
                }
                
                Log.d(TAG, "Vosk reinitialization successful")
            }
            
            // 验证模型文件
            val modelDir = File(filesDir, "model")
            if (!modelDir.exists() || !modelDir.isDirectory) {
                val error = "Model directory not found or invalid: ${modelDir.absolutePath}"
                Log.e(TAG, error)
                throw IllegalStateException(error)
            }
            
            val modelFiles = modelDir.list()
            if (modelFiles.isNullOrEmpty()) {
                val error = "No model files found in directory: ${modelDir.absolutePath}"
                Log.e(TAG, error)
                throw IllegalStateException(error)
            }
            
            Log.d(TAG, "Model verification successful. Found ${modelFiles.size} files")
            
            // 读取WAV文件
            updateNotification("正在识别文件: ${file.name}")
            Log.d(TAG, "Starting recognition process...")
            val audioData = ByteArray(4096)
            var partialResult = StringBuilder()
            var totalBytesProcessed = 0
            var hasRecognizedSomething = false
            
            FileInputStream(wavFile!!).use { input ->
                var bytesRead: Int
                while (input.read(audioData).also { bytesRead = it } != -1) {
                    totalBytesProcessed += bytesRead
                    Log.d(TAG, "Processing audio chunk: $bytesRead bytes (Total: $totalBytesProcessed bytes)")
                    
                    val accepted = recognizer?.acceptWaveForm(audioData, bytesRead) ?: false
                    Log.d(TAG, "Chunk processed, accepted: $accepted")
                    if (accepted) {
                        val result = JSONObject(recognizer?.getResult() ?: "{}")
                        val text = result.optString("text", "")
                        Log.d(TAG, "Got recognition result: $text")
                        if (text.isNotEmpty()) {
                            hasRecognizedSomething = true
                            Log.d(TAG, "Adding to partial result: $text")
                            partialResult.append(text).append(" ")
                            // 发送部分识别结果
                            sendBroadcast(Intent(ACTION_TRANSCRIPTION_PARTIAL).apply {
                                putExtra(EXTRA_FILE_NAME, file.name)
                                putExtra(EXTRA_TRANSCRIPTION, partialResult.toString().trim())
                            })
                        }
                    } else {
                        val partial = JSONObject(recognizer?.getPartialResult() ?: "{}")
                        val text = partial.optString("partial", "")
                        Log.d(TAG, "Got partial result: $text")
                        if (text.isNotEmpty()) {
                            Log.d(TAG, "Broadcasting partial result")
                            // 发送部分识别结果
                            sendBroadcast(Intent(ACTION_TRANSCRIPTION_PARTIAL).apply {
                                putExtra(EXTRA_FILE_NAME, file.name)
                                putExtra(EXTRA_TRANSCRIPTION, text)
                            })
                        }
                    }
                }
                
                Log.d(TAG, "Recognition process completed")
                val finalResult = JSONObject(recognizer?.getFinalResult() ?: "{}")
                val finalText = finalResult.optString("text", "")
                Log.d(TAG, "Final recognition result: $finalText")
                
                if (!hasRecognizedSomething && finalText.isEmpty()) {
                    val error = "No speech recognized in the audio file"
                    Log.e(TAG, error)
                    throw IllegalStateException(error)
                }
                
                if (finalText.isNotEmpty()) {
                    Log.d(TAG, "Saving final transcription")
                    saveTranscription(file, finalText)
                } else if (partialResult.isNotEmpty()) {
                    // If no final result but we have partial results, use those
                    val text = partialResult.toString().trim()
                    Log.d(TAG, "Using accumulated partial results as final transcription")
                    saveTranscription(file, text)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error transcribing file: ${file.name}", e)
            // Notify UI about the error
            sendBroadcast(Intent(ACTION_TRANSCRIPTION_COMPLETED).apply {
                putExtra(EXTRA_FILE_NAME, file.name)
                putExtra(EXTRA_TRANSCRIPTION, "语音识别失败: ${e.message}")
            })
        } finally {
            try {
                // Clean up temporary WAV file
                if (wavFile?.exists() == true) {
                    Log.d(TAG, "Cleaning up temporary WAV file")
                    wavFile.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up WAV file", e)
            }
            processNextFile()
        }
    }

    private fun saveTranscription(file: File, text: String) {
        Log.d(TAG, "Saving transcription for file: ${file.name}")
        preferencesManager.saveTranscription(file.name, text)
        sendBroadcast(Intent(ACTION_TRANSCRIPTION_COMPLETED).apply {
            putExtra(EXTRA_FILE_NAME, file.name)
            putExtra(EXTRA_TRANSCRIPTION, text)
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
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
            ACTION_TRANSCRIBE_ALL -> queuePendingRecordings()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.close()
        mediaPlayer?.release()
        mediaPlayer = null
        stopForeground(true)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "VoskTranscriptionService"
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
