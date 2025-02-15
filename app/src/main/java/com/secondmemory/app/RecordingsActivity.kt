package com.secondmemory.app

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.secondmemory.app.utils.PreferencesManager
import com.secondmemory.app.service.TranscriptionService
import com.secondmemory.app.utils.AudioFileManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecordingsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var searchView: SearchView
    private lateinit var audioFileManager: AudioFileManager
    private lateinit var preferencesManager: PreferencesManager
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingPosition: Int = -1
    private var recordings: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recordings)

        audioFileManager = AudioFileManager(this)
        preferencesManager = PreferencesManager(this)
        
        recyclerView = findViewById(R.id.recordingsRecyclerView)
        searchResultsRecyclerView = findViewById(R.id.searchResultsRecyclerView)
        searchView = findViewById(R.id.searchView)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)
        
        setupSearchView()
        
        // 启动后台转写服务
        startService(Intent(this, TranscriptionService::class.java).apply {
            action = TranscriptionService.ACTION_TRANSCRIBE_ALL
        })
        
        updateRecordingsList()
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    performSearch(query)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrBlank()) {
                    searchResultsRecyclerView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                } else {
                    performSearch(newText)
                }
                return true
            }
        })
    }

    private fun performSearch(query: String) {
        val searchResults = mutableListOf<SearchResult>()
        
        recordings.forEach { file ->
            val transcription = preferencesManager.getTranscription(file.name)
            if (transcription != null && transcription.contains(query, ignoreCase = true)) {
                // 找到匹配的文本位置
                var lastIndex = 0
                while (true) {
                    val index = transcription.indexOf(query, lastIndex, ignoreCase = true)
                    if (index == -1) break
                    
                    // 获取上下文（前后各50个字符）
                    val start = (index - 50).coerceAtLeast(0)
                    val end = (index + query.length + 50).coerceAtMost(transcription.length)
                    val context = transcription.substring(start, end)
                    
                    searchResults.add(SearchResult(file, context, index))
                    lastIndex = index + 1
                }
            }
        }
        
        if (searchResults.isNotEmpty()) {
            searchResultsRecyclerView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            searchResultsRecyclerView.adapter = SearchResultsAdapter(searchResults) { result ->
                val intent = Intent(this, RecordingDetailActivity::class.java)
                intent.putExtra("fileName", result.file.name)
                startActivity(intent)
            }
        } else {
            searchResultsRecyclerView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun updateRecordingsList() {
        recordings = audioFileManager.getRecordingsList()
        recyclerView.adapter = RecordingsAdapter(recordings) { file, position ->
            playRecording(file, position)
        }
    }

    data class SearchResult(
        val file: File,
        val context: String,
        val position: Int
    )

    inner class SearchResultsAdapter(
        private val results: List<SearchResult>,
        private val onClick: (SearchResult) -> Unit
    ) : RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val fileNameText: TextView = view.findViewById(R.id.fileNameText)
            val contextText: TextView = view.findViewById(R.id.contextText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_result, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val result = results[position]
            holder.fileNameText.text = result.file.name
            holder.contextText.text = result.context
            holder.itemView.setOnClickListener { onClick(result) }
        }

        override fun getItemCount() = results.size
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
            val cardView: androidx.cardview.widget.CardView = view as androidx.cardview.widget.CardView
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
            
            holder.itemView.setOnClickListener {
                val intent = Intent(this@RecordingsActivity, RecordingDetailActivity::class.java)
                intent.putExtra("fileName", file.name)
                startActivity(intent)
            }

            holder.playButton.setOnClickListener {
                onItemClick(file, position)
            }

            holder.cardView.setOnLongClickListener {
                showPopupMenu(it, file)
                true
            }
        }

        override fun getItemCount() = recordings.size

        private fun showPopupMenu(view: View, file: File) {
            val popup = android.widget.PopupMenu(this@RecordingsActivity, view)
            popup.menuInflater.inflate(R.menu.recording_item_menu, popup.menu)
            
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_delete -> {
                        showDeleteConfirmationDialog(file)
                        true
                    }
                    R.id.action_export -> {
                        exportRecording(file)
                        true
                    }
                    else -> false
                }
            }
            
            popup.show()
        }
    }

    private fun showDeleteConfirmationDialog(file: File) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.delete_recording_title)
            .setMessage(R.string.delete_recording_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (audioFileManager.deleteRecording(file.name)) {
                    updateRecordingsList()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun exportRecording(file: File) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/mp4"
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        startActivityForResult(intent, CREATE_FILE_REQUEST_CODE)
        currentExportingFile = file
    }

    private var currentExportingFile: File? = null
    private val CREATE_FILE_REQUEST_CODE = 1

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_FILE_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                currentExportingFile?.let { file ->
                    try {
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            file.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        android.widget.Toast.makeText(
                            this,
                            R.string.export_success,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                            this,
                            R.string.export_failed,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        e.printStackTrace()
                    }
                }
            }
        }
        currentExportingFile = null
    }
}
