package com.voiceai.app.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Locale
import java.util.UUID

class SpeechEngine(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var stt: SpeechRecognizer? = null
    private var ttsReady = false

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    sealed class Event {
        object ListeningStarted : Event()
        object ListeningStopped : Event()
        data class PartialResult(val text: String) : Event()
        data class Recognized(val text: String) : Event()
        data class SpeakingFinished(val utteranceId: String) : Event()
        data class Error(val code: Int) : Event()
    }

    fun initialize(onReady: () -> Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.95f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String) {}
                    override fun onDone(id: String) {
                        _events.trySend(Event.SpeakingFinished(id))
                    }
                    @Deprecated("Deprecated in API 21")
                    override fun onError(id: String) {}
                })
                ttsReady = true
            }
            onReady()
        }

        stt = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle) =
                    _events.trySend(Event.ListeningStarted).let {}

                override fun onEndOfSpeech() {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onResults(results: Bundle) {
                    val text = results
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: return
                    _events.trySend(Event.Recognized(text))
                }

                override fun onPartialResults(partial: Bundle) {
                    val text = partial
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: return
                    _events.trySend(Event.PartialResult(text))
                }

                override fun onError(error: Int) =
                    _events.trySend(Event.Error(error)).let {}

                override fun onEvent(type: Int, params: Bundle?) {}
            })
        }
    }

    /** Speaks [text] and returns an utterance ID that matches the SpeakingFinished event. */
    fun speak(text: String): String {
        val id = UUID.randomUUID().toString()
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }
        return id
    }

    fun stopSpeaking() = tts?.stop()

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Allow a few seconds of silence before timing out
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }
        stt?.startListening(intent)
    }

    fun stopListening() {
        stt?.stopListening()
        _events.trySend(Event.ListeningStopped)
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        stt?.destroy()
        _events.close()
    }
}
