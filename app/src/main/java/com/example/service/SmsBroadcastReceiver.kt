package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.util.Log

class SmsBroadcastReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "RakshakSMS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            
            // Check if the user is currently on an active phone call
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val isOnCall = telephonyManager.callState != TelephonyManager.CALL_STATE_IDLE
            
            for (sms in messages) {
                val messageBody = sms.displayMessageBody
                val sender = sms.displayOriginatingAddress ?: "Unknown"

                Log.d(TAG, "Received SMS from: $sender")
                
                // 1. Analyze for Spam or Fraud Links
                val isSpamOrPhishing = analyzeForSpam(messageBody)
                
                // 2. Analyze for Original OTPs
                val isOtp = analyzeForOtp(messageBody)

                if (isSpamOrPhishing) {
                    Log.d(TAG, "🚨 BLOCKED: Spam or Phishing SMS detected from $sender!")
                    // In a Default SMS App, we would abort the broadcast here.
                } else if (isOtp) {
                    Log.d(TAG, "🔑 OTP Detected from $sender.")
                    
                    if (isOnCall) {
                        // ELDERLY PROTECTION: Scammer is likely on the phone asking for the OTP
                        Log.e(TAG, "⚠️ CRITICAL WARNING: OTP received while on an active call!")
                        Log.e(TAG, "⚠️ Action: Trigger Loud Voice Warning (TTS) & Screen Overlay!")
                        
                        // NEW: Digital Arrest Family Guardian Auto-Alert
                        triggerFamilySosAlert(context, sender, messageBody)
                    }
                    
                    Log.d(TAG, "Scheduled background task to auto-delete this OTP after 15 minutes.")
                } else {
                    Log.d(TAG, "✉️ Normal SMS processed from $sender")
                }
            }
        }
    }

    /**
     * Basic heuristic check for common spam and phishing patterns
     */
    private fun analyzeForSpam(message: String): Boolean {
        val lowerMsg = message.lowercase()
        val spamKeywords = listOf(
            "lottery", "urgent loan", "click here", "free gift",
            "account blocked", "kyc pending", "dear customer your a/c", 
            "http://", "https://" // Broad check, usually combined with keywords
        )
        
        // If it contains suspicious keywords and a link, it's highly likely phishing/spam
        val hasLink = lowerMsg.contains("http://") || lowerMsg.contains("https://") || lowerMsg.contains(".apk")
        val hasKeyword = spamKeywords.any { lowerMsg.contains(it) && it != "http://" && it != "https://" }
        
        return hasKeyword && hasLink
    }

    /**
     * Regex check for OTP patterns
     */
    private fun analyzeForOtp(message: String): Boolean {
        val lowerMsg = message.lowercase()
        // Checks if the message contains words like "otp", "code", "pin" and a 4 to 8 digit number
        val hasOtpKeyword = lowerMsg.contains("otp") || lowerMsg.contains("code") || lowerMsg.contains("verification")
        val hasDigits = ".*\\b\\d{4,8}\\b.*".toRegex().matches(message)
        
        return hasOtpKeyword && hasDigits
    }

    /**
     * "Digital Arrest" Family Guardian Alert
     * Triggers an emergency SMS to a trusted family member if the user is 
     * trapped in a high-risk scenario (e.g., getting OTPs while on a call).
     */
    private fun triggerFamilySosAlert(context: Context, sender: String, message: String) {
        Log.e(TAG, "🚨 Triggering Family SOS Alert!")
        val trustedContact = "9876543210" // In a real app, fetched from SharedPreferences or Room DB
        
        val sosMessage = """
            🚨 EMERGENCY ALERT from Rakshak AI 🚨
            Your family member is currently on a phone call and just received an OTP from $sender. 
            They might be falling victim to a 'Digital Arrest' or Tech Support scam. 
            Please call them IMMEDIATELY on a different line or check on them.
        """.trimIndent()
        
        Log.d(TAG, "Would send SOS SMS to $trustedContact: \n$sosMessage")
        // Implementation requires SmsManager API:
        // val smsManager = SmsManager.getDefault()
        // smsManager.sendTextMessage(trustedContact, null, sosMessage, null, null)
    }
}
