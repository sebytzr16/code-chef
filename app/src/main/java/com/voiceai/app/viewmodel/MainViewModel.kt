package com.voiceai.app.viewmodel

import android.Manifest
import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voiceai.app.engine.LLMEngine
import com.voiceai.app.engine.SpeechEngine
import com.voiceai.app.engine.WebSearchEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val llm = LLMEngine(application)
    private val speech = SpeechEngine(application)
    private val web = WebSearchEngine()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var responseJob: Job? = null
    private var downloadId: Long = -1

    // ---- Data model ----

    data class UiState(
        val screen: Screen = Screen.INITIALIZING,
        val statusText: String = "",
        val partialSpeech: String = "",
        val downloadProgress: Int = 0,
        val hasPermission: Boolean = false,
    )

    enum class Screen {
        INITIALIZING,
        NEEDS_MODEL,
        DOWNLOADING,
        SPEAKING,
        LISTENING,
        THINKING,
        SEARCHING,
        STOPPED,
        ERROR,
    }

    // ---- Init ----

    init {
        val hasMic = ContextCompat.checkSelfPermission(
            getApplication(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        _state.value = _state.value.copy(hasPermission = hasMic)

        viewModelScope.launch {
            speech.initialize { viewModelScope.launch { bootLLM() } }
        }
        collectSpeechEvents()
    }

    private suspend fun bootLLM() {
        if (!llm.modelExists) {
            set(Screen.NEEDS_MODEL, "AI model not found.\nTap below to download (~1.4 GB).")
            return
        }
        set(Screen.INITIALIZING, "Loading AI model…")
        runCatching { llm.initialize() }
            .onSuccess { deliverWelcome() }
            .onFailure { set(Screen.ERROR, "Failed to load model: ${it.message}") }
    }

    // ---- Welcome ----

    private fun deliverWelcome() {
        viewModelScope.launch {
            set(Screen.THINKING, "")
            val welcome = llm.generate(
                "Greet the user in one warm sentence and say you are ready to help with any question."
            )
            speakThenListen(welcome)
        }
    }

    // ---- Speech event loop ----

    private fun collectSpeechEvents() {
        viewModelScope.launch {
            speech.events.collect { event ->
                when (event) {
                    is SpeechEngine.Event.ListeningStarted ->
                        set(Screen.LISTENING, "Listening…")

                    is SpeechEngine.Event.PartialResult ->
                        _state.value = _state.value.copy(partialSpeech = event.text)

                    is SpeechEngine.Event.Recognized -> {
                        _state.value = _state.value.copy(partialSpeech = "")
                        handleUserInput(event.text)
                    }

                    is SpeechEngine.Event.SpeakingFinished -> {
                        if (_state.value.screen == Screen.SPEAKING) {
                            delay(300)
                            if (_state.value.screen != Screen.STOPPED) startListening()
                        }
                    }

                    is SpeechEngine.Event.Error -> {
                        // On STT error, wait briefly and retry unless paused
                        if (_state.value.screen != Screen.STOPPED &&
                            _state.value.screen != Screen.NEEDS_MODEL
                        ) {
                            delay(800)
                            if (_state.value.screen == Screen.LISTENING) startListening()
                        }
                    }

                    is SpeechEngine.Event.ListeningStopped -> Unit
                }
            }
        }
    }

    // ---- LLM + web search pipeline ----

    private fun handleUserInput(text: String) {
        responseJob?.cancel()
        responseJob = viewModelScope.launch {
            set(Screen.THINKING, "Thinking…")

            val firstResponse = llm.generate(text)
            val searchQuery = llm.extractSearchQuery(firstResponse)

            if (searchQuery != null) {
                set(Screen.SEARCHING, "Looking that up…")
                val results = web.search(searchQuery)
                val finalResponse = llm.generate(text, results)
                speakThenListen(finalResponse)
            } else {
                speakThenListen(firstResponse)
            }
        }
    }

    private fun startListening() {
        if (_state.value.hasPermission) {
            set(Screen.LISTENING, "Listening…")
            speech.startListening()
        }
    }

    private fun speakThenListen(text: String) {
        set(Screen.SPEAKING, text)
        speech.speak(text)
    }

    // ---- Controls ----

    fun togglePlayStop() {
        when (_state.value.screen) {
            Screen.STOPPED -> resume()
            else -> pause()
        }
    }

    private fun pause() {
        responseJob?.cancel()
        speech.stopSpeaking()
        speech.stopListening()
        set(Screen.STOPPED, "Paused — tap ▶ to resume")
    }

    private fun resume() {
        set(Screen.THINKING, "")
        startListening()
    }

    fun onPermissionGranted() {
        _state.value = _state.value.copy(hasPermission = true)
        if (_state.value.screen == Screen.STOPPED || _state.value.screen == Screen.ERROR) {
            viewModelScope.launch { bootLLM() }
        }
    }

    // ---- Model download ----

    fun startModelDownload() {
        val app = getApplication<Application>()
        val destDir = File(app.filesDir, "models").also { it.mkdirs() }
        val destFile = File(destDir, LLMEngine.MODEL_FILENAME)
        if (destFile.exists()) destFile.delete()

        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(LLMEngine.MODEL_DOWNLOAD_URL))
            .setTitle("Voice AI – Downloading model")
            .setDescription("Gemma 2B IT (~1.4 GB) — Wi-Fi recommended")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(destFile))
            .setAllowedOverMetered(false)

        downloadId = dm.enqueue(request)
        set(Screen.DOWNLOADING, "Downloading model…")
        pollDownloadProgress(dm)
    }

    private fun pollDownloadProgress(dm: DownloadManager) {
        viewModelScope.launch {
            while (_state.value.screen == Screen.DOWNLOADING) {
                delay(1500)
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(query)
                if (!cursor.moveToFirst()) continue

                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                val status = if (statusIdx != -1) cursor.getInt(statusIdx) else -1
                val downloaded = if (downloadedIdx != -1) cursor.getLong(downloadedIdx) else 0L
                val total = if (totalIdx != -1) cursor.getLong(totalIdx) else -1L
                cursor.close()

                val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                _state.value = _state.value.copy(downloadProgress = progress)

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        set(Screen.INITIALIZING, "Download complete. Loading model…")
                        runCatching { llm.initialize() }
                            .onSuccess { deliverWelcome() }
                            .onFailure { set(Screen.ERROR, "Model load failed: ${it.message}") }
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        set(Screen.NEEDS_MODEL, "Download failed. Check your connection and try again.")
                        return@launch
                    }
                }
            }
        }
    }

    // ---- Helpers ----

    private fun set(screen: Screen, text: String) {
        _state.value = _state.value.copy(screen = screen, statusText = text)
    }

    override fun onCleared() {
        super.onCleared()
        llm.release()
        speech.release()
    }
}
