package com.secondmemory.app

import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.secondmemory.app.utils.AudioFileManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecordingsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var audioFileManager: AudioFileManager
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingPosition: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recordings)

        audioFileManager = AudioFileManager(this)
        recyclerView = findViewById(R.id.recordingsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        updateRecordingsList()
    }

    private fun updateRecordingsList() {
        val recordings = audioFileManager.getRecordingsList()
        recyclerView.adapter = RecordingsAdapter(recordings) { file, position ->
            playRecording(file, position)
        }
    }

    private fun playRecording(file: File, position: Int) {
        if (currentPlayingPosition == position) {
            // 点击当前正在播放的录音，停止播放
            stopPlayback()
            return
        }

        // 停止当前播放的录音
        stopPlayback()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                }
                start()
            }
            currentPlayingPosition = position
            (recyclerView.adapter as RecordingsAdapter).notifyDataSetChanged()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        val oldPosition = currentPlayingPosition
        currentPlayingPosition = -1
        recyclerView.adapter?.notifyItemChanged(oldPosition)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }

    inner class RecordingsAdapter(
        private val recordings: List<File>,
        private val onItemClick: (File, Int) -> Unit
    ) : RecyclerView.Adapter<RecordingsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameText: TextView = view.findViewById(R.id.recordingNameText)
            val dateText: TextView = view.findViewById(R.id.recordingDateText)
            val playButton: ImageButton = view.findViewById(R.id.playButton)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recording, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = recordings[position]
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            
            holder.nameText.text = file.name
            holder.dateText.text = dateFormat.format(Date(file.lastModified()))
            
            // 更新播放按钮状态
            holder.playButton.setImageResource(
                if (position == currentPlayingPosition) 
                    R.drawable.ic_stop 
                else 
                    R.drawable.ic_play
            )
            
            holder.playButton.setOnClickListener {
                onItemClick(file, position)
            }
        }

        override fun getItemCount() = recordings.size
    }
}
