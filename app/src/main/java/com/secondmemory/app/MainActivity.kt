package com.secondmemory.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.secondmemory.app.service.RecordingService
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var recordingStartTimeText: TextView
    private lateinit var recordingDurationText: TextView
    private lateinit var startStopButton: Button
    private lateinit var saveButton: Button
    private lateinit var settingsButton: Button
    private lateinit var recordingsButton: Button
    private var recordingStartTime: Long = 0
    private val durationUpdateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val durationUpdateRunnable = object : Runnable {
        override fun run() {
            updateRecordingDuration()
            durationUpdateHandler.postDelayed(this, 1000)
        }
    }

    private val PERMISSIONS = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    } else {
        arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        recordingStartTimeText = findViewById(R.id.recordingStartTimeText)
        recordingDurationText = findViewById(R.id.recordingDurationText)
        startStopButton = findViewById(R.id.startStopButton)
        saveButton = findViewById(R.id.saveButton)
        settingsButton = findViewById(R.id.settingsButton)
        recordingsButton = findViewById(R.id.recordingsButton)

        setupButtons()
        checkPermissions()
        checkBatteryOptimization()
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

        saveButton.setOnClickListener {
            if (RecordingService.isRunning) {
                val intent = Intent(this, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_SAVE_RECORDING
                }
                startService(intent)
            }
        }

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        recordingsButton.setOnClickListener {
            startActivity(Intent(this, RecordingsActivity::class.java))
        }
    }

    private fun checkBatteryOptimization() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            val packageName = packageName
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.battery_optimization_title)
                    .setMessage(R.string.battery_optimization_message)
                    .setPositiveButton(R.string.go_to_settings) { _, _ ->
                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = android.net.Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun checkPermissions() {
        val missingPermissions = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            if (missingPermissions.any { permission ->
                    ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
                }) {
                // 显示权限解释对话框
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("需要权限")
                    .setMessage(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        "为了录音功能的正常使用，我们需要录音权限。\n\n" +
                        "录音权限：用于录制和保存音频"
                    } else {
                        "为了录音功能的正常使用，我们需要录音和存储权限。\n\n" +
                        "录音权限：用于录制音频\n" +
                        "存储权限：用于保存录音文件"
                    })
                    .setPositiveButton("授予权限") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            missingPermissions.toTypedArray(),
                            PERMISSION_REQUEST_CODE
                        )
                    }
                    .setNegativeButton("取消") { dialog, _ ->
                        dialog.dismiss()
                        updateUIForMissingPermissions()
                    }
                    .show()
            } else {
                // 直接请求权限
                ActivityCompat.requestPermissions(
                    this,
                    missingPermissions.toTypedArray(),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun updateUIForMissingPermissions() {
        statusText.text = getString(R.string.permissions_required)
        startStopButton.isEnabled = false
    }

    private fun startRecording() {
        val intent = Intent(this, RecordingService::class.java)
        ContextCompat.startForegroundService(this, intent)
        recordingStartTime = System.currentTimeMillis()
        updateUI(true)
        startDurationUpdates()
    }

    private fun stopRecording() {
        stopService(Intent(this, RecordingService::class.java))
        updateUI(false)
        stopDurationUpdates()
    }

    private fun updateUI(isRecording: Boolean = RecordingService.isRunning) {
        statusText.text = getString(if (isRecording) R.string.recording_active else R.string.recording_inactive)
        startStopButton.text = getString(if (isRecording) R.string.stop_recording else R.string.start_recording)
        saveButton.isEnabled = isRecording
        
        if (isRecording) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            recordingStartTimeText.text = getString(R.string.recording_start_time, dateFormat.format(Date(recordingStartTime)))
            updateRecordingDuration()
        } else {
            recordingStartTimeText.text = ""
            recordingDurationText.text = ""
        }
    }

    private fun updateRecordingDuration() {
        if (RecordingService.isRunning) {
            val duration = System.currentTimeMillis() - recordingStartTime
            val hours = duration / (1000 * 60 * 60)
            val minutes = (duration % (1000 * 60 * 60)) / (1000 * 60)
            val seconds = (duration % (1000 * 60)) / 1000
            recordingDurationText.text = getString(R.string.recording_duration, 
                String.format("%02d:%02d:%02d", hours, minutes, seconds))
        }
    }

    private fun startDurationUpdates() {
        durationUpdateHandler.post(durationUpdateRunnable)
    }

    private fun stopDurationUpdates() {
        durationUpdateHandler.removeCallbacks(durationUpdateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDurationUpdates()
    }

    // 注册广播接收器来监听录音服务的状态变化
    private val recordingStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateUI()
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(
            recordingStateReceiver,
            android.content.IntentFilter("com.secondmemory.app.RECORDING_STATE_CHANGED")
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(recordingStateReceiver)
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
                // 如果权限被拒绝，显示提示并引导用户去设置中开启权限
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("权限被拒绝")
                    .setMessage("没有必要的权限，应用将无法正常工作。请在设置中手动开启所需权限。")
                    .setPositiveButton("去设置") { _, _ ->
                        // 打开应用设置页面
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", packageName, null)
                        )
                        startActivity(intent)
                    }
                    .setNegativeButton("取消") { dialog, _ ->
                        dialog.dismiss()
                        updateUIForMissingPermissions()
                    }
                    .show()
            }
        }
    }
}
