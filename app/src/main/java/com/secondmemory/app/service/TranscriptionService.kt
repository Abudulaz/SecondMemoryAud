package com.secondmemory.app.service

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.AudioManager
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.secondmemory.app.utils.AudioFileManager
import com.secondmemory.app.utils.PreferencesManager
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

class TranscriptionService : Service() {
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var audioFileManager: AudioFileManager
    private lateinit var preferencesManager: PreferencesManager
    private val transcriptionQueue = ConcurrentLinkedQueue<File>()
    private var isProcessing = false
    private var mediaRecorder: MediaRecorder? = null

    override fun onCreate() {
        super.onCreate()
        audioFileManager = AudioFileManager(this)
        preferencesManager = PreferencesManager(this)
        setupSpeechRecognizer()
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "onReadyForSpeech")
                }
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "onBeginningOfSpeech")
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {
                    Log.d(TAG, "onBufferReceived")
                }
                override fun onEndOfSpeech() {
                    Log.d(TAG, "onEndOfSpeech")
                }
                
                override fun onError(error: Int) {
                    Log.e(TAG, "Speech recognition error: $error")
                    when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> Log.e(TAG, "Audio recording error")
                        SpeechRecognizer.ERROR_CLIENT -> Log.e(TAG, "Client side error")
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Log.e(TAG, "Insufficient permissions")
                        SpeechRecognizer.ERROR_NETWORK -> Log.e(TAG, "Network error")
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> Log.e(TAG, "Network timeout")
                        SpeechRecognizer.ERROR_NO_MATCH -> Log.e(TAG, "No recognition result matched")
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Log.e(TAG, "Recognition service busy")
                        SpeechRecognizer.ERROR_SERVER -> Log.e(TAG, "Server error")
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Log.e(TAG, "No speech input")
                    }
                    processNextFile()
                }

                override fun onResults(results: Bundle?) {
                    Log.d(TAG, "onResults")
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val currentFile = transcriptionQueue.peek()
                        currentFile?.let {
                            saveTranscription(it, matches[0])
                        }
                    }
                    processNextFile()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    Log.d(TAG, "onPartialResults")
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val currentFile = transcriptionQueue.peek()
                        currentFile?.let {
                            sendBroadcast(Intent(ACTION_TRANSCRIPTION_PARTIAL).apply {
                                putExtra(EXTRA_FILE_NAME, it.name)
                                putExtra(EXTRA_TRANSCRIPTION, matches[0])
                            })
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    Log.d(TAG, "onEvent: $eventType")
                }
            })
        } else {
            Log.e(TAG, "Speech recognition not available")
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
        try {
            Log.d(TAG, "Starting transcription for file: ${file.name}")
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                prepare()
                start()
                setOnCompletionListener {
                    release()
                    startSpeechRecognition()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    release()
                    processNextFile()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error transcribing file: ${file.name}", e)
            processNextFile()
        }
    }

    private fun startSpeechRecognition() {
        try {
            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            }
            
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
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
        mediaRecorder?.release()
        mediaRecorder = null
        speechRecognizer?.destroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "TranscriptionService"
        const val ACTION_START_TRANSCRIPTION = "com.secondmemory.app.START_TRANSCRIPTION"
        const val ACTION_TRANSCRIBE_ALL = "com.secondmemory.app.TRANSCRIBE_ALL"
        const val ACTION_TRANSCRIPTION_COMPLETED = "com.secondmemory.app.TRANSCRIPTION_COMPLETED"
        const val ACTION_TRANSCRIPTION_PARTIAL = "com.secondmemory.app.TRANSCRIPTION_PARTIAL"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_TRANSCRIPTION = "transcription"
    }
}
