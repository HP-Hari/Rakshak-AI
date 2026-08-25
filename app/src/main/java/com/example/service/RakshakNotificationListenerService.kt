package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.RakshakApplication
import com.example.data.model.UpiProvider
import com.example.engine.NpuInferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class LiveUpiNotificationEvent(
    val payerName: String,
    val amount: Double,
    val upiApp: String,
    val packageName: String,
    val referenceId: String,
    val rawText: String,
    val timestamp: Long = System.currentTimeMillis()
)

class RakshakNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val npuEngine = NpuInferenceEngine()

    companion object {
        private val _liveUpiFlow = MutableSharedFlow<LiveUpiNotificationEvent>(extraBufferCapacity = 20)
        val liveUpiFlow: SharedFlow<LiveUpiNotificationEvent> = _liveUpiFlow.asSharedFlow()

        @Volatile
        var lastNotificationTimestamp: Long = 0L

        fun broadcastManualUpiPayment(event: LiveUpiNotificationEvent) {
            lastNotificationTimestamp = event.timestamp
            _liveUpiFlow.tryEmit(event)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        val extras = sbn.notification.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val combinedMessage = if (bigText.isNotBlank()) bigText else text

        // Filter UPI Providers (Google Pay, PhonePe, Paytm, BharatPe, BHIM)
        val isUpiPackage = UpiProvider.values().any { pkg.contains(it.packageName) } ||
                pkg.contains("paisa") || pkg.contains("phonepe") || pkg.contains("paytm") || pkg.contains("bharatpe") || pkg.contains("upi")

        if (isUpiPackage) {
            Log.d("NotificationListener", "UPI notification detected from $pkg: $title - $combinedMessage")

            val extracted = npuEngine.extractUpiPayment(pkg, title, combinedMessage)
            if (extracted != null) {
                val now = System.currentTimeMillis()
                lastNotificationTimestamp = now

                val event = LiveUpiNotificationEvent(
                    payerName = extracted.payerName,
                    amount = extracted.amount,
                    upiApp = extracted.upiApp,
                    packageName = pkg,
                    referenceId = extracted.referenceId,
                    rawText = extracted.rawText,
                    timestamp = now
                )

                _liveUpiFlow.tryEmit(event)

                // Save to Room DB and vocalize on-device TTS
                serviceScope.launch {
                    val app = applicationContext as? RakshakApplication
                    app?.repository?.recordUpiTransaction(
                        payerName = extracted.payerName,
                        amount = extracted.amount,
                        upiApp = extracted.upiApp,
                        packageName = pkg,
                        referenceId = extracted.referenceId,
                        isVerified = true,
                        isSpoofAttempt = false,
                        rawText = extracted.rawText
                    )
                    app?.ttsManager?.speakPaymentReceived(extracted.amount, extracted.payerName, extracted.upiApp)
                }
            }
        }
    }
}
