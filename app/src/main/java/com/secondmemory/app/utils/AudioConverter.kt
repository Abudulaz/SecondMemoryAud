package com.secondmemory.app.utils

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodec
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import android.media.MediaPlayer
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioConverter {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        fun convertToWav(inputFile: File, outputFile: File) {
            try {
                var mediaPlayer: MediaPlayer? = null
                try {
                    // 创建MediaPlayer来读取输入文件
                    mediaPlayer = MediaPlayer()
                    mediaPlayer.setDataSource(inputFile.absolutePath)
                    mediaPlayer.prepare()
                    
                    // 获取音频时长（毫秒）
                    val duration = mediaPlayer.duration
                    Log.d("AudioConverter", "Audio duration: $duration ms")
                } finally {
                    mediaPlayer?.release()
                }

                // 设置音频录制配置
                val minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AUDIO_FORMAT
                )

                // 创建临时PCM文件
                val tempPcmFile = File(outputFile.parent, "temp_${System.currentTimeMillis()}.pcm")
                
                try {
                    // 使用MediaExtractor和MediaCodec解码音频
                    val extractor = MediaExtractor()
                    extractor.setDataSource(inputFile.absolutePath)
                    
                    // 查找音频轨道
                    var audioTrackIndex = -1
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                            audioTrackIndex = i
                            break
                        }
                    }
                    
                    if (audioTrackIndex < 0) {
                        throw IOException("No audio track found")
                    }
                    
                    // 选择音频轨道
                    extractor.selectTrack(audioTrackIndex)
                    val format = extractor.getTrackFormat(audioTrackIndex)
                    
                    // 检查音频格式
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    if (mime == null) {
                        throw IOException("Unknown audio format")
                    }
                    
                    // 检查采样率
                    val inputSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    Log.d("AudioConverter", "Input audio format: $mime, sample rate: $inputSampleRate Hz")
                    
                    // 检查声道数
                    val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    Log.d("AudioConverter", "Channel count: $channelCount")
                    
                    // 创建解码器
                    val decoder = MediaCodec.createDecoderByType(mime)
                    
                    // 配置输出格式为16kHz单声道PCM
                    val outputFormat = MediaFormat.createAudioFormat(
                        MediaFormat.MIMETYPE_AUDIO_RAW,
                        SAMPLE_RATE,
                        1  // mono
                    ).apply {
                        setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE)
                        setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
                    }
                    outputFormat.setInteger(
                        MediaFormat.KEY_PCM_ENCODING,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    outputFormat.setInteger(
                        MediaFormat.KEY_SAMPLE_RATE,
                        SAMPLE_RATE
                    )
                    
                    Log.d("AudioConverter", "Input format: $format")
                    Log.d("AudioConverter", "Output format: $outputFormat")
                    
                    decoder.configure(format, null, null, 0)
                    decoder.start()
                    
                    Log.d("AudioConverter", "Starting audio decoding process")
                    Log.d("AudioConverter", "Input sample rate: ${format.getInteger(MediaFormat.KEY_SAMPLE_RATE)}")
                    Log.d("AudioConverter", "Input channel count: ${format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)}")
                    // 准备缓冲区
                    val bufferInfo = MediaCodec.BufferInfo()
                    var outputStream: FileOutputStream? = null
                    var totalBytesProcessed = 0L
                    
                    try {
                        outputStream = FileOutputStream(tempPcmFile)
                        var isEOS = false
                        
                        while (!isEOS) {
                            // 处理输入
                            val inIndex = decoder.dequeueInputBuffer(10000)
                            if (inIndex >= 0) {
                                val inputBuffer = decoder.getInputBuffer(inIndex)
                                if (inputBuffer == null) {
                                    Log.e("AudioConverter", "Failed to get input buffer")
                                    throw IOException("Failed to get input buffer")
                                }
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                
                                if (sampleSize < 0) {
                                    decoder.queueInputBuffer(
                                        inIndex, 0, 0, 0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    isEOS = true
                                } else {
                                    decoder.queueInputBuffer(
                                        inIndex, 0, sampleSize,
                                        extractor.sampleTime, 0
                                    )
                                    extractor.advance()
                                }
                            }
                            
                            // 处理输出
                            var outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                            while (outIndex >= 0) {
                                val outputBuffer = decoder.getOutputBuffer(outIndex)
                                if (outputBuffer == null) {
                                    Log.e("AudioConverter", "Failed to get output buffer")
                                    throw IOException("Failed to get output buffer")
                                }
                                val chunk = ByteArray(bufferInfo.size)
                                outputBuffer.get(chunk)
                                outputBuffer.clear()
                                if (bufferInfo.size > 0) {
                                    // 重采样到16kHz
                                    val inputSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                    if (inputSampleRate != SAMPLE_RATE) {
                                        val resampledChunk = resampleAudio(
                                            chunk,
                                            inputSampleRate,
                                            SAMPLE_RATE
                                        )
                                        outputStream.write(resampledChunk)
                                        totalBytesProcessed += resampledChunk.size
                                        Log.d("AudioConverter", "Resampled and processed $totalBytesProcessed bytes")
                                    } else {
                                        outputStream.write(chunk)
                                        totalBytesProcessed += bufferInfo.size
                                        Log.d("AudioConverter", "Processed $totalBytesProcessed bytes (no resampling needed)")
                                    }
                                }
                                decoder.releaseOutputBuffer(outIndex, false)
                                outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                            }
                        }
                    } finally {
                        outputStream?.close()
                        decoder.stop()
                        decoder.release()
                        extractor.release()
                    }
                    
                    // 将PCM转换为WAV
                    convertPcmToWav(tempPcmFile, outputFile)
                    
                } finally {
                    // 清理临时文件
                    tempPcmFile.delete()
                }
                
            } catch (e: Exception) {
                throw IOException("Error converting audio: ${e.message}", e)
            }
        }

        private fun convertPcmToWav(pcmFile: File, wavFile: File) {
            Log.d("AudioConverter", "Converting PCM to WAV: ${pcmFile.absolutePath} -> ${wavFile.absolutePath}")
            
            if (!pcmFile.exists()) {
                val error = "PCM file does not exist: ${pcmFile.absolutePath}"
                Log.e("AudioConverter", error)
                throw IOException(error)
            }
            
            if (pcmFile.length() == 0L) {
                val error = "PCM file is empty: ${pcmFile.absolutePath}"
                Log.e("AudioConverter", error)
                throw IOException(error)
            }
            
            val pcmData = pcmFile.readBytes()
            Log.d("AudioConverter", "PCM data size: ${pcmData.size} bytes")
            
            val totalDataLen = pcmData.size + 36
            val byteRate = SAMPLE_RATE * 2  // 16 bits per sample
            
            try {
                FileOutputStream(wavFile).use { output ->
                    Log.d("AudioConverter", "Writing WAV header...")
                    // RIFF header
                    writeString(output, "RIFF")
                    writeInt(output, totalDataLen)
                    writeString(output, "WAVE")
                    
                    // fmt chunk
                    writeString(output, "fmt ")
                    writeInt(output, 16)  // Subchunk1Size
                    writeShort(output, 1)  // AudioFormat (PCM)
                    writeShort(output, 1)  // Channels (Mono)
                    writeInt(output, SAMPLE_RATE)  // Sample rate
                    writeInt(output, byteRate)  // Byte rate
                    writeShort(output, 2)  // Block align
                    writeShort(output, 16)  // Bits per sample
                    
                    // data chunk
                    writeString(output, "data")
                    writeInt(output, pcmData.size)
                    output.write(pcmData)
                    
                    Log.d("AudioConverter", "WAV file created successfully: ${wavFile.absolutePath}")
                }
            } catch (e: Exception) {
                val error = "Error creating WAV file: ${e.message}"
                Log.e("AudioConverter", error, e)
                throw IOException(error, e)
            }
        }

        private fun writeString(output: FileOutputStream, value: String) {
            output.write(value.toByteArray())
        }

        private fun writeInt(output: FileOutputStream, value: Int) {
            output.write(value and 0xFF)
            output.write(value shr 8 and 0xFF)
            output.write(value shr 16 and 0xFF)
            output.write(value shr 24 and 0xFF)
        }

        private fun writeShort(output: FileOutputStream, value: Int) {
            output.write(value and 0xFF)
            output.write(value shr 8 and 0xFF)
        }

        private fun resampleAudio(input: ByteArray, inputSampleRate: Int, outputSampleRate: Int): ByteArray {
            Log.d("AudioConverter", "Resampling from ${inputSampleRate}Hz to ${outputSampleRate}Hz")
            
            // 将字节数组转换为短整型数组（16位采样）
            val inputSamples = ShortArray(input.size / 2)
            for (i in inputSamples.indices) {
                inputSamples[i] = (input[i * 2].toInt() and 0xFF or 
                    (input[i * 2 + 1].toInt() shl 8)).toShort()
            }
            
            // 计算输出采样数
            val outputLength = (inputSamples.size * outputSampleRate.toLong() / inputSampleRate).toInt()
            val outputSamples = ShortArray(outputLength)
            
            // 线性插值重采样
            for (i in 0 until outputLength) {
                val inputIndex = (i.toDouble() * inputSampleRate / outputSampleRate).toInt()
                if (inputIndex < inputSamples.size) {
                    outputSamples[i] = inputSamples[inputIndex]
                }
            }
            
            // 将短整型数组转换回字节数组
            val output = ByteArray(outputSamples.size * 2)
            for (i in outputSamples.indices) {
                output[i * 2] = outputSamples[i].toByte()
                output[i * 2 + 1] = (outputSamples[i].toInt() shr 8).toByte()
            }
            
            Log.d("AudioConverter", "Resampling completed: ${input.size} bytes -> ${output.size} bytes")
            return output
        }
    }
}
