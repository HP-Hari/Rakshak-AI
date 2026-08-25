package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.AcousticSpoofClassifier
import com.example.engine.NpuInferenceEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Rakshak AI", appName)
    }

    @Test
    fun `test NPU threat classification for OTP scam`() = runBlocking {
        val npuEngine = NpuInferenceEngine()
        val scamTranscript = "Sir please tell me the 6 digit OTP sent to your phone right now to avoid account freeze."
        val result = npuEngine.classifyTranscriptStream(scamTranscript)

        assertTrue(result.isScam)
        assertTrue(result.confidence >= 0.85f)
        assertTrue(result.triggerWords.isNotEmpty())
    }

    @Test
    fun `test UPI regex extraction for PhonePe and GPay`() {
        val npuEngine = NpuInferenceEngine()
        val gpayRaw = "Google Pay: Ramesh Verma paid you Rs. 450.00 via GPay UPI ref: 83920194821"
        val extracted = npuEngine.extractUpiPayment("com.google.android.apps.nbu.paisa.user", "Google Pay", gpayRaw)

        assertEquals(450.0, extracted?.amount ?: 0.0, 0.01)
        assertEquals("Google Pay", extracted?.upiApp)
    }

    @Test
    fun `test acoustic soundbox 800ms anti-spoof window`() {
        val classifier = AcousticSpoofClassifier()
        val now = System.currentTimeMillis()

        // 1. Genuine payment synchronized within 300ms
        val genuineEvent = classifier.evaluateAudioPayment(
            soundboxApp = "Paytm Soundbox",
            amount = 500.0,
            lastNotificationTimestamp = now - 200L
        )
        assertTrue(genuineEvent.isVerified)

        // 2. Spoof attack with no bank notification
        val spoofEvent = classifier.evaluateAudioPayment(
            soundboxApp = "PhonePe Soundbox",
            amount = 1000.0,
            lastNotificationTimestamp = null
        )
        assertFalse(spoofEvent.isVerified)
    }
}
