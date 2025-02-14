package com.secondmemory.app.utils

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class AudioFileManager(private val context: Context) {
    companion object {
        private const val MAX_RETENTION_DAYS = 15 // 保存录音文件的最大天数
        private const val AUDIO_FILE_PREFIX = "recording_"
        private const val AUDIO_FILE_EXTENSION = ".m4a"
    }

    private val baseDir: File by lazy {
        File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "SecondMemory").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    fun createNewAudioFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(baseDir, "${AUDIO_FILE_PREFIX}${timestamp}${AUDIO_FILE_EXTENSION}")
    }

    fun manageRecordingFiles() {
        val currentTime = System.currentTimeMillis()
        val retentionPeriod = TimeUnit.DAYS.toMillis(MAX_RETENTION_DAYS.toLong())

        baseDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith(AUDIO_FILE_PREFIX)) {
                val fileAge = currentTime - file.lastModified()
                if (fileAge > retentionPeriod) {
                    file.delete()
                }
            }
        }

        // 检查存储空间
        checkStorageSpace()
    }

    private fun checkStorageSpace() {
        val freeSpace = baseDir.freeSpace
        val totalSpace = baseDir.totalSpace
        val usedSpace = totalSpace - freeSpace
        val usageRatio = usedSpace.toDouble() / totalSpace.toDouble()

        // 如果使用空间超过90%，删除最旧的文件
        if (usageRatio > 0.9) {
            val files = baseDir.listFiles()
                ?.filter { it.isFile && it.name.startsWith(AUDIO_FILE_PREFIX) }
                ?.sortedBy { it.lastModified() }

            files?.take((files.size * 0.2).toInt())?.forEach { // 删除最旧的20%的文件
                it.delete()
            }
        }
    }

    fun getRecordingsList(): List<File> {
        return baseDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(AUDIO_FILE_PREFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun getRecordingFile(fileName: String): File? {
        val file = File(baseDir, fileName)
        return if (file.exists()) file else null
    }

    fun deleteRecording(fileName: String): Boolean {
        val file = File(baseDir, fileName)
        return if (file.exists()) {
            file.delete()
        } else false
    }

    fun getTotalStorageUsed(): Long {
        var total = 0L
        baseDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith(AUDIO_FILE_PREFIX)) {
                total += file.length()
            }
        }
        return total
    }

    fun getAvailableStorage(): Long {
        return baseDir.freeSpace
    }
}
