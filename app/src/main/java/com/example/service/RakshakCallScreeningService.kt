package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.annotation.RequiresApi
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

data class IncomingCallEvent(
    val phoneNumber: String,
    val isUnknownCaller: Boolean,
    val callerName: String? = null,
    val riskScore: Float = 0f,
    val isAutoBlocked: Boolean = false,
    val callerProfile: String = "Incoming Caller",
    val reasoning: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@RequiresApi(Build.VERSION_CODES.Q)
class RakshakCallScreeningService : CallScreeningService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val geminiService by lazy { GeminiAiService() }

    companion object {
        const val CHANNEL_ID_CALL_GUARDIAN = "rakshak_call_guardian_channel"
        const val NOTIFICATION_ID_CALL_SCREEN = 1001

        private val _callEvents = MutableSharedFlow<IncomingCallEvent>(extraBufferCapacity = 20)
        val callEvents: SharedFlow<IncomingCallEvent> = _callEvents.asSharedFlow()

        // Known high-risk scam & spoofing prefixes (Cyber crime syndicate origins)
        private val HIGH_RISK_PREFIXES = listOf(
            "+92",   // Pakistan extortion / lottery scams
            "+880",  // Bangladesh spoofing
            "+234",  // Nigeria advance fee
            "+4470", // UK premium redirect
            "+1876", // Jamaica lottery scam
            "+1800", // Impersonation toll-free
            "140",   // Unregistered telemarketing spam
            "160"    // Financial spam calls
        )

        fun simulateIncomingCall(
            phoneNumber: String,
            isUnknown: Boolean,
            callerName: String? = null,
            riskScore: Float = 0.9f,
            callerProfile: String = "Incoming Call",
            reasoning: String = ""
        ) {
            _callEvents.tryEmit(
                IncomingCallEvent(
                    phoneNumber = phoneNumber,
                    isUnknownCaller = isUnknown,
                    callerName = callerName,
                    riskScore = riskScore,
                    isAutoBlocked = riskScore >= 0.85f,
                    callerProfile = callerProfile,
                    reasoning = reasoning
                )
            )
        }
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle?.schemeSpecificPart ?: "Unknown Caller"
        val callerName = callDetails.callerDisplayName?.takeIf { it.isNotBlank() }
            ?: (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) callDetails.contactDisplayName?.takeIf { it.isNotBlank() } else null)

        val isKnownContact = !callerName.isNullOrBlank()
        val isIncoming = callDetails.callDirection == Call.Details.DIRECTION_INCOMING

        Log.d("CallScreening", "Live screening call: $handle, Name: $callerName, IsContact: $isKnownContact, IsIncoming: $isIncoming")

        // Clean phone number for analysis
        val cleanNumber = handle.replace(" ", "").replace("-", "")
        val isHighRiskPrefix = HIGH_RISK_PREFIXES.any { cleanNumber.startsWith(it) || cleanNumber.startsWith("+$it") }

        // Initial risk calculation for immediate telecom response
        val initialRisk = when {
            isKnownContact -> 0.05f
            isHighRiskPrefix -> 0.95f
            cleanNumber.length < 10 && cleanNumber.all { it.isDigit() || it == '+' } -> 0.88f
            else -> 0.25f // Normal unknown mobile number default to moderate review
        }

        val shouldAutoBlock = isHighRiskPrefix && !isKnownContact

        val event = IncomingCallEvent(
            phoneNumber = handle,
            isUnknownCaller = !isKnownContact,
            callerName = callerName,
            riskScore = initialRisk,
            isAutoBlocked = shouldAutoBlock,
            callerProfile = if (isKnownContact) "Known Saved Contact" else if (isHighRiskPrefix) "High-Risk International Origin" else "Domestic Cellular Mobile",
            reasoning = if (isKnownContact) "Contact exists in user's phonebook." else if (isHighRiskPrefix) "Matches international cyber scam prefix blocklist." else "Normal incoming mobile call."
        )

        _callEvents.tryEmit(event)

        // Perform in-depth Gemini AI analysis asynchronously & log to Room DB
        serviceScope.launch {
            handleAiCallAnalysisAndLogging(event, handle, callerName, isKnownContact)
        }

        // Build Telecom Call Response
        val response = if (shouldAutoBlock) {
            Log.w("CallScreening", "AUTO-BLOCKING scam call from $handle (Risk: $initialRisk)")
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSilenceCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
        } else {
            // Allow normal mobile numbers to ring while Call Guardian AI passive listener inspects
            CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
        }

        respondToCall(callDetails, response)
    }

    private suspend fun handleAiCallAnalysisAndLogging(
        event: IncomingCallEvent,
        phoneNumber: String,
        callerName: String?,
        isKnownContact: Boolean
    ) {
        try {
            val app = applicationContext as? RakshakApplication ?: return

            // Run Gemini AI Analysis on the phone number / caller
            val aiResult = geminiService.analyzeCallOrNumberWithAi(phoneNumber)

            val isScam = aiResult.isScam || event.isAutoBlocked
            val riskScore = if (isKnownContact) 0.05f else aiResult.confidence

            val triggers = if (aiResult.redFlags.isNotEmpty()) {
                aiResult.redFlags
            } else if (isScam) {
                listOf("unknown_origin", "suspicious_number")
            } else {
                listOf("verified_domestic")
            }

            // Record into Room Database
            app.repository.recordCallThreat(
                phoneNumber = phoneNumber,
                callerTag = callerName ?: "${aiResult.callerProfile} ($phoneNumber)",
                transcript = if (isKnownContact) "Incoming call from saved contact." else "${aiResult.reasoning} Action: ${aiResult.suggestedAction}",
                isScam = isScam,
                confidence = riskScore,
                triggerWords = triggers,
                threatCategory = if (isScam) ThreatCategory.URGENT_FINE.name else ThreatCategory.SAFE_CALL.name,
                actionTaken = if (event.isAutoBlocked) "AUTO-BLOCKED BY TELECOM SCREENING" else if (isScam) "FLAGGED AS SUSPICIOUS BY AI" else "VERIFIED NORMAL CALL"
            )

            // Notify user
            if (event.isAutoBlocked) {
                showScreeningNotification(
                    title = "🚨 Rakshak Auto-Blocked Scam Call",
                    message = "Blocked call from $phoneNumber (${(riskScore * 100).toInt()}% risk ${aiResult.callerProfile})",
                    isHighPriority = true
                )
                app.ttsManager.speakCustom("Rakshak Call Guardian blocked a high risk fraud call from $phoneNumber")
            } else if (isScam) {
                showScreeningNotification(
                    title = "⚠️ Suspicious Call Detected",
                    message = "$phoneNumber flagged by Gemini AI: ${aiResult.reasoning}",
                    isHighPriority = true
                )
            } else if (event.isUnknownCaller) {
                showScreeningNotification(
                    title = "🛡️ Call Guardian: Normal Call Verified",
                    message = "Incoming call from $phoneNumber analyzed: ${aiResult.callerProfile}",
                    isHighPriority = false
                )
            }
        } catch (e: Exception) {
            Log.e("CallScreening", "Error in AI call analysis & logging: ${e.message}")
        }
    }

    private fun showScreeningNotification(title: String, message: String, isHighPriority: Boolean) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID_CALL_GUARDIAN,
                    "Rakshak Call Guardian",
                    if (isHighPriority) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Real-time call screening and scam interception alerts"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val launchIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("NAVIGATE_TO", "CALL_GUARDIAN")
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID_CALL_GUARDIAN)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(if (isHighPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(NOTIFICATION_ID_CALL_SCREEN, notification)
        } catch (e: Exception) {
            Log.e("CallScreening", "Failed to show screening notification: ${e.message}")
        }
    }
}
