package com.dakd.jarvis

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class VoiceManager(
    private val context: Context,
    private val permissionManager: PermissionManager
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    var isListening: Boolean = false
        private set

    var onListeningStarted: (() -> Unit)? = null
    var onListeningEnded: (() -> Unit)? = null
    var onRmsChanged: ((Float) -> Unit)? = null
    var onSpeechResult: ((String) -> Unit)? = null
    var onSpeechError: ((String) -> Unit)? = null

    init {
        mainHandler.post {
            ensureRecognizer()
        }
    }

    private fun ensureRecognizer() {
        if (speechRecognizer == null && SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createListener())
            }
        }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            onListeningStarted?.invoke()
        }

        override fun onBeginningOfSpeech() {
            isListening = true
        }

        override fun onRmsChanged(rmsdB: Float) {
            onRmsChanged?.invoke(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
            onListeningEnded?.invoke()
        }

        override fun onError(error: Int) {
            isListening = false
            onListeningEnded?.invoke()
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> "Speech recognition error ($error)"
            }
            onSpeechError?.invoke(errorMsg)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            onListeningEnded?.invoke()
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull() ?: ""
            if (recognizedText.isNotBlank()) {
                onSpeechResult?.invoke(recognizedText)
            } else {
                onSpeechError?.invoke("No text recognized")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening(languageHint: String = "hi-IN") {
        if (!permissionManager.hasAudioPermission()) {
            onSpeechError?.invoke("Microphone permission required hai Sir.")
            return
        }

        mainHandler.post {
            ensureRecognizer()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageHint)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageHint)
                putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("hi-IN", "en-IN", "en-US"))
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            try {
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                isListening = false
                onListeningEnded?.invoke()
                onSpeechError?.invoke("Microphone start nahi ho saka: ${e.message}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                // ignore
            }
            isListening = false
            onListeningEnded?.invoke()
        }
    }

    fun destroy() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
