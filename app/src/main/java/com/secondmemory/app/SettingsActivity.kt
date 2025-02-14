package com.secondmemory.app

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.secondmemory.app.utils.AudioFileManager
import java.text.SimpleDateFormat
import java.util.*

class SettingsActivity : AppCompatActivity() {
    private lateinit var autoStartSwitch: Switch
    private lateinit var storageInfoText: TextView
    private lateinit var retentionPeriodText: TextView
    private lateinit var audioFileManager: AudioFileManager
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = "SecondMemoryPrefs"
        private const val KEY_AUTO_START = "auto_start_on_boot"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // 初始化
        audioFileManager = AudioFileManager(this)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // 查找视图
        autoStartSwitch = findViewById(R.id.autoStartSwitch)
        storageInfoText = findViewById(R.id.storageInfoText)
        retentionPeriodText = findViewById(R.id.retentionPeriodText)

        // 设置开机自启动开关
        setupAutoStartSwitch()

        // 更新存储信息
        updateStorageInfo()

        // 设置保留期限信息
        updateRetentionPeriodInfo()
    }

    private fun setupAutoStartSwitch() {
        // 读取当前设置
        autoStartSwitch.isChecked = prefs.getBoolean(KEY_AUTO_START, false)

        // 设置监听器
        autoStartSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_START, isChecked).apply()
        }
    }

    private fun updateStorageInfo() {
        val usedSpace = audioFileManager.getTotalStorageUsed()
        val availableSpace = audioFileManager.getAvailableStorage()

        val usedSpaceMB = usedSpace / (1024 * 1024)
        val availableSpaceGB = availableSpace / (1024 * 1024 * 1024)

        storageInfoText.text = getString(
            R.string.storage_info,
            usedSpaceMB,
            availableSpaceGB
        )
    }

    private fun updateRetentionPeriodInfo() {
        val recordings = audioFileManager.getRecordingsList()
        if (recordings.isNotEmpty()) {
            val oldestFile = recordings.minByOrNull { it.lastModified() }
            oldestFile?.let {
                val date = Date(it.lastModified())
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                retentionPeriodText.text = getString(
                    R.string.oldest_recording,
                    dateFormat.format(date)
                )
            }
        } else {
            retentionPeriodText.text = getString(R.string.no_recordings)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStorageInfo()
        updateRetentionPeriodInfo()
    }
}
