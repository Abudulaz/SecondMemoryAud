package com.secondmemory.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.secondmemory.app.service.RecordingService
import com.secondmemory.app.service.VoskTranscriptionService
import com.secondmemory.app.utils.FileUtils
import com.secondmemory.app.utils.SystemSettingsHelper
import io.noties.markwon.Markwon
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var recordingStatus: TextView
    private lateinit var startRecordingButton: Button
    private lateinit var markdownView: TextView
    private lateinit var markwon: Markwon
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recordingStatus = findViewById(R.id.recordingStatus)
        startRecordingButton = findViewById(R.id.startRecordingButton)
        markdownView = findViewById<TextView>(R.id.markdownView).apply {
            movementMethod = ScrollingMovementMethod()
        }

        setupMarkdown()
        setupButtons()
        checkPermissions()
    }

    private fun setupMarkdown() {
        markwon = Markwon.builder(this)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .build()

        // 复制并读取develog.md文件
        FileUtils.copyAssetToInternal(this, "develog.md", "develog.md")
        val devlogFile = File(filesDir, "develog.md")
        val markdown = devlogFile.readText()
        markwon.setMarkdown(markdownView, markdown)
    }

    private fun setupButtons() {
        startRecordingButton.setOnClickListener {
            if (!isRecording) {
                startRecording()
            } else {
                stopRecording()
            }
        }

        findViewById<Button>(R.id.recordingsButton).setOnClickListener {
            startActivity(Intent(this, RecordingsActivity::class.java))
        }

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SystemSettingsHelper.PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startTranscriptionService()
            }
        }
    }

    private fun checkPermissions() {
        val helper = SystemSettingsHelper(this)
        if (helper.checkAndRequestPermissions()) {
            startTranscriptionService()
        }
    }

    private fun startRecording() {
        val intent = Intent(this, RecordingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateUI(true)
    }

    private fun stopRecording() {
        val intent = Intent(this, RecordingService::class.java)
        stopService(intent)
        // 停止录音时自动保存并开始识别
        val saveIntent = Intent(RecordingService.ACTION_SAVE_RECORDING)
        sendBroadcast(saveIntent)
        startTranscriptionService()
        updateUI(false)
    }

    private fun updateUI(recording: Boolean) {
        isRecording = recording
        recordingStatus.text = getString(
            if (recording) R.string.recording_active
            else R.string.recording_inactive
        )
        startRecordingButton.text = getString(
            if (recording) R.string.stop_recording
            else R.string.start_recording
        )
    }

    private fun startTranscriptionService() {
        val intent = Intent(this, VoskTranscriptionService::class.java).apply {
            action = VoskTranscriptionService.ACTION_TRANSCRIBE_ALL
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // 重新读取develog.md文件
        val devlogFile = File(filesDir, "develog.md")
        val markdown = devlogFile.readText()
        markwon.setMarkdown(markdownView, markdown)
    }
}
