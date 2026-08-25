package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.RakshakApplication
import com.example.engine.AudioScamTfLiteClassifier
import com.example.engine.NpuInferenceEngine
import com.example.ui.overlay.FraudAlertOverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * CallAudioInterceptorService
 *
 * Real-time active phone call sentry that captures the raw audio stream from incoming
 * and outgoing calls using AudioManager communication routing and MediaProjection /
 * AudioPlaybackCapture API.
 *
 * Captures the remote caller's audio stream in real-time for continuous NPU inference,
 * social engineering detection, and acoustic fraud classification.
 */
class CallAudioInterceptorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var captureJob: Job? = null
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private lateinit var tfLiteClassifier: AudioScamTfLiteClassifier
    private val npuEngine = NpuInferenceEngine()

    private var audioManager: AudioManager? = null
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var activeCommunicationDevice: AudioDeviceInfo? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var activeCallerNumber: String = "Active Call"
    private var lastScamAlertTimestamp: Long = 0L

    // Accumulated rolling transcript of the ongoing call session
    private val callDialogueHistory = StringBuilder()

    companion object {
        private const val TAG = "CallAudioInterceptor"
        private const val NOTIFICATION_ID = 5005
        private const val CHANNEL_ID = "rakshak_audio_interceptor_channel"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"

        const val ACTION_START = "com.example.service.ACTION_START_AUDIO_CAPTURE"
        const val ACTION_STOP = "com.example.service.ACTION_STOP_AUDIO_CAPTURE"

        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        private val _isInterceptorActive = MutableStateFlow(false)
        val isInterceptorActive: StateFlow<Boolean> = _isInterceptorActive.asStateFlow()

        private val _latestAudioClassification = MutableSharedFlow<AudioScamTfLiteClassifier.AudioClassificationResult>(extraBufferCapacity = 10)
        val latestAudioClassification: SharedFlow<AudioScamTfLiteClassifier.AudioClassificationResult> = _latestAudioClassification.asSharedFlow()

        private val _liveCallTranscript = MutableStateFlow("")
        val liveCallTranscript: StateFlow<String> = _liveCallTranscript.asStateFlow()

        private val _fullCallTranscript = MutableStateFlow("")
        val fullCallTranscript: StateFlow<String> = _fullCallTranscript.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        tfLiteClassifier = AudioScamTfLiteClassifier(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                activeCallerNumber = intent.getStringExtra(EXTRA_CALLER_NUMBER) ?: "Active Call"

                startForeground(NOTIFICATION_ID, createNotification("Rakshak Sentry Active • Live Call Listening with $activeCallerNumber"))
                _isInterceptorActive.value = true
                callDialogueHistory.clear()
                _liveCallTranscript.value = ""
                _fullCallTranscript.value = ""

                Log.i(TAG, "🟢 Starting acoustic & telecom sentry for $activeCallerNumber")

                // Start continuous real-time speech recognizer for live conversation fraud analysis
                startContinuousSpeechRecognition()

                // Launch Raw Audio Capture & Acoustic Stream Scanner
                if (resultCode != 0 && resultData != null) {
                    startMediaProjectionCapture(resultCode, resultData, activeCallerNumber)
                } else {
                    startDirectCallStreamCapture(activeCallerNumber)
                }
            }
            ACTION_STOP -> {
                Log.i(TAG, "🔴 Stopping real-time call listening sentry.")
                stopAudioCapture()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Initializes and maintains continuous speech recognition throughout the live call.
     */
    private fun startContinuousSpeechRecognition() {
        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                    Log.w(TAG, "Speech recognition service unavailable on device.")
                    return@post
                }

                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            Log.d(TAG, "SpeechRecognizer ready for live call dialogue.")
                        }

                        override fun onBeginningOfSpeech() {}

                        override fun onRmsChanged(rmsdB: Float) {}

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            Log.d(TAG, "End of speech chunk. Re-arming listener...")
                        }

                        override fun onError(error: Int) {
                            Log.d(TAG, "SpeechRecognizer error code: $error. Auto-restarting loop...")
                            if (_isInterceptorActive.value) {
                                mainHandler.postDelayed({
                                    if (_isInterceptorActive.value) {
                                        restartSpeechListening()
                                    }
                                }, 250)
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull() ?: ""
                            if (text.isNotBlank()) {
                                synchronized(callDialogueHistory) {
                                    if (callDialogueHistory.isNotEmpty()) {
                                        callDialogueHistory.append(" ")
                                    }
                                    callDialogueHistory.append(text)
                                }
                                _liveCallTranscript.value = text
                                _fullCallTranscript.value = callDialogueHistory.toString()
                                processSpokenTranscript(text, callDialogueHistory.toString())
                            }
                            if (_isInterceptorActive.value) {
                                mainHandler.postDelayed({
                                    if (_isInterceptorActive.value) {
                                        restartSpeechListening()
                                    }
                                }, 150)
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull() ?: ""
                            if (text.isNotBlank()) {
                                _liveCallTranscript.value = text
                                processSpokenTranscript(text, callDialogueHistory.toString() + " " + text)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                restartSpeechListening()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize continuous speech recognizer: ${e.message}", e)
            }
        }
    }

    private fun restartSpeechListening() {
        if (!_isInterceptorActive.value) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Error in restartSpeechListening: ${e.message}")
        }
    }

    /**
     * Evaluates live spoken dialogue in real time for social engineering threats.
     */
    private fun processSpokenTranscript(currentPhrase: String, cumulativeText: String) {
        Log.d(TAG, "Live phrase: '$currentPhrase' | Total Dialogue: '${cumulativeText.takeLast(100)}'")

        serviceScope.launch {
            // First check the immediate phrase, then check the whole conversation
            var analysis = npuEngine.classifyTranscriptStream(currentPhrase)
            if (!analysis.isScam && cumulativeText.isNotBlank()) {
                analysis = npuEngine.classifyTranscriptStream(cumulativeText)
            }

            if (analysis.isScam) {
                val now = System.currentTimeMillis()
                if (now - lastScamAlertTimestamp > 7000) {
                    lastScamAlertTimestamp = now
                    handleSocialEngineeringThreatDetected(activeCallerNumber, analysis, currentPhrase)
                }
            }
        }
    }

    /**
     * Triggered immediately when social engineering scam cues (OTP, Digital Arrest, KYC, APK)
     * are spoken on the active call.
     */
    private fun handleSocialEngineeringThreatDetected(
        callerNumber: String,
        analysis: com.example.data.model.ScamAnalysisResult,
        rawSpokenText: String
    ) {
        val appContext = applicationContext as? RakshakApplication

        val archetype = when (analysis.threatCategory) {
            com.example.data.model.ThreatCategory.OTP_THEFT -> "OTP & Security Code Solicitation"
            com.example.data.model.ThreatCategory.URGENT_FINE -> "Digital Arrest & Police Impersonation"
            com.example.data.model.ThreatCategory.APK_SIDELOAD -> "Malicious APK / Remote Screen Share"
            com.example.data.model.ThreatCategory.LOTTERY_PRIZE -> "Prize / Advance Fee Fraud"
            else -> "Social Engineering Fraud Vector"
        }

        val recommendedAction = when (analysis.threatCategory) {
            com.example.data.model.ThreatCategory.OTP_THEFT -> "DO NOT SHARE OTP • HANG UP IMMEDIATELY"
            com.example.data.model.ThreatCategory.URGENT_FINE -> "NO POLICE CALLS OVER PHONE • DISCONNECT NOW"
            com.example.data.model.ThreatCategory.APK_SIDELOAD -> "DO NOT INSTALL ANY APP • DISCONNECT CALL"
            else -> "DO NOT SHARE MONEY OR CODES • HANG UP"
        }

        Log.w(TAG, "🚨 SOCIAL ENGINEERING FRAUD IDENTIFIED on active call ($callerNumber): $archetype")

        // 1. Instantly display Floating Awareness Overlay on top of Phone Dialer
        val overlayData = FraudAlertOverlayManager.OverlayData(
            callerNumber = callerNumber,
            archetype = archetype,
            confidence = analysis.confidence,
            stressLevel = 0.95f,
            reasoning = analysis.reasoning,
            recommendedAction = recommendedAction,
            acousticMarkers = analysis.triggerWords
        )
        mainHandler.post {
            FraudAlertOverlayManager.getInstance(applicationContext).showFraudAlertOverlay(overlayData)
        }

        // 2. Persist threat record into Room Database
        serviceScope.launch {
            appContext?.repository?.recordCallThreat(
                phoneNumber = callerNumber,
                callerTag = "$archetype ($callerNumber)",
                transcript = "Live Call Dialogue: \"$rawSpokenText\" • Analysis: ${analysis.reasoning}",
                isScam = true,
                confidence = analysis.confidence,
                triggerWords = analysis.triggerWords,
                threatCategory = analysis.threatCategory.name,
                actionTaken = recommendedAction
            )
        }

        // 3. Audible Voice Alert via Text-To-Speech (TTS)
        val voiceAlert = when (analysis.threatCategory) {
            com.example.data.model.ThreatCategory.OTP_THEFT ->
                "Warning! Fraud detected on call. Caller is asking for your secret OTP or verification code. Do not share any code and disconnect immediately."
            com.example.data.model.ThreatCategory.URGENT_FINE ->
                "Warning! Digital arrest and police impersonation scam detected. Real police never conducts arrests over the phone. Hang up now."
            com.example.data.model.ThreatCategory.APK_SIDELOAD ->
                "Warning! Caller is directing you to install an unknown APK or remote support tool. Hang up immediately."
            else ->
                "Warning! Social engineering scam detected. Do not share any personal info or money."
        }
        appContext?.ttsManager?.speakCustom(voiceAlert)

        // 4. Update Ongoing Notification with high priority
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val threatNotification = createNotification("🚨 SCAM DETECTED: $archetype • HANG UP NOW")
        notificationManager.notify(NOTIFICATION_ID, threatNotification)
    }

    private fun startMediaProjectionCapture(resultCode: Int, resultData: Intent, callerNumber: String) {
        configureCommunicationAudioRouting()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startDirectCallStreamCapture(callerNumber)
            return
        }

        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

            if (mediaProjection == null) {
                Log.w(TAG, "MediaProjection was null, falling back to direct communication stream.")
                startDirectCallStreamCapture(callerNumber)
                return
            }

            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped.")
                    stopAudioCapture()
                }
            }, null)

            setupAudioPlaybackCapture(mediaProjection!!, callerNumber)
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection initialization failed: ${e.message}", e)
            startDirectCallStreamCapture(callerNumber)
        }
    }

    private fun configureCommunicationAudioRouting() {
        try {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let { am ->
                previousAudioMode = am.mode
                am.mode = AudioManager.MODE_IN_COMMUNICATION

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val availableDevices = am.availableCommunicationDevices
                    val targetDevice = availableDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        ?: availableDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                        ?: availableDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET }
                        ?: availableDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                        ?: availableDevices.firstOrNull()

                    if (targetDevice != null) {
                        val success = am.setCommunicationDevice(targetDevice)
                        if (success) {
                            activeCommunicationDevice = targetDevice
                            Log.i(TAG, "Configured AudioManager communication device: ${targetDevice.productName} (type: ${targetDevice.type})")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to configure communication audio routing: ${e.message}")
        }
    }

    private fun resetCommunicationAudioRouting() {
        try {
            audioManager?.let { am ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.clearCommunicationDevice()
                }
                am.mode = previousAudioMode
            }
            activeCommunicationDevice = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reset communication audio routing: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun setupAudioPlaybackCapture(projection: MediaProjection, callerNumber: String) {
        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ).coerceAtLeast(4096)

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .build()

            audioRecord = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(minBufferSize * 2)
                .setAudioPlaybackCaptureConfig(config)
                .build()

            audioRecord?.startRecording()
            Log.i(TAG, "AudioPlaybackCapture (MediaProjection raw stream) active.")
            startAnalysisLoop(minBufferSize, callerNumber)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioPlaybackCapture: ${e.message}", e)
            startDirectCallStreamCapture(callerNumber)
        }
    }

    private fun startDirectCallStreamCapture(callerNumber: String) {
        configureCommunicationAudioRouting()
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ).coerceAtLeast(4096)

            val audioSources = listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
            )

            var initializedRecord: AudioRecord? = null
            for (source in audioSources) {
                try {
                    val record = AudioRecord(
                        source,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        minBufferSize * 2
                    )
                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        initializedRecord = record
                        Log.i(TAG, "AudioRecord initialized using audio source: $source")
                        break
                    } else {
                        record.release()
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Could not initialize AudioRecord for source $source: ${e.message}")
                }
            }

            audioRecord = initializedRecord

            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.startRecording()
                Log.i(TAG, "Direct call audio stream active.")
                startAnalysisLoop(minBufferSize, callerNumber)
            } else {
                Log.e(TAG, "Failed to initialize AudioRecord for communication stream.")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "RECORD_AUDIO permission missing: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting communication stream: ${e.message}", e)
        }
    }

    private fun startAnalysisLoop(bufferSize: Int, callerNumber: String) {
        captureJob = serviceScope.launch {
            val buffer = ShortArray(bufferSize / 2)
            var consecutiveThreatCount = 0

            while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val readSamples = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readSamples > 0) {
                    val frame = buffer.copyOf(readSamples)
                    val result = tfLiteClassifier.classifyAudioBuffer(frame)

                    _latestAudioClassification.emit(result)

                    if (result.isScam) {
                        consecutiveThreatCount++
                        if (consecutiveThreatCount >= 2) {
                            handleAcousticThreatDetected(callerNumber, result)
                        }
                    } else {
                        consecutiveThreatCount = (consecutiveThreatCount - 1).coerceAtLeast(0)
                    }
                }
            }
        }
    }

    private fun handleAcousticThreatDetected(
        callerNumber: String,
        result: AudioScamTfLiteClassifier.AudioClassificationResult
    ) {
        val now = System.currentTimeMillis()
        if (now - lastScamAlertTimestamp < 7000) return
        lastScamAlertTimestamp = now

        val appContext = applicationContext as? RakshakApplication

        val overlayData = FraudAlertOverlayManager.OverlayData(
            callerNumber = callerNumber,
            archetype = result.archetype,
            confidence = result.confidence,
            stressLevel = result.stressLevel,
            reasoning = result.reasoning,
            recommendedAction = result.recommendedAction,
            acousticMarkers = result.acousticMarkers
        )
        mainHandler.post {
            FraudAlertOverlayManager.getInstance(applicationContext).showFraudAlertOverlay(overlayData)
        }

        serviceScope.launch {
            appContext?.repository?.recordCallThreat(
                phoneNumber = callerNumber,
                callerTag = "${result.archetype} ($callerNumber)",
                transcript = "TensorFlow Lite Audio Model Intercept: ${result.reasoning}",
                isScam = true,
                confidence = result.confidence,
                triggerWords = result.acousticMarkers,
                threatCategory = "URGENT_FINE",
                actionTaken = result.recommendedAction
            )
        }

        appContext?.ttsManager?.speakCustom(
            "Warning! Potential fraud detected by acoustic sentry. ${result.archetype}. Do not transfer money."
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val threatNotification = createNotification("🚨 SCAM THREAT: ${result.archetype} • ${(result.confidence * 100).toInt()}% CONFIDENCE")
        notificationManager.notify(NOTIFICATION_ID, threatNotification)
    }

    private fun stopAudioCapture() {
        _isInterceptorActive.value = false
        resetCommunicationAudioRouting()
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying speech recognizer: ${e.message}")
            }
            FraudAlertOverlayManager.getInstance(applicationContext).dismissOverlay()
        }
        captureJob?.cancel()
        captureJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing audioRecord: ${e.message}")
        }
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun createNotification(statusText: String): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rakshak Call Sentry",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous real-time call speech & scam detection sentry"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("NAVIGATE_TO", "CALL_GUARDIAN")
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rakshak Call Guardian Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopAudioCapture()
        tfLiteClassifier.close()
        super.onDestroy()
    }
}
