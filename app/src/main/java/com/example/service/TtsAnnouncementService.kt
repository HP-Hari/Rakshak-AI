package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Text-to-Speech (TTS) Background Service for Rakshak AI & Smart Vyapar.
 * Immediately and audibly announces transaction amounts, sender/payer names,
 * and security alerts even when the app is in the background.
 */
class TtsAnnouncementService : Service(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val pendingUtterances = mutableListOf<String>()
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    companion object {
        const val TAG = "TtsAnnouncementService"
        const val ACTION_ANNOUNCE_PAYMENT = "com.example.action.ANNOUNCE_PAYMENT"
        const val ACTION_SPEAK_TEXT = "com.example.action.SPEAK_TEXT"
        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_PAYER_NAME = "extra_payer_name"
        const val EXTRA_BANK_NAME = "extra_bank_name"
        const val EXTRA_TEXT = "extra_text"

        fun announcePayment(context: Context, amount: Double, payerName: String, bankName: String) {
            val intent = Intent(context, TtsAnnouncementService::class.java).apply {
                action = ACTION_ANNOUNCE_PAYMENT
                putExtra(EXTRA_AMOUNT, amount)
                putExtra(EXTRA_PAYER_NAME, payerName)
                putExtra(EXTRA_BANK_NAME, bankName)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start TTS service for payment: ${e.message}")
            }
        }

        fun speak(context: Context, text: String) {
            val intent = Intent(context, TtsAnnouncementService::class.java).apply {
                action = ACTION_SPEAK_TEXT
                putExtra(EXTRA_TEXT, text)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start TTS service for speech: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        tts = TextToSpeech(applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val inLocale = Locale("en", "IN")
            val result = tts?.setLanguage(inLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.US
            }
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(0.95f)

            // Listen for speech completion to release audio focus and self-stop if idle
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS utterance started: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS utterance completed: $utteranceId")
                    releaseAudioFocus()
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS utterance error: $utteranceId")
                    releaseAudioFocus()
                }
            })

            isTtsReady = true
            Log.d(TAG, "TtsAnnouncementService initialized. Flushing ${pendingUtterances.size} queued utterances.")

            synchronized(pendingUtterances) {
                for (text in pendingUtterances) {
                    speakInternal(text)
                }
                pendingUtterances.clear()
            }
        } else {
            Log.e(TAG, "TtsAnnouncementService initialization failed with status $status")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_ANNOUNCE_PAYMENT -> {
                    val amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
                    val payerName = intent.getStringExtra(EXTRA_PAYER_NAME) ?: ""
                    val bankName = intent.getStringExtra(EXTRA_BANK_NAME) ?: ""

                    val trimmed = payerName.trim()
                    val lower = trimmed.lowercase(Locale.ROOT)
                    val invalidTokens = listOf(
                        "customer", "upi customer", "unknown", "bank", "kotak", "mahindra", "sbi", "hdfc",
                        "icici", "axis", "pnb", "bob", "canara", "union", "idfc", "indusind", "paytm", "gpay",
                        "phonepe", "bhim", "user", "vpa", "account", "transfer", "credited", "received", "none", "direct credit"
                    )
                    val isRealName = trimmed.isNotBlank() &&
                            !invalidTokens.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") } &&
                            !lower.contains("kotak") && !lower.contains("bank")

                    val cleanBank = bankName.trim()
                    val isRealBank = cleanBank.isNotBlank() && !cleanBank.equals("Bank", ignoreCase = true) && !cleanBank.equals("UPI", ignoreCase = true)

                    val formattedAmount = if (amount == 1.0) {
                        "1 Rupee"
                    } else if (amount % 1.0 == 0.0) {
                        "${amount.toInt()} Rupees"
                    } else {
                        String.format(Locale.US, "%.2f Rupees", amount)
                    }

                    val textToSpeak = when {
                        isRealName && isRealBank -> "Received $formattedAmount from $trimmed on $cleanBank."
                        isRealName -> "Received $formattedAmount from $trimmed."
                        isRealBank -> "Received $formattedAmount on $cleanBank."
                        else -> "Received $formattedAmount."
                    }

                    queueOrSpeak(textToSpeak)
                }
                ACTION_SPEAK_TEXT -> {
                    val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
                    if (text.isNotBlank()) {
                        queueOrSpeak(text)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun queueOrSpeak(text: String) {
        if (isTtsReady && tts != null) {
            speakInternal(text)
        } else {
            synchronized(pendingUtterances) {
                pendingUtterances.add(text)
            }
        }
    }

    private fun speakInternal(text: String) {
        try {
            requestAudioFocus()
            val utteranceId = "TtsSvc_${System.currentTimeMillis()}"
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            Log.i(TAG, "Audibly announcing: \"$text\"")
        } catch (e: Exception) {
            Log.e(TAG, "Error in speakInternal: ${e.message}", e)
            releaseAudioFocus()
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                focusRequest?.let { audioManager?.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_NOTIFICATION,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request audio focus: ${e.message}")
        }
    }

    private fun releaseAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release audio focus: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            tts?.stop()
            tts?.shutdown()
            releaseAudioFocus()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}")
        }
        super.onDestroy()
    }
}
