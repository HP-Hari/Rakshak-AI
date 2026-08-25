package com.example.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.data.model.ThreatCategory
import com.example.service.TtsAnnouncementService
import java.util.Locale

/**
 * LocalTtsManager
 *
 * High-clarity On-Device Voice & Soundbox Engine for Rakshak AI & Smart Vyapar.
 * Provides authentic Soundbox voice announcements:
 * "Received [Amount] Rupees from [Payer Name] on [Bank/UPI]."
 * and security alerts during live phone calls.
 */
class LocalTtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isReady = false
    private val pendingSpeechQueue = mutableListOf<Pair<String, Int>>()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("en", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.US
            }
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(0.95f)
            isReady = true
            Log.d("LocalTtsManager", "Local Soundbox & Sentry TTS Engine initialized on-device.")

            synchronized(pendingSpeechQueue) {
                for ((text, mode) in pendingSpeechQueue) {
                    tts?.speak(text, mode, null, "RakshakTts_${System.currentTimeMillis()}")
                }
                pendingSpeechQueue.clear()
            }
        } else {
            Log.e("LocalTtsManager", "TTS initialization failed with code: $status")
        }
    }

    /**
     * Soundbox announcement for UPI app notifications (Google Pay, PhonePe, Paytm, BHIM).
     */
    fun speakPaymentReceived(amount: Double, payerName: String, provider: String = "UPI") {
        val cleanPayer = sanitizePayerName(payerName)
        val formattedAmt = formatRupees(amount)
        val cleanBank = provider.trim()
        val isRealBank = cleanBank.isNotBlank() && !cleanBank.equals("Bank", ignoreCase = true) && !cleanBank.equals("UPI", ignoreCase = true)

        val text = when {
            cleanPayer != null && isRealBank -> "Received $formattedAmt from $cleanPayer on $cleanBank."
            cleanPayer != null -> "Received $formattedAmt from $cleanPayer."
            isRealBank -> "Received $formattedAmt on $cleanBank."
            else -> "Received $formattedAmt."
        }
        speakInternal(text, TextToSpeech.QUEUE_FLUSH)

        try {
            TtsAnnouncementService.announcePayment(context, amount, cleanPayer ?: "", provider)
        } catch (e: Exception) {
            Log.w("LocalTtsManager", "Background fallback notice: ${e.message}")
        }
    }

    /**
     * Soundbox announcement for Bank & SMS credit transactions.
     */
    fun speakBankPaymentAnnouncement(amount: Double, payerName: String, bankName: String) {
        val cleanPayer = sanitizePayerName(payerName)
        val formattedAmt = formatRupees(amount)
        val cleanBank = bankName.trim()
        val isRealBank = cleanBank.isNotBlank() && !cleanBank.equals("Bank", ignoreCase = true) && !cleanBank.equals("UPI", ignoreCase = true)

        val text = when {
            cleanPayer != null && isRealBank -> "Received $formattedAmt from $cleanPayer on $cleanBank."
            cleanPayer != null -> "Received $formattedAmt from $cleanPayer."
            isRealBank -> "Received $formattedAmt on $cleanBank."
            else -> "Received $formattedAmt."
        }
        speakInternal(text, TextToSpeech.QUEUE_FLUSH)

        try {
            TtsAnnouncementService.announcePayment(context, amount, cleanPayer ?: "", bankName)
        } catch (e: Exception) {
            Log.w("LocalTtsManager", "Background TTS service trigger fallback: ${e.message}")
        }
    }

    private fun formatRupees(amount: Double): String {
        return if (amount == 1.0) {
            "1 Rupee"
        } else if (amount % 1.0 == 0.0) {
            "${amount.toInt()} Rupees"
        } else {
            String.format(Locale.US, "%.2f Rupees", amount)
        }
    }

    private fun sanitizePayerName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val trimmed = name.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
        val invalidTokens = listOf(
            "customer", "upi customer", "unknown", "bank", "kotak", "mahindra", "sbi", "hdfc",
            "icici", "axis", "pnb", "bob", "canara", "union", "idfc", "indusind", "paytm", "gpay",
            "phonepe", "bhim", "user", "vpa", "account", "transfer", "credited", "received", "none"
        )
        if (invalidTokens.any { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }) {
            return null
        }
        if (lower.contains("kotak") || lower.contains("bank") || lower.contains("account")) {
            return null
        }
        return if (trimmed.length >= 2) trimmed else null
    }

    fun speakScamWarning() {
        val alert = "Warning! This caller is requesting sensitive information. Hang up immediately."
        speakInternal(alert, TextToSpeech.QUEUE_FLUSH)
    }

    fun speakCallInterruptionAlert(category: ThreatCategory, confidence: Float) {
        val alert = "Security Alert! ${category.displayName} detected with ${(confidence * 100).toInt()}% confidence. Disconnect immediately."
        speakInternal(alert, TextToSpeech.QUEUE_FLUSH)
    }

    fun speakImportantNotice(notice: String) {
        speakInternal("Important security alert: $notice", TextToSpeech.QUEUE_FLUSH)
    }

    fun speakSpoofAlert(amount: Double) {
        val formattedAmt = if (amount % 1.0 == 0.0) "${amount.toInt()} Rupees" else String.format(Locale.US, "%.2f Rupees", amount)
        val alert = "Security Alert! Fake audio payment detected for $formattedAmt. No bank notification received."
        speakInternal(alert, TextToSpeech.QUEUE_FLUSH)
    }

    fun speakCustom(message: String) {
        speakInternal(message, TextToSpeech.QUEUE_ADD)
    }

    private fun speakInternal(text: String, queueMode: Int) {
        if (isReady && tts != null) {
            tts?.speak(text, queueMode, null, "RakshakTts_${System.currentTimeMillis()}")
            Log.i("LocalTtsManager", "🔊 Soundbox Spoken: \"$text\"")
        } else {
            synchronized(pendingSpeechQueue) {
                pendingSpeechQueue.add(text to queueMode)
            }
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("LocalTtsManager", "Error shutting down TTS", e)
        }
    }
}
