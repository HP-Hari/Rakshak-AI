package com.example.engine

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
import java.util.Locale

/**
 * LiveSpeechRecognizerManager
 *
 * Resilient, Continuous On-Device Speech Recognition Manager.
 * Unlike standard single-shot speech recognizers which shut down after 2 seconds
 * of silence, this manager maintains a persistent listening loop throughout
 * live phone calls and ambient sentry sessions, automatically re-arming itself
 * upon timeouts, pause boundaries, and speech endpoints.
 */
class LiveSpeechRecognizerManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _fullSessionTranscript = MutableStateFlow("")
    val fullSessionTranscript: StateFlow<String> = _fullSessionTranscript.asStateFlow()

    private val _audioRms = MutableStateFlow(0f)
    val audioRms: StateFlow<Float> = _audioRms.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    var onPartialWordCallback: ((String) -> Unit)? = null
    var onFinalSpeechCallback: ((String) -> Unit)? = null

    @Volatile
    private var isContinuousModeActive = false
    private var restartAttemptCount = 0

    companion object {
        private const val TAG = "LiveSpeechRecognizer"
    }

    fun startListening(continuous: Boolean = true) {
        isContinuousModeActive = continuous
        restartAttemptCount = 0
        mainHandler.post {
            initiateListeningSession()
        }
    }

    private fun initiateListeningSession() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _errorMessage.value = "Speech recognition service not available on this device"
                _isListening.value = false
                return
            }

            // Cleanup any existing instance cleanly
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning previous recognizer: ${e.message}")
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _errorMessage.value = null
                        restartAttemptCount = 0
                        Log.d(TAG, "SpeechRecognizer is READY and actively listening.")
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        _audioRms.value = (rmsdB / 10f).coerceIn(0f, 1f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "Speech endpoint reached. Continuous mode: $isContinuousModeActive")
                        // If continuous, do not mark as inactive; wait for results or restart
                    }

                    override fun onError(error: Int) {
                        val errorText = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording notice"
                            SpeechRecognizer.ERROR_CLIENT -> "Client side notice"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "RECORD_AUDIO permission required"
                            SpeechRecognizer.ERROR_NETWORK -> "Network connection notice"
                            SpeechRecognizer.ERROR_NO_MATCH -> "Listening for caller speech..."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy, retrying..."
                            SpeechRecognizer.ERROR_SERVER -> "Server notice"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening..."
                            else -> "Speech recognizer active ($error)"
                        }
                        Log.d(TAG, "SpeechRecognizer status: $errorText ($error) - Continuous: $isContinuousModeActive")
                        _isListening.value = false
                        _errorMessage.value = errorText

                        if (isContinuousModeActive) {
                            val retryDelay = when (error) {
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 500L
                                SpeechRecognizer.ERROR_AUDIO -> 400L
                                else -> 200L
                            }
                            scheduleContinuousRestart(delayMs = retryDelay)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            _liveTranscript.value = text
                            val currentFull = _fullSessionTranscript.value
                            _fullSessionTranscript.value = if (currentFull.isBlank()) text else "$currentFull $text"
                            onFinalSpeechCallback?.invoke(text)
                        }
                        _isListening.value = false
                        if (isContinuousModeActive) {
                            scheduleContinuousRestart(delayMs = 150)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            _liveTranscript.value = text
                            onPartialWordCallback?.invoke(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognizer: ${e.message}", e)
            _errorMessage.value = e.message
            if (isContinuousModeActive) {
                scheduleContinuousRestart(delayMs = 600)
            } else {
                _isListening.value = false
            }
        }
    }

    private fun scheduleContinuousRestart(delayMs: Long = 200) {
        if (!isContinuousModeActive) return
        restartAttemptCount++
        val cappedDelay = if (restartAttemptCount > 5) 800L else delayMs

        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (isContinuousModeActive) {
                initiateListeningSession()
            }
        }, cappedDelay)
    }

    fun stopListening() {
        isContinuousModeActive = false
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping speech recognizer: ${e.message}", e)
            }
            _isListening.value = false
            _audioRms.value = 0f
        }
    }

    fun clearTranscript() {
        _liveTranscript.value = ""
        _fullSessionTranscript.value = ""
        _errorMessage.value = null
    }
}
