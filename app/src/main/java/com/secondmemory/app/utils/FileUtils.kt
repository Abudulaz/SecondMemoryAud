package com.secondmemory.app.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object FileUtils {
    fun copyAssetToInternal(context: Context, assetName: String, targetFileName: String) {
        try {
            val assetManager = context.assets
            val targetDir = File(context.filesDir, targetFileName)
            
            // Check if it's a directory
            try {
                // Try to list contents - if it succeeds, it's a directory
                val files = assetManager.list(assetName)
                if (files != null && files.isNotEmpty()) {
                    copyAssetDirectory(context, assetName, targetDir)
                    return
                }
            } catch (e: IOException) {
                // Not a directory, continue with file copy
            }

            // Copy single file
            if (!targetDir.exists()) {
                val inputStream = assetManager.open(assetName)
                FileOutputStream(targetDir).use { output ->
                    inputStream.copyTo(output)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun copyAssetDirectory(context: Context, assetPath: String, targetDir: File) {
        val assetManager = context.assets
        
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        
        try {
            assetManager.list(assetPath)?.forEach { fileName ->
                val subAssetPath = if (assetPath.isEmpty()) fileName else "$assetPath/$fileName"
                val subTargetDir = File(targetDir, fileName)
                
                try {
                    // Try to list contents - if it succeeds, it's a directory
                    val subFiles = assetManager.list(subAssetPath)
                    if (subFiles != null && subFiles.isNotEmpty()) {
                        copyAssetDirectory(context, subAssetPath, subTargetDir)
                    } else {
                        // It's a file
                        val inputStream = assetManager.open(subAssetPath)
                        FileOutputStream(subTargetDir).use { output ->
                            inputStream.copyTo(output)
                        }
                    }
                } catch (e: IOException) {
                    // Not a directory, copy as file
                    val inputStream = assetManager.open(subAssetPath)
                    FileOutputStream(subTargetDir).use { output ->
                        inputStream.copyTo(output)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun writeToFile(context: Context, fileName: String, content: String) {
        try {
            val file = File(context.filesDir, fileName)
            file.writeText(content)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
