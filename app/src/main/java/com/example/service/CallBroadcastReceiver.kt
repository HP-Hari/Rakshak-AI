package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.RakshakApplication
import com.example.data.model.ThreatCategory
import com.example.engine.GeminiAiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * CallBroadcastReceiver
 * Listens for incoming phone calls in real time and implements multi-tier fraud detection logic
 * (International Syndicate blocklist, TRAI telemarketing prefixes, spoof pattern heuristics, and Gemini AI).
 */
class CallBroadcastReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO)
    private val geminiService by lazy { GeminiAiService() }

    data class RealTimeCallFraudResult(
        val isFraudulent: Boolean,
        val riskLevel: String, // "CRITICAL", "HIGH", "SUSPICIOUS", "SAFE"
        val confidence: Float,
        val callerProfile: String,
        val threatCategory: String,
        val triggerFlags: List<String>,
        val reasoning: String,
        val recommendedAction: String
    )

    companion object {
        private const val TAG = "CallBroadcastReceiver"
        const val CHANNEL_ID_FRAUD_CALL = "rakshak_fraud_call_channel"
        const val NOTIFICATION_ID_FRAUD_CALL = 4004

        private val _fraudCallEvents = MutableSharedFlow<Pair<String, RealTimeCallFraudResult>>(extraBufferCapacity = 20)
        val fraudCallEvents: SharedFlow<Pair<String, RealTimeCallFraudResult>> = _fraudCallEvents.asSharedFlow()

        // 1. High-Risk International Extortion & Spoofing Prefixes (Digital Arrest, Wangiri, Advance Fee)
        private val HIGH_RISK_SYNDICATE_PREFIXES = listOf(
            "+92",   // Pakistan - Cyber extortion / Digital arrest impersonation
            "+880",  // Bangladesh - Caller ID spoofing & cross-border extortion
            "+234",  // Nigeria - Advance fee & romantic investment fraud
            "+4470", // UK - Premium rate redirection scam
            "+1876", // Jamaica / Caribbean - Lottery & sweepstake prize scams
            "+371",  // Latvia - Wangiri one-ring callback traps
            "+216",  // Tunisia - International wangiri callback
            "+212",  // Morocco - Wangiri toll redirect
            "+225",  // Ivory Coast - Blackmail & extortion syndicates
            "+232",  // Sierra Leone - Callback fraud
            "+247"   // Ascension Island - High-cost toll spoofing
        )

        // 2. Unregistered Telemarketing & Predatory Loan Series (TRAI 140 / 160 range)
        private val SPAM_TELEMARKETING_PREFIXES = listOf(
            "140",   // TRAI Telemarketing series
            "160",   // TRAI Financial marketing series
            "+91140",
            "+91160"
        )

        // 3. Known Fake Support / Toll-Free Impersonation Spoof Formats
        private val SPOOFED_IMPERSONATION_PREFIXES = listOf(
            "+1800",
            "+1860"
        )

        private var lastRingingNumber: String = ""

        // 4. Repeated / Degenerate Pattern Numbers (e.g. 9999999999, 1234567890)
        private val REPEATED_DIGIT_PATTERNS = listOf(
            "0000000000", "1111111111", "2222222222", "3333333333",
            "4444444444", "5555555555", "6666666666", "7777777777",
            "8888888888", "9999999999", "1234567890", "9876543210"
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        try {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                ?: intent.extras?.getString("incoming_number")
                ?: ""

            Log.d(TAG, "Phone State Change: $state | Incoming Number: '$incomingNumber'")

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    if (incomingNumber.isNotBlank()) {
                        lastRingingNumber = incomingNumber
                        handleIncomingRingingCall(context, incomingNumber)
                    }
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    val activeNumber = incomingNumber.ifBlank { lastRingingNumber }.ifBlank { "Active Call" }
                    Log.d(TAG, "Call answered / in-progress with $activeNumber. Starting real-time CallAudioInterceptorService...")

                    try {
                        val interceptorIntent = Intent(context, CallAudioInterceptorService::class.java).apply {
                            this.action = CallAudioInterceptorService.ACTION_START
                            putExtra(CallAudioInterceptorService.EXTRA_CALLER_NUMBER, activeNumber)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(interceptorIntent)
                        } else {
                            context.startService(interceptorIntent)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start CallAudioInterceptorService: ${e.message}", e)
                    }
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    Log.d(TAG, "Call ended / device idle. Stopping CallAudioInterceptorService...")
                    try {
                        val stopIntent = Intent(context, CallAudioInterceptorService::class.java).apply {
                            this.action = CallAudioInterceptorService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to stop CallAudioInterceptorService: ${e.message}", e)
                    }
                    lastRingingNumber = ""
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling phone state broadcast: ${e.message}", e)
        }
    }

    /**
     * Executes real-time heuristics and Gemini AI inspection on incoming numbers.
     */
    private fun handleIncomingRingingCall(context: Context, rawNumber: String) {
        val appContext = context.applicationContext as? RakshakApplication ?: return

        receiverScope.launch {
            val fraudResult = evaluateNumberForFraud(rawNumber)
            _fraudCallEvents.emit(Pair(rawNumber, fraudResult))

            if (fraudResult.isFraudulent) {
                Log.w(TAG, "🚨 FRAUD DETECTED on incoming call: $rawNumber (${fraudResult.callerProfile})")

                // 1. Record call threat in Room Database
                appContext.repository.recordCallThreat(
                    phoneNumber = rawNumber,
                    callerTag = "${fraudResult.callerProfile} ($rawNumber)",
                    transcript = "Real-time incoming call intercept: ${fraudResult.reasoning}",
                    isScam = true,
                    confidence = fraudResult.confidence,
                    triggerWords = fraudResult.triggerFlags,
                    threatCategory = fraudResult.threatCategory,
                    actionTaken = fraudResult.recommendedAction
                )

                // 2. Display High-Priority Heads-Up Security Alert Notification
                showFraudCallAlertNotification(
                    context = appContext,
                    phoneNumber = rawNumber,
                    fraudResult = fraudResult
                )

                // 3. Audio Voice Warning via Text-to-Speech
                appContext.ttsManager.speakCustom(
                    "Warning! Potential fraud call detected from $rawNumber. ${fraudResult.callerProfile}. Do not share OTP or money."
                )
            } else {
                Log.d(TAG, "Incoming call verified as safe / low-risk: $rawNumber")
            }
        }
    }

    /**
     * Real-time multi-layered fraud evaluation logic.
     */
    suspend fun evaluateNumberForFraud(rawNumber: String): RealTimeCallFraudResult {
        val cleanNumber = rawNumber.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        val triggerFlags = mutableListOf<String>()

        // 1. Check International High-Risk Syndicates
        for (prefix in HIGH_RISK_SYNDICATE_PREFIXES) {
            val rawPrefix = prefix.removePrefix("+")
            if (cleanNumber.startsWith(prefix) || cleanNumber.startsWith("+$rawPrefix") || cleanNumber.startsWith(rawPrefix)) {
                triggerFlags.add("international_extortion_prefix ($prefix)")
                return RealTimeCallFraudResult(
                    isFraudulent = true,
                    riskLevel = "CRITICAL",
                    confidence = 0.98f,
                    callerProfile = "High-Risk International Cyber Syndicate ($prefix)",
                    threatCategory = ThreatCategory.URGENT_FINE.name,
                    triggerFlags = triggerFlags,
                    reasoning = "Origin matches known international extortion and Digital Arrest scam prefix blocklist ($prefix).",
                    recommendedAction = "DO NOT ANSWER • HANG UP IMMEDIATELY"
                )
            }
        }

        // 2. Check Telemarketing & Predatory Loan Series (TRAI 140/160 series)
        for (prefix in SPAM_TELEMARKETING_PREFIXES) {
            val rawPrefix = prefix.removePrefix("+")
            if (cleanNumber.startsWith(prefix) || cleanNumber.startsWith("+$rawPrefix") || cleanNumber.startsWith(rawPrefix)) {
                triggerFlags.add("telecom_spam_series ($prefix)")
                return RealTimeCallFraudResult(
                    isFraudulent = true,
                    riskLevel = "HIGH",
                    confidence = 0.92f,
                    callerProfile = "Unregistered Telemarketing / Predatory Loan Spam",
                    threatCategory = ThreatCategory.URGENT_FINE.name,
                    triggerFlags = triggerFlags,
                    reasoning = "Originates from high-volume automated marketing & loan cold-call prefix series ($prefix).",
                    recommendedAction = "SILENCE & BLOCK CALLER"
                )
            }
        }

        // 3. Check Repeated / Fake Pattern Numbers
        val digitsOnly = cleanNumber.filter { it.isDigit() }
        if (REPEATED_DIGIT_PATTERNS.contains(digitsOnly) || (digitsOnly.length >= 10 && digitsOnly.all { it == digitsOnly[0] })) {
            triggerFlags.add("spoofed_repeated_digits")
            return RealTimeCallFraudResult(
                isFraudulent = true,
                riskLevel = "CRITICAL",
                confidence = 0.95f,
                callerProfile = "Caller ID Spoofing / Degenerate Pattern",
                threatCategory = ThreatCategory.APK_SIDELOAD.name,
                triggerFlags = triggerFlags,
                reasoning = "The caller number consists of impossible repeated/sequential digits ($digitsOnly) indicative of automated VoIP spoofing.",
                recommendedAction = "REJECT & REPORT SPOOFED NUMBER"
            )
        }

        // 4. Check Shortcode / Abnormal Length anomalies
        if (digitsOnly.length in 1..5 && !cleanNumber.startsWith("198") && !cleanNumber.startsWith("199")) {
            triggerFlags.add("suspicious_shortcode")
            return RealTimeCallFraudResult(
                isFraudulent = true,
                riskLevel = "SUSPICIOUS",
                confidence = 0.85f,
                callerProfile = "Suspicious Shortcode / Virtual Dialer",
                threatCategory = ThreatCategory.LOTTERY_PRIZE.name,
                triggerFlags = triggerFlags,
                reasoning = "Unregistered shortcode length ($digitsOnly) often utilized in automated toll traps.",
                recommendedAction = "EXERCISE CAUTION"
            )
        }

        // 5. Deep Real-Time Gemini AI Inspection for Edge Cases & Custom Formats
        try {
            val aiAnalysis = geminiService.analyzeCallOrNumberWithAi(
                phoneNumberOrQuery = cleanNumber,
                callContextOrTranscript = "Incoming phone call ringing on device. Number: $cleanNumber"
            )

            if (aiAnalysis.isScam || aiAnalysis.riskLevel in listOf("CRITICAL", "HIGH", "MEDIUM")) {
                triggerFlags.addAll(aiAnalysis.redFlags)
                return RealTimeCallFraudResult(
                    isFraudulent = true,
                    riskLevel = aiAnalysis.riskLevel,
                    confidence = aiAnalysis.confidence,
                    callerProfile = aiAnalysis.callerProfile,
                    threatCategory = aiAnalysis.threatCategory,
                    triggerFlags = triggerFlags.ifEmpty { listOf("gemini_ai_scam_pattern") },
                    reasoning = aiAnalysis.reasoning,
                    recommendedAction = aiAnalysis.suggestedAction
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini AI evaluation exception during live call evaluation: ${e.message}")
        }

        // Verified safe or standard mobile caller
        return RealTimeCallFraudResult(
            isFraudulent = false,
            riskLevel = "SAFE",
            confidence = 0.99f,
            callerProfile = "Standard Mobile / Landline Contact",
            threatCategory = ThreatCategory.SAFE_CALL.name,
            triggerFlags = emptyList(),
            reasoning = "Number format is standard and matches no known cyber syndicates or spam patterns.",
            recommendedAction = "SAFE TO ANSWER"
        )
    }

    /**
     * Posts a High-Priority Security Notice notification with deep actions.
     */
    private fun showFraudCallAlertNotification(
        context: Context,
        phoneNumber: String,
        fraudResult: RealTimeCallFraudResult
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID_FRAUD_CALL,
                    "Rakshak Fraud Call Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent high-priority alerts for incoming fraudulent or spoofed calls"
                    enableVibration(true)
                    enableLights(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("NAVIGATE_TO", "CALL_GUARDIAN")
                putExtra("ALERT_PHONE_NUMBER", phoneNumber)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val bigText = """
                🚨 Caller Profile: ${fraudResult.callerProfile}
                ⚠️ Risk Level: ${fraudResult.riskLevel} (${(fraudResult.confidence * 100).toInt()}% Confidence)
                
                Reason: ${fraudResult.reasoning}
                
                👉 Action: ${fraudResult.recommendedAction}
                Never share OTP, passwords, or install remote access APKs.
            """.trimIndent()

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_FRAUD_CALL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("🚨 FRAUD CALL DETECTED: $phoneNumber")
                .setContentText("${fraudResult.callerProfile} • ${fraudResult.recommendedAction}")
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(NOTIFICATION_ID_FRAUD_CALL, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display fraud call notification: ${e.message}")
        }
    }
}
