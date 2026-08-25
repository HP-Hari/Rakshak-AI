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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RakshakPhoneStateReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val CHANNEL_ID_CALL_STATE = "rakshak_call_state_channel"
        const val NOTIFICATION_ID_CALL_STATE = 3003

        private val HIGH_RISK_SYNDICATE_PREFIXES = listOf(
            "+92",   // Offshore cyber extortion
            "+880",  // Bangladesh spoofing
            "+234",  // Advance fee fraud
            "+4470", // UK premium redirect
            "+1876", // Lottery scam
            "140",   // Telemarketing spam
            "160"    // Financial spam
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        try {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

            Log.d("RakshakPhoneStateReceiver", "Phone state changed: $state, incomingNumber: $incomingNumber")

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    if (incomingNumber.isNotBlank()) {
                        val cleanNumber = incomingNumber.replace(" ", "").replace("-", "")
                        val isHighRisk = HIGH_RISK_SYNDICATE_PREFIXES.any { cleanNumber.startsWith(it) || cleanNumber.startsWith("+$it") }

                        val appContext = context.applicationContext as? RakshakApplication ?: return

                        receiverScope.launch {
                            if (isHighRisk) {
                                Log.w("RakshakPhoneStateReceiver", "Incoming call from high-risk origin: $incomingNumber")

                                // Record threat to Room DB
                                appContext.repository.recordCallThreat(
                                    phoneNumber = incomingNumber,
                                    callerTag = "High-Risk Origin ($incomingNumber)",
                                    transcript = "Auto-intercepted incoming call matching international cyber extortion prefix blocklist.",
                                    isScam = true,
                                    confidence = 0.95f,
                                    triggerWords = listOf("syndicate_prefix", "international_spoofing"),
                                    threatCategory = ThreatCategory.URGENT_FINE.name,
                                    actionTaken = "HIGH-RISK WARNING DISPLAYED"
                                )

                                showHighRiskCallAlert(
                                    context = appContext,
                                    phoneNumber = incomingNumber
                                )

                                appContext.ttsManager.speakCustom(
                                    "Warning! Incoming call from high risk offshore number $incomingNumber. Exercise caution."
                                )
                            }
                        }
                    }
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    Log.d("RakshakPhoneStateReceiver", "Call answered - Live call sentry active.")
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    Log.d("RakshakPhoneStateReceiver", "Call ended - Device idle.")
                }
            }
        } catch (e: Exception) {
            Log.e("RakshakPhoneStateReceiver", "Error processing phone state broadcast: ${e.message}", e)
        }
    }

    private fun showHighRiskCallAlert(context: Context, phoneNumber: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID_CALL_STATE,
                    "Rakshak Call Security Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent alerts for high-risk unknown incoming calls"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("NAVIGATE_TO", "CALL_GUARDIAN")
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_CALL_STATE)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("🚨 High-Risk Call Detected ($phoneNumber)")
                .setContentText("Origin matches known international cyber extortion syndicate.")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Incoming call from: $phoneNumber\n\n⚠️ Origin matches known international cyber fraud syndicate blocklist. Do not share OTP or personal bank details."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(NOTIFICATION_ID_CALL_STATE, notification)
        } catch (e: Exception) {
            Log.e("RakshakPhoneStateReceiver", "Failed to show call alert notification: ${e.message}")
        }
    }
}
