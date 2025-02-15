package com.secondmemory.app

import android.os.Bundle
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.secondmemory.app.utils.AudioFileManager
import com.secondmemory.app.utils.PreferencesManager
import java.text.SimpleDateFormat
import java.util.*

class SettingsActivity : AppCompatActivity() {
    private lateinit var autoStartSwitch: Switch
    private lateinit var storageInfoText: TextView
    private lateinit var retentionPeriodText: TextView
    private lateinit var oldestRecordingText: TextView
    private lateinit var audioFileManager: AudioFileManager
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        audioFileManager = AudioFileManager(this)
        preferencesManager = PreferencesManager(this)

        autoStartSwitch = findViewById(R.id.autoStartSwitch)
        storageInfoText = findViewById(R.id.storageInfoText)
        retentionPeriodText = findViewById(R.id.retentionPeriodText)
        oldestRecordingText = findViewById(R.id.oldestRecordingText)

        setupAutoStartSwitch()
        updateStorageInfo()
        updateRetentionInfo()
    }

    private fun setupAutoStartSwitch() {
        autoStartSwitch.isChecked = preferencesManager.getAutoStartEnabled()
        autoStartSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setAutoStartEnabled(isChecked)
        }
    }

    private fun updateStorageInfo() {
        val usedSpace = audioFileManager.getTotalStorageUsed()
        val availableSpace = audioFileManager.getAvailableStorage()
        
        val usedMB = usedSpace / (1024 * 1024) // Convert to MB
        val availableGB = availableSpace / (1024 * 1024 * 1024) // Convert to GB
        
        storageInfoText.text = getString(R.string.storage_info, usedMB, availableGB)
    }

    private fun updateRetentionInfo() {
        retentionPeriodText.text = getString(R.string.retention_period_info)

        val recordings = audioFileManager.getRecordingsList()
        if (recordings.isNotEmpty()) {
            val oldestRecording = recordings.minByOrNull { it.lastModified() }
            oldestRecording?.let {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                oldestRecordingText.text = getString(
                    R.string.oldest_recording,
                    dateFormat.format(Date(it.lastModified()))
                )
            }
        } else {
            oldestRecordingText.text = getString(R.string.no_recordings)
        }
    }
}
