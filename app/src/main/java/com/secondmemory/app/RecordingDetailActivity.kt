package com.secondmemory.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.secondmemory.app.service.VoskTranscriptionService
import com.secondmemory.app.utils.AudioFileManager
import com.secondmemory.app.utils.PreferencesManager
import java.io.File

class RecordingDetailActivity : AppCompatActivity() {
    private lateinit var audioFileManager: AudioFileManager
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var transcriptText: TextView
    private lateinit var searchInput: EditText
    private lateinit var transcriptProgress: ProgressBar
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var currentFile: File
    private lateinit var playPauseButton: ImageButton
    private lateinit var playbackSeekBar: SeekBar
    private lateinit var currentTimeText: TextView
    private lateinit var totalTimeText: TextView
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private val updateSeekBar = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    playbackSeekBar.progress = player.currentPosition
                    currentTimeText.text = formatTime(player.currentPosition)
                    handler.postDelayed(this, 1000)
                }
            }
        }
    }
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recording_detail)

        audioFileManager = AudioFileManager(this)
        preferencesManager = PreferencesManager(this)
        transcriptText = findViewById(R.id.transcriptText)
        searchInput = findViewById(R.id.searchInput)
        transcriptProgress = findViewById(R.id.transcriptProgress)
        searchResultsRecyclerView = findViewById(R.id.searchResultsRecyclerView)
        playPauseButton = findViewById(R.id.playPauseButton)
        playbackSeekBar = findViewById(R.id.playbackSeekBar)
        currentTimeText = findViewById(R.id.currentTimeText)
        totalTimeText = findViewById(R.id.totalTimeText)

        val fileName = intent.getStringExtra("fileName") ?: return
        currentFile = audioFileManager.getRecordingFile(fileName) ?: return

        setupPlayer(false) // 不自动播放
        setupSearchInput()
        loadTranscription()
    }

    private fun setupPlayer(autoPlay: Boolean) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(currentFile.absolutePath)
                setOnPreparedListener { player ->
                    playbackSeekBar.max = player.duration
                    totalTimeText.text = formatTime(player.duration)
                    if (autoPlay) start()
                }
                prepareAsync()
            }

            playPauseButton.setOnClickListener {
                if (isPlaying) {
                    pausePlayback()
                } else {
                    startPlayback()
                }
            }

            playbackSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        mediaPlayer?.seekTo(progress)
                        currentTimeText.text = formatTime(progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            mediaPlayer?.setOnCompletionListener {
                isPlaying = false
                playPauseButton.setImageResource(R.drawable.ic_play)
                handler.removeCallbacks(updateSeekBar)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPlayback() {
        mediaPlayer?.start()
        isPlaying = true
        playPauseButton.setImageResource(R.drawable.ic_pause)
        handler.post(updateSeekBar)
    }

    private fun pausePlayback() {
        mediaPlayer?.pause()
        isPlaying = false
        playPauseButton.setImageResource(R.drawable.ic_play)
        handler.removeCallbacks(updateSeekBar)
    }

    private fun formatTime(millis: Int): String {
        val minutes = millis / 1000 / 60
        val seconds = millis / 1000 % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun loadTranscription() {
        val transcription = preferencesManager.getTranscription(currentFile.name)
        if (transcription != null) {
            displayFormattedText(transcription)
            transcriptProgress.visibility = View.GONE
        } else {
            transcriptText.text = getString(R.string.transcription_in_progress)
            transcriptProgress.visibility = View.VISIBLE
            startTranscriptionService()
        }
    }

    private fun displayFormattedText(text: String) {
        // 按句号、问号、感叹号分割文本
        val sentences = text.split(Regex("[。？！]"))
            .filter { it.isNotBlank() }
            .map { it.trim() }
        
        // 重新组合文本，每句话后加换行
        val formattedText = sentences.joinToString("\n") { "$it。" }
        transcriptText.text = formattedText
    }

    private fun startTranscriptionService() {
        val intent = Intent(this, VoskTranscriptionService::class.java).apply {
            action = VoskTranscriptionService.ACTION_START_TRANSCRIPTION
            putExtra(VoskTranscriptionService.EXTRA_FILE_NAME, currentFile.name)
        }
        startService(intent)
    }

    private val transcriptionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                VoskTranscriptionService.ACTION_TRANSCRIPTION_COMPLETED -> {
                    val fileName = intent.getStringExtra(VoskTranscriptionService.EXTRA_FILE_NAME)
                    if (fileName == currentFile.name) {
                        val text = intent.getStringExtra(VoskTranscriptionService.EXTRA_TRANSCRIPTION)
                        if (text != null) {
                            displayFormattedText(text)
                            transcriptProgress.visibility = View.GONE
                        }
                    }
                }
                VoskTranscriptionService.ACTION_TRANSCRIPTION_PARTIAL -> {
                    val fileName = intent.getStringExtra(VoskTranscriptionService.EXTRA_FILE_NAME)
                    if (fileName == currentFile.name) {
                        val text = intent.getStringExtra(VoskTranscriptionService.EXTRA_TRANSCRIPTION)
                        if (text != null) {
                            displayFormattedText(text)
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 使用LocalBroadcastManager来避免系统广播可能导致的性能问题
        LocalBroadcastManager.getInstance(this).registerReceiver(
            transcriptionReceiver,
            IntentFilter().apply {
                addAction(VoskTranscriptionService.ACTION_TRANSCRIPTION_COMPLETED)
                addAction(VoskTranscriptionService.ACTION_TRANSCRIPTION_PARTIAL)
            }
        )
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(transcriptionReceiver)
    }

    override fun onBackPressed() {
        // 如果正在识别，不要等待识别完成，直接返回
        if (transcriptProgress.visibility == View.VISIBLE) {
            finish()
            return
        }
        super.onBackPressed()
    }

    private fun setupSearchInput() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchTranscript(s.toString())
            }
        })

        searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun searchTranscript(query: String) {
        if (query.isEmpty()) {
            searchResultsRecyclerView.visibility = View.GONE
            return
        }

        val transcript = transcriptText.text.toString()
        val results = mutableListOf<SearchResult>()
        var lastIndex = 0

        while (true) {
            val index = transcript.indexOf(query, lastIndex, ignoreCase = true)
            if (index == -1) break

            // 获取上下文（前后各50个字符）
            val start = (index - 50).coerceAtLeast(0)
            val end = (index + query.length + 50).coerceAtMost(transcript.length)
            val context = transcript.substring(start, end)

            results.add(SearchResult(context, index))
            lastIndex = index + 1
        }

        if (results.isNotEmpty()) {
            searchResultsRecyclerView.visibility = View.VISIBLE
            searchResultsRecyclerView.adapter = SearchResultsAdapter(results) { result ->
                highlightText(result.position)
            }
        } else {
            searchResultsRecyclerView.visibility = View.GONE
        }
    }

    private fun highlightText(position: Int) {
        // 计算要滚动到的位置
        val layout = transcriptText.layout
        if (layout != null) {
            val line = layout.getLineForOffset(position)
            val y = layout.getLineTop(line)
            // 滚动到对应位置
            transcriptText.scrollTo(0, y)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        handler.removeCallbacks(updateSeekBar)
    }

    data class SearchResult(val context: String, val position: Int)

    private class SearchResultsAdapter(
        private val results: List<SearchResult>,
        private val onClick: (SearchResult) -> Unit
    ) : RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = android.widget.TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(16, 16, 16, 16)
                setTextIsSelectable(true)
            }
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val result = results[position]
            holder.textView.text = result.context
            holder.itemView.setOnClickListener { onClick(result) }
        }

        override fun getItemCount() = results.size
    }
}
