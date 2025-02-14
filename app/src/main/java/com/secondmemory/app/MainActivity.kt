package com.secondmemory.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.secondmemory.app.service.RecordingService

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var startStopButton: Button
    private lateinit var settingsButton: Button

    private val PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )
    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        startStopButton = findViewById(R.id.startStopButton)
        settingsButton = findViewById(R.id.settingsButton)

        setupButtons()
        checkPermissions()
        updateUI()
    }

    private fun setupButtons() {
        startStopButton.setOnClickListener {
            if (RecordingService.isRunning) {
                stopRecording()
            } else {
                startRecording()
            }
            updateUI()
        }

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun checkPermissions() {
        val missingPermissions = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun startRecording() {
        val intent = Intent(this, RecordingService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopRecording() {
        stopService(Intent(this, RecordingService::class.java))
    }

    private fun updateUI() {
        if (RecordingService.isRunning) {
            statusText.text = getString(R.string.recording_active)
            startStopButton.text = getString(R.string.stop_recording)
        } else {
            statusText.text = getString(R.string.recording_inactive)
            startStopButton.text = getString(R.string.start_recording)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                updateUI()
            } else {
                // 如果权限被拒绝，显示提示并禁用录音功能
                statusText.text = getString(R.string.permissions_required)
                startStopButton.isEnabled = false
            }
        }
    }
}
