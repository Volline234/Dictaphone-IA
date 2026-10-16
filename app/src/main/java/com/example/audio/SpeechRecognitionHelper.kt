package com.example.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SpeechRecognitionHelper(private val context: Context) {

    companion object {
        private const val TAG = "SpeechRecognition"
        // Delay before restarting after silence (ms)
        private const val RESTART_DELAY_MS = 300L
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Committed text = finalized results from previous recognition sessions
    // This allows partial results to display correctly alongside already-recognized text
    private var committedTranscript = ""

    // Whether continuous listening mode is active (auto-restart after silence)
    private var continuousMode = false
    private var currentLanguageCode = "fr-FR"

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Starts continuous speech recognition.
     * The recognizer will automatically restart after pauses/silences
     * until [stopListening] is called explicitly.
     */
    fun startListening(languageCode: String = "fr-FR") {
        stopListening()
        continuousMode = true
        currentLanguageCode = languageCode
        committedTranscript = ""
        startRecognizerSession()
    }

    /**
     * Starts a single recognizer session (internal).
     * Called initially and on each auto-restart after silence.
     */
    private fun startRecognizerSession() {
        if (!continuousMode) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _errorMessage.value = com.example.settings.LocaleHelper.localizedContext(context).getString(com.example.R.string.speech_unavailable)
            return
        }

        // SpeechRecognizer MUST be created on the main thread
        mainHandler.post {
            try {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguageCode)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLanguageCode)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _errorMessage.value = null
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        _isListening.value = false

                        when (error) {
                            // Silence detected or no match — normal during dictation pauses
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                // Auto-restart to keep listening for the next sentence
                                scheduleRestart()
                            }
                            // Recoverable errors — retry
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                                Log.w(TAG, "Network error ($error) — retrying...")
                                scheduleRestart()
                            }
                            // Non-recoverable errors — stop
                            else -> {
                                Log.e(TAG, "Speech recognition error: $error")
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            // Commit this final result to the accumulated transcript
                            committedTranscript = if (committedTranscript.isBlank()) {
                                text
                            } else {
                                "$committedTranscript $text"
                            }
                            _liveTranscript.value = committedTranscript
                        }
                        _isListening.value = false

                        // Auto-restart to capture the next sentence
                        scheduleRestart()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val partial = matches[0]
                            // Show committed text + current partial text in real-time
                            // This fixes the bug where partial results were ignored
                            // when the transcript already had text
                            _liveTranscript.value = if (committedTranscript.isBlank()) {
                                partial
                            } else {
                                "$committedTranscript $partial"
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer = recognizer
                recognizer.startListening(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = e.localizedMessage
            }
        }
    }

    /**
     * Schedules a recognizer restart after a short delay.
     * This creates the "continuous listening" behavior for long dictations.
     */
    private fun scheduleRestart() {
        if (!continuousMode) return
        // Destroy current session before restarting
        destroyCurrentSession()
        mainHandler.postDelayed({
            startRecognizerSession()
        }, RESTART_DELAY_MS)
    }

    private fun destroyCurrentSession() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        speechRecognizer = null
    }

    fun setCustomTranscript(text: String) {
        committedTranscript = text
        _liveTranscript.value = text
    }

    fun appendTranscript(text: String) {
        committedTranscript = if (committedTranscript.isBlank()) {
            text
        } else {
            "$committedTranscript $text"
        }
        _liveTranscript.value = committedTranscript
    }

    fun clearTranscript() {
        committedTranscript = ""
        _liveTranscript.value = ""
        _errorMessage.value = null
    }

    fun stopListening() {
        continuousMode = false
        mainHandler.removeCallbacksAndMessages(null)
        destroyCurrentSession()
        _isListening.value = false
    }
}
