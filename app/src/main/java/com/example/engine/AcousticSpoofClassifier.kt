package com.example.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AcousticDetectionEvent(
    val soundboxApp: String,
    val detectedAmount: Double,
    val isVerified: Boolean,
    val latencyMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String
)

class AcousticSpoofClassifier {

    private val _detectionEvents = MutableStateFlow<List<AcousticDetectionEvent>>(emptyList())
    val detectionEvents: StateFlow<List<AcousticDetectionEvent>> = _detectionEvents.asStateFlow()

    private val _isAcousticMonitoring = MutableStateFlow(true)
    val isAcousticMonitoring: StateFlow<Boolean> = _isAcousticMonitoring.asStateFlow()

    fun toggleMonitoring(enabled: Boolean) {
        _isAcousticMonitoring.value = enabled
    }

    /**
     * Cross-checks audio chime detection against the 800ms UPI notification timestamp window.
     */
    fun evaluateAudioPayment(
        soundboxApp: String,
        amount: Double,
        lastNotificationTimestamp: Long?
    ): AcousticDetectionEvent {
        val now = System.currentTimeMillis()
        val latency = if (lastNotificationTimestamp != null) {
            Math.abs(now - lastNotificationTimestamp)
        } else {
            Long.MAX_VALUE
        }

        // Strict 800ms correlation window
        val isVerified = latency <= 800L

        val event = if (isVerified) {
            AcousticDetectionEvent(
                soundboxApp = soundboxApp,
                detectedAmount = amount,
                isVerified = true,
                latencyMs = latency,
                timestamp = now,
                message = "VERIFIED: Soundbox chime synchronized with Bank Notification (${latency}ms window)"
            )
        } else {
            AcousticDetectionEvent(
                soundboxApp = soundboxApp,
                detectedAmount = amount,
                isVerified = false,
                latencyMs = latency,
                timestamp = now,
                message = "SPOOF ALERT: Soundbox audio detected without authentic bank notification (Delta: ${if (latency == Long.MAX_VALUE) "No notification" else "${latency}ms > 800ms"})"
            )
        }

        _detectionEvents.value = listOf(event) + _detectionEvents.value.take(19)
        return event
    }
}
