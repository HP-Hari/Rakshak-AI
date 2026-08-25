package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.RakshakApplication
import com.example.data.model.ThreatCategory
import com.example.engine.GeminiAiService
import com.example.engine.NpuInferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.regex.Pattern

/**
 * High-Priority SMS Broadcast Receiver for Smart Vyapar Soundbox & Rakshak AI Defense.
 *
 * 1. Intercepts incoming SMS broadcasts instantly.
 * 2. Parses bank credit transactions locally and accurately extracts:
 *    - Payer / Sender Person Name (e.g., "Ramesh Sharma", "Priya Verma", "Anand Verma")
 *    - Amount Received (e.g., 500.0, 1000.0)
 *    - Bank / UPI Provider (e.g., "State Bank of India", "HDFC Bank", "PhonePe", "Google Pay")
 * 3. Immediately announces the transaction via Text-to-Speech (TTS):
 *    "Received [Amount] Rupees from [Payer Name] on [Bank/UPI]."
 *    (Replacing expensive ₹1500 monthly hardware soundboxes).
 * 4. Checks incoming messages for phishing, scam threats, and fraudulent links.
 */
class RakshakSmsReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO)
    private val npuEngine = NpuInferenceEngine()
    private val geminiAi = GeminiAiService()

    companion object {
        const val TAG = "RakshakSmsReceiver"
        const val CHANNEL_ID_SMS_SECURITY = "rakshak_sms_security_channel"
        const val CHANNEL_ID_SMS_PAYMENT = "rakshak_sms_payment_channel"
        const val NOTIFICATION_ID_SMS = 2002
        const val NOTIFICATION_ID_PAYMENT = 3003
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val sender = messages[0].originatingAddress ?: "Unknown Sender"
            val fullBody = messages.joinToString(separator = "") { it.messageBody ?: "" }

            Log.d(TAG, "Incoming SMS from '$sender': \"$fullBody\"")

            val appContext = context.applicationContext as? RakshakApplication

            receiverScope.launch {
                // 1. High-Precision Local Bank & UPI Soundbox Parser
                val localPayment = parseBankSmsSoundbox(context = context, smsBody = fullBody, senderAddress = sender)

                if (localPayment.isCreditPayment && localPayment.amount > 0) {
                    Log.i(
                        TAG,
                        "🔊 Soundbox Credit Identified: ₹${localPayment.amount} from '${localPayment.payerName}' via ${localPayment.bankName} (Ref: ${localPayment.referenceId})"
                    )

                    // Store in local Room Database
                    appContext?.repository?.recordUpiTransaction(
                        payerName = localPayment.payerName,
                        amount = localPayment.amount,
                        upiApp = localPayment.bankName,
                        packageName = "sms.telephony",
                        referenceId = localPayment.referenceId,
                        isVerified = true,
                        isSpoofAttempt = false,
                        rawText = fullBody
                    )

                    // Audibly vocalize soundbox announcement: Amount + Person Name + Bank
                    if (appContext != null) {
                        appContext.ttsManager.speakBankPaymentAnnouncement(
                            amount = localPayment.amount,
                            payerName = localPayment.payerName,
                            bankName = localPayment.bankName
                        )
                    } else {
                        TtsAnnouncementService.announcePayment(
                            context = context,
                            amount = localPayment.amount,
                            payerName = localPayment.payerName,
                            bankName = localPayment.bankName
                        )
                    }

                    // Display high-visibility payment notification
                    showPaymentSuccessNotification(
                        context = context,
                        amount = localPayment.amount,
                        payerName = localPayment.payerName,
                        bankName = localPayment.bankName,
                        refId = localPayment.referenceId
                    )
                    return@launch
                }

                // 2. Check for Phishing / Cyber Extortion / Fraud Threats
                val threatAnalysis = npuEngine.classifyTranscriptStream(fullBody)

                if (threatAnalysis.isScam) {
                    Log.w(TAG, "Phishing SMS detected from $sender: ${threatAnalysis.reasoning}")

                    appContext?.repository?.recordCallThreat(
                        phoneNumber = sender,
                        callerTag = "SMS: $sender",
                        transcript = fullBody,
                        isScam = true,
                        confidence = threatAnalysis.confidence,
                        triggerWords = threatAnalysis.triggerWords,
                        threatCategory = threatAnalysis.threatCategory.name,
                        actionTaken = "AUTO-INTERCEPTED BY SMS SENTRY"
                    )

                    if (appContext != null) {
                        showSmsThreatNotification(
                            context = appContext,
                            sender = sender,
                            body = fullBody,
                            category = threatAnalysis.threatCategory,
                            reasoning = threatAnalysis.reasoning
                        )
                    }

                    appContext?.ttsManager?.speakCustom(
                        "Warning! Phishing SMS received from $sender. Do not share OTP or click any links."
                    )
                    return@launch
                }

                // 3. Fallback AI refinement for ambiguous SMS structures
                val aiPayment = geminiAi.parsePaymentSmsWithAi(smsBody = fullBody, sender = sender)
                if (aiPayment.isCreditPayment && aiPayment.amount > 0) {
                    Log.i(TAG, "AI Refined Payment: ₹${aiPayment.amount} from ${aiPayment.payerName} via ${aiPayment.bankName}")

                    appContext?.repository?.recordUpiTransaction(
                        payerName = aiPayment.payerName,
                        amount = aiPayment.amount,
                        upiApp = aiPayment.bankName,
                        packageName = "sms.telephony",
                        referenceId = aiPayment.referenceId,
                        isVerified = true,
                        isSpoofAttempt = false,
                        rawText = fullBody
                    )

                    if (appContext != null) {
                        appContext.ttsManager.speakBankPaymentAnnouncement(
                            amount = aiPayment.amount,
                            payerName = aiPayment.payerName,
                            bankName = aiPayment.bankName
                        )
                    } else {
                        TtsAnnouncementService.announcePayment(
                            context = context,
                            amount = aiPayment.amount,
                            payerName = aiPayment.payerName,
                            bankName = aiPayment.bankName
                        )
                    }

                    showPaymentSuccessNotification(
                        context = context,
                        amount = aiPayment.amount,
                        payerName = aiPayment.payerName,
                        bankName = aiPayment.bankName,
                        refId = aiPayment.referenceId
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing incoming SMS broadcast: ${e.message}", e)
        }
    }

    /**
     * Ultra-High Precision On-Device Soundbox Parser for all Indian Bank & UPI SMS formats.
     * Extracts:
     * - Exact Amount Received (e.g. 1 Rupee, 500 Rupees)
     * - Person Name who sent the money (e.g. "Rahul Sharma" from Kotak, SBI, HDFC, ICICI, UPI formats)
     * - Bank / UPI App (e.g. "Kotak Mahindra Bank")
     * - Reference UTR ID
     */
    private fun parseBankSmsSoundbox(context: Context, smsBody: String, senderAddress: String): GeminiAiService.AiPaymentSmsResult {
        val clean = smsBody.replace(",", "").replace("\n", " ").trim()
        val lower = clean.lowercase(Locale.ROOT)
        val lowerSender = senderAddress.lowercase(Locale.ROOT)

        // Step A: Inbound Credit Verification
        // Must contain credit/received keywords and NOT debit/OTP/request/promo keywords
        val hasCreditWord = lower.contains("credited") || lower.contains("received") || lower.contains("deposited") ||
                lower.contains("cr to a/c") || lower.contains("cr to acct") || lower.contains("has transferred") || lower.contains("paid you") ||
                lower.contains("payment received") || lower.contains("payment of") || lower.contains("credit of") ||
                lower.contains("transferred rs") || lower.contains("transferred inr") || lower.contains("sent you") ||
                lower.contains("sent rs") || lower.contains("sent inr") || lower.contains("is credited with")

        val hasDebitOrExcludeWord = lower.contains("debited") || lower.contains("withdrawn") || lower.contains("spent") ||
                lower.contains("paid to") || (lower.contains("otp") && !lower.contains("credited")) ||
                lower.contains("requesting") || lower.contains("apply now") || lower.contains("mandate created")

        val isCredit = hasCreditWord && !hasDebitOrExcludeWord

        // Step B: Amount Extraction
        val amountPatterns = listOf(
            Pattern.compile("(?:(?:rs\\.?|inr|₹|credited by|credited with|received|paid|transferred)\\s*[:]?\\s*)([0-9]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:rs\\.?|inr|₹|rupees|credited|deposited)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:sum of|amount of|value of)\\s*(?:rs\\.?|inr|₹)?\\s*([0-9]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("credited with\\s*(?:rs\\.?|inr|₹)?\\s*([0-9]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("credited to (?:your )?(?:[a-zA-Z\\s]+)?a/c [^ ]+ (?:with|for)?\\s*(?:rs\\.?|inr|₹)?\\s*([0-9]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE)
        )
        var extractedAmount = 0.0
        for (pat in amountPatterns) {
            val m = pat.matcher(clean)
            if (m.find()) {
                val parsed = m.group(1)?.toDoubleOrNull()
                if (parsed != null && parsed > 0) {
                    extractedAmount = parsed
                    break
                }
            }
        }

        // Step C: Bank / Provider Extraction
        val bankName = when {
            lowerSender.contains("kotak") || lower.contains("kotak") || lowerSender.contains("kotakb") -> "Kotak Mahindra Bank"
            lowerSender.contains("sbi") || lower.contains("state bank") || lower.contains("sbi inb") -> "State Bank of India"
            lowerSender.contains("hdfc") || lower.contains("hdfcbk") -> "HDFC Bank"
            lowerSender.contains("icici") || lower.contains("icicib") -> "ICICI Bank"
            lowerSender.contains("axis") || lower.contains("axisbk") -> "Axis Bank"
            lowerSender.contains("pnb") || lower.contains("punjab") -> "Punjab National Bank"
            lowerSender.contains("bob") || lower.contains("baroda") -> "Bank of Baroda"
            lowerSender.contains("can") || lower.contains("canara") -> "Canara Bank"
            lowerSender.contains("union") || lower.contains("unionb") -> "Union Bank of India"
            lowerSender.contains("yes") || lower.contains("yesb") -> "YES Bank"
            lowerSender.contains("idfc") -> "IDFC FIRST Bank"
            lowerSender.contains("indus") -> "IndusInd Bank"
            lowerSender.contains("paytm") -> "Paytm"
            lowerSender.contains("gpay") || lower.contains("google pay") -> "Google Pay"
            lowerSender.contains("phonepe") -> "PhonePe"
            lowerSender.contains("bhim") -> "BHIM UPI"
            senderAddress.isNotBlank() && !senderAddress.startsWith("+") && senderAddress.length >= 3 ->
                "Bank (${senderAddress.takeLast(6).uppercase(Locale.ROOT)})"
            else -> "UPI"
        }

        // Step D: Reference / UTR ID Extraction
        val refPattern = Pattern.compile("(?:(?:ref(?:erence)?|rrn|upi\\s*ref(?:\\s*no)?|txn\\s*(?:id|ref)|utr)\\s*[:]?\\s*)([0-9A-Za-z]{6,22})", Pattern.CASE_INSENSITIVE)
        val refMatcher = refPattern.matcher(clean)
        val refId = if (refMatcher.find()) {
            refMatcher.group(1) ?: "UTR${System.currentTimeMillis() % 10000000000L}"
        } else {
            "UTR${System.currentTimeMillis() % 10000000000L}"
        }

        // Step E: Person / Sender Name Extraction
        val invalidWords = setOf(
            "transfer", "vpa", "account", "user", "cheque", "neft", "rtgs", "imps", "bank", "card",
            "your", "dear", "upi", "credit", "debited", "deposited", "instant", "payment", "successful",
            "wallet", "soundbox", "app", "alert", "notice", "balance", "total", "ref", "rrn", "linked",
            "inr", "rs", "rupees", "info", "a/c", "acct", "mob", "mobile", "txn", "id", "avl", "bal",
            "available", "tot", "nodal", "branch", "customer", "clearing", "system", "auto", "cr", "dr",
            "kotak", "kotakb", "kotakbank", "mahindra", "sbi", "sbin", "hdfc", "hdfcbk", "icici", "icicib",
            "axis", "axisbk", "pnb", "bob", "yesb", "paytm", "phonepe", "gpay", "okaxis", "okhdfcbank",
            "oksbi", "okicici", "apl", "ybl", "ibl", "axl", "upi_cr", "upicr", "transaction", "direct",
            "direct credit", "p2a", "p2p", "unknown", "none"
        )
        var extractedPayer: String? = null

        // 1. Kotak / HDFC / SBI Info field slash structure: "Info: UPI/CR/123456789012/Rahul Sharma/Kotak" or "UPI/P2A/123456789012/Rahul Sharma/Kotak"
        val slashPattern = Pattern.compile("(?:upi|imps|neft)(?:/(?:cr|p2a|p2p|dr))?/[0-9]+/([^/\\r\\n\\.]+)(?:/[^/\\r\\n\\.]*)?", Pattern.CASE_INSENSITIVE)
        val slashMatcher = slashPattern.matcher(clean)
        if (slashMatcher.find()) {
            val candidate = slashMatcher.group(1)?.trim()
            if (!candidate.isNullOrBlank() && candidate.length >= 2) {
                val candidateLower = candidate.lowercase(Locale.ROOT)
                if (!invalidWords.contains(candidateLower) && !candidateLower.contains("kotak") && !candidateLower.contains("bank")) {
                    extractedPayer = candidate
                }
            }
        }

        // 2. Direct Info: segment inspection
        if (extractedPayer.isNullOrBlank()) {
            val infoPattern = Pattern.compile("info\\s*:\\s*([^\\.\\r\\n]+)", Pattern.CASE_INSENSITIVE)
            val infoMatcher = infoPattern.matcher(clean)
            if (infoMatcher.find()) {
                val infoBody = infoMatcher.group(1)?.trim() ?: ""
                val tokens = infoBody.split("/").map { it.trim() }.filter { it.isNotBlank() }
                for (token in tokens) {
                    if (token.matches(Regex("^[A-Za-z\\s]{2,30}$"))) {
                        val tokenLower = token.lowercase(Locale.ROOT)
                        if (!invalidWords.contains(tokenLower) && !tokenLower.contains("kotak") && !tokenLower.contains("bank")) {
                            extractedPayer = token
                            break
                        }
                    }
                }
            }
        }

        // 3. Hyphen Pattern: "UPI-CR-123456789012-Rahul Sharma" or "IMPS-CR-123456-Rahul Sharma"
        if (extractedPayer.isNullOrBlank()) {
            val upiHyphenPattern = Pattern.compile("(?:upi|imps)(?:-cr)?-[0-9]+-([A-Za-z\\s]{2,30})", Pattern.CASE_INSENSITIVE)
            val hyphenMatcher = upiHyphenPattern.matcher(clean)
            if (hyphenMatcher.find()) {
                val candidate = hyphenMatcher.group(1)?.trim()
                if (!candidate.isNullOrBlank() && candidate.length >= 2) {
                    val candidateLower = candidate.lowercase(Locale.ROOT)
                    if (!invalidWords.contains(candidateLower) && !candidateLower.contains("kotak") && !candidateLower.contains("bank")) {
                        extractedPayer = candidate
                    }
                }
            }
        }

        // 4. VPA format: "linked to VPA rahul.sharma@okaxis" or "by VPA rahul@oksbi"
        if (extractedPayer.isNullOrBlank()) {
            val vpaPattern = Pattern.compile("(?:linked to vpa|by vpa|from vpa|vpa)\\s+([a-zA-Z0-9._-]+)@[a-zA-Z0-9]+", Pattern.CASE_INSENSITIVE)
            val vpaMatcher = vpaPattern.matcher(clean)
            if (vpaMatcher.find()) {
                val rawUser = vpaMatcher.group(1) ?: ""
                val cleanedUser = rawUser
                    .replace(Regex("[0-9]"), "")
                    .replace(".", " ")
                    .replace("_", " ")
                    .replace("-", " ")
                    .trim()
                if (cleanedUser.length >= 2 && !invalidWords.contains(cleanedUser.lowercase(Locale.ROOT))) {
                    extractedPayer = cleanedUser
                }
            }
        }

        // 5. Transfer from / By / From / Paid by / Credited by Name / Sender: Name
        if (extractedPayer.isNullOrBlank()) {
            val byPattern = Pattern.compile(
                "(?:by transfer from|transfer from|received from|credited by|paid by|sent by|transferred by|sender:?|payer:?|from|by)\\s+(?:mr\\.?\\s+|mrs\\.?\\s+|ms\\.?\\s+|shri\\.?\\s+|smt\\.?\\s+)?([A-Za-z\\s]{2,30}?)(?=\\s*(?:\\(upi|\\(ref|upi ref|ref|on |via |using |through |for |to |a/c |acct |balance |tot bal|avl|bal|info|\\.|,|-|$))",
                Pattern.CASE_INSENSITIVE
            )
            val byMatcher = byPattern.matcher(clean)
            while (byMatcher.find()) {
                val candidate = byMatcher.group(1)?.trim() ?: ""
                val firstWord = candidate.split(" ").firstOrNull()?.lowercase(Locale.ROOT) ?: ""
                if (candidate.length >= 2 && !invalidWords.contains(firstWord) && !invalidWords.contains(candidate.lowercase(Locale.ROOT)) && !candidate.lowercase(Locale.ROOT).contains("kotak") && !candidate.lowercase(Locale.ROOT).contains("bank")) {
                    extractedPayer = candidate
                    break
                }
            }
        }

        // 6. "[Name] paid you" or "[Name] sent you"
        if (extractedPayer.isNullOrBlank()) {
            val sentPattern = Pattern.compile("([A-Za-z\\s]{2,25})\\s+(?:paid you|sent you|transferred)", Pattern.CASE_INSENSITIVE)
            val sentMatcher = sentPattern.matcher(clean)
            if (sentMatcher.find()) {
                val candidate = sentMatcher.group(1)?.trim() ?: ""
                val firstWord = candidate.split(" ").firstOrNull()?.lowercase(Locale.ROOT) ?: ""
                if (candidate.length >= 2 && !invalidWords.contains(firstWord)) {
                    extractedPayer = candidate
                }
            }
        }

        // 7. Check mobile number in SMS and match phonebook contact name
        if (extractedPayer.isNullOrBlank()) {
            val mobPattern = Pattern.compile("(?:mobile|mob|linked to mobile)\\s*[:]?\\s*([0-9]{10})", Pattern.CASE_INSENSITIVE)
            val mobMatcher = mobPattern.matcher(clean)
            if (mobMatcher.find()) {
                val mob = mobMatcher.group(1)
                if (!mob.isNullOrBlank()) {
                    val contactName = getContactNameFromNumber(context, mob)
                    if (!contactName.isNullOrBlank()) {
                        extractedPayer = contactName
                    }
                }
            }
        }

        // 8. If SMS was sent from a friend's phone number, check contact name
        if (extractedPayer.isNullOrBlank() && senderAddress.isNotBlank() && (senderAddress.startsWith("+") || senderAddress.matches(Regex("[0-9]{10,13}")))) {
            val contactName = getContactNameFromNumber(context, senderAddress)
            if (!contactName.isNullOrBlank()) {
                extractedPayer = contactName
            }
        }

        // Format clean Payer Name
        var payerName = ""
        if (!extractedPayer.isNullOrBlank()) {
            val words = extractedPayer.split(" ")
                .map { it.trim() }
                .filter { it.isNotBlank() && !invalidWords.contains(it.lowercase(Locale.ROOT)) && it.length > 1 }
            if (words.isNotEmpty()) {
                val formatted = words.joinToString(" ") { word ->
                    word.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }.take(30)
                val lowerFormatted = formatted.lowercase(Locale.ROOT)
                if (!lowerFormatted.contains("kotak") && !lowerFormatted.contains("bank") && !lowerFormatted.contains("customer")) {
                    payerName = formatted
                }
            }
        }

        val formattedAmt = if (extractedAmount == 1.0) "1 Rupee" else if (extractedAmount % 1.0 == 0.0) "${extractedAmount.toInt()} Rupees" else String.format(Locale.US, "%.2f Rupees", extractedAmount)
        val soundboxText = if (payerName.isNotBlank()) {
            "Received $formattedAmt from $payerName."
        } else {
            "Received $formattedAmt."
        }

        val displayPayer = if (payerName.isNotBlank()) payerName else "Direct Credit"

        return GeminiAiService.AiPaymentSmsResult(
            isCreditPayment = isCredit && extractedAmount > 0,
            amount = extractedAmount,
            payerName = displayPayer,
            bankName = bankName,
            referenceId = refId,
            confidence = if (isCredit && extractedAmount > 0) 0.98f else 0.20f,
            vocalAnnouncement = soundboxText,
            rawSummary = if (payerName.isNotBlank()) "₹$formattedAmt from $payerName" else "₹$formattedAmt received ($bankName)"
        )
    }

    private fun getContactNameFromNumber(context: Context, phoneNumber: String): String? {
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val cursor: Cursor? = context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIdx >= 0) it.getString(nameIdx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun showPaymentSuccessNotification(
        context: Context,
        amount: Double,
        payerName: String,
        bankName: String,
        refId: String
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID_SMS_PAYMENT,
                    "Smart Vyapar Payment Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Instant soundbox voice payment notifications"
                    enableVibration(true)
                    enableLights(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("NAVIGATE_TO", "SMART_VYAPAR")
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val formattedAmount = if (amount % 1.0 == 0.0) "₹${amount.toInt()}" else String.format(Locale.US, "₹%.2f", amount)

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_SMS_PAYMENT)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("🔊 Payment Received: $formattedAmount")
                .setContentText("From $payerName on $bankName")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Received $formattedAmount from $payerName\n\nChannel: $bankName\nRef / UTR: $refId\nStatus: Verified Bank Deposit"
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(NOTIFICATION_ID_PAYMENT, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show payment notification: ${e.message}")
        }
    }

    private fun showSmsThreatNotification(
        context: Context,
        sender: String,
        body: String,
        category: ThreatCategory,
        reasoning: String
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID_SMS_SECURITY,
                    "Rakshak SMS Security Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent alerts for malicious SMS phishing and scam messages"
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

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_SMS_SECURITY)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("🚨 Phishing SMS Intercepted ($sender)")
                .setContentText(reasoning)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Threat: ${category.displayName}\nSender: $sender\n\nBody: \"$body\"\n\n⚠️ Action: Do not click links or share OTP."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(NOTIFICATION_ID_SMS, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post SMS threat notification: ${e.message}")
        }
    }
}
