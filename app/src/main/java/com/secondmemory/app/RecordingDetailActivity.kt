package com.secondmemory.app

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.secondmemory.app.utils.AudioFileManager
import java.io.File

class RecordingDetailActivity : AppCompatActivity() {
    private lateinit var audioFileManager: AudioFileManager
    private lateinit var transcriptText: TextView
    private lateinit var searchInput: EditText
    private lateinit var transcriptProgress: ProgressBar
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var currentFile: File
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recording_detail)

        audioFileManager = AudioFileManager(this)
        transcriptText = findViewById(R.id.transcriptText)
        searchInput = findViewById(R.id.searchInput)
        transcriptProgress = findViewById(R.id.transcriptProgress)
        searchResultsRecyclerView = findViewById(R.id.searchResultsRecyclerView)

        val fileName = intent.getStringExtra("fileName") ?: return
        currentFile = audioFileManager.getRecordingFile(fileName) ?: return

        setupSpeechRecognizer()
        setupSearchInput()
        startTranscription()
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    transcriptProgress.visibility = View.GONE
                    transcriptText.text = getString(R.string.transcription_failed)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        transcriptText.text = matches[0]
                        transcriptProgress.visibility = View.GONE
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
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

    private fun startTranscription() {
        transcriptProgress.visibility = View.VISIBLE
        val recognizerIntent = android.speech.RecognizerIntent.getVoiceDetailsIntent(this)
        recognizerIntent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        recognizerIntent.putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        speechRecognizer?.startListening(recognizerIntent)
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
        speechRecognizer?.destroy()
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
