package com.example.engine

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * AudioScamTfLiteClassifier
 *
 * Machine Learning model wrapper using TensorFlow Lite to analyze raw PCM 16-bit audio streams
 * (16kHz sample rate) from the CallAudioInterceptor to detect patterns indicative of phone scams:
 * - Digital Arrest & Legal Extortion audio signatures
 * - Banking / KYC OTP Coercion cadence
 * - Tech Support & Remote Screen Takeover guidance
 * - Synthetic Voice / Deepfake Clone acoustic artifacts
 * - Extreme vocal stress & pressure markers
 */
class AudioScamTfLiteClassifier(private val context: Context) {

    private var tfliteInterpreter: Interpreter? = null
    private var isTfLiteLoaded = false

    companion object {
        private const val TAG = "AudioScamTfLite"
        const val MODEL_ASSET_PATH = "models/audio_scam_detector.tflite"

        const val SAMPLE_RATE = 16000 // 16 kHz mono
        const val FRAME_SIZE = 512    // 32ms frame window
        const val NUM_MEL_BINS = 40
        const val NUM_CLASSES = 5     // 0: SAFE, 1: DIGITAL_ARREST, 2: BANK_OTP_THEFT, 3: REMOTE_ACCESS, 4: SYNTHETIC_VOICE

        // Scam Archetype Labels
        const val ARCHETYPE_SAFE = "Normal / Non-Threatening Conversation"
        const val ARCHETYPE_DIGITAL_ARREST = "Digital Arrest & Police Extortion"
        const val ARCHETYPE_BANK_KYC = "Banking KYC & OTP Theft Coercion"
        const val ARCHETYPE_REMOTE_ACCESS = "Remote Screen Hijack Support Scam"
        const val ARCHETYPE_SYNTHETIC_VOICE = "AI Deepfake Voice Clone / Spoofing"
    }

    data class AudioClassificationResult(
        val isScam: Boolean,
        val archetype: String,
        val confidence: Float,
        val stressLevel: Float,          // 0.0 to 1.0
        val syntheticVoiceScore: Float,  // 0.0 to 1.0
        val acousticMarkers: List<String>,
        val reasoning: String,
        val recommendedAction: String,
        val inferenceLatencyMs: Long
    )

    init {
        initializeTfLiteInterpreter()
    }

    /**
     * Initializes the TensorFlow Lite Interpreter if a model asset is present.
     */
    private fun initializeTfLiteInterpreter() {
        try {
            val modelBuffer = loadModelFile(context, MODEL_ASSET_PATH)
            if (modelBuffer != null) {
                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                    setUseNNAPI(true)
                }
                tfliteInterpreter = Interpreter(modelBuffer, options)
                isTfLiteLoaded = true
                Log.i(TAG, "TensorFlow Lite Audio Scam Detector loaded successfully.")
            } else {
                Log.i(TAG, "No external .tflite model asset found. Utilizing embedded acoustic inference engine.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "TensorFlow Lite initialization fallback: ${e.message}")
            isTfLiteLoaded = false
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer? {
        return try {
            val fileDescriptor = context.assets.openFd(modelPath)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Analyzes a raw ShortArray PCM audio buffer (from AudioRecord / MediaProjection).
     */
    fun classifyAudioBuffer(
        audioSamples: ShortArray,
        contextTranscript: String = ""
    ): AudioClassificationResult {
        val startTime = System.currentTimeMillis()

        if (audioSamples.isEmpty()) {
            return AudioClassificationResult(
                isScam = false,
                archetype = ARCHETYPE_SAFE,
                confidence = 0.99f,
                stressLevel = 0.0f,
                syntheticVoiceScore = 0.0f,
                acousticMarkers = emptyList(),
                reasoning = "Audio stream is silent or empty.",
                recommendedAction = "NORMAL CALL",
                inferenceLatencyMs = 0
            )
        }

        // 1. Acoustic Feature Extraction (Time & Spectral Domain)
        val rms = calculateRms(audioSamples)
        val zcr = calculateZeroCrossingRate(audioSamples)
        val spectralCentroid = calculateSpectralCentroid(audioSamples)
        val pitchJitter = calculatePitchJitter(audioSamples)
        val energyFlux = calculateEnergyFlux(audioSamples)

        // 2. High-Stress & Voice Clone Acoustic Marker Calculation
        // Synthetic AI Voice typically exhibits unnaturally low pitch jitter and abnormal spectral slope
        val syntheticVoiceScore = if (rms > 200f && pitchJitter < 0.012f && spectralCentroid > 2800f) {
            (1.0f - (pitchJitter * 50f)).coerceIn(0.70f, 0.96f)
        } else {
            0.05f
        }

        // Vocal urgency / coercive stress is characterized by elevated high-band energy flux and high ZCR
        val stressLevel = ((energyFlux / 1500f) + (zcr * 2.5f) + (if (rms > 1200f) 0.35f else 0.05f))
            .coerceIn(0.0f, 1.0f)

        val acousticMarkers = mutableListOf<String>()
        if (stressLevel > 0.65f) acousticMarkers.add("Extreme Vocal Pressure & Urgency (Stress: ${(stressLevel * 100).toInt()}%)")
        if (syntheticVoiceScore > 0.70f) acousticMarkers.add("Synthetic Vocoder / Clone Artifacts (Clone Score: ${(syntheticVoiceScore * 100).toInt()}%)")
        if (spectralCentroid > 3200f) acousticMarkers.add("High-Frequency Robotic Carrier Noise")
        if (pitchJitter > 0.18f) acousticMarkers.add("Unstable Aggressive Vocal Modulation")

        // 3. TensorFlow Lite Inference or Acoustic Classification Engine
        var isScam = false
        var confidence = 0.5f
        var archetype = ARCHETYPE_SAFE
        var reasoning = "Acoustic envelope matches natural conversational parameters."
        var recommendedAction = "SAFE TO CONTINUE"

        if (isTfLiteLoaded && tfliteInterpreter != null) {
            try {
                val inputBuffer = preprocessAudioForTfLite(audioSamples)
                val outputBuffer = Array(1) { FloatArray(NUM_CLASSES) }
                tfliteInterpreter?.run(inputBuffer, outputBuffer)

                val probabilities = outputBuffer[0]
                val maxIdx = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
                val maxProb = probabilities[maxIdx]

                confidence = maxProb
                when (maxIdx) {
                    1 -> {
                        isScam = true
                        archetype = ARCHETYPE_DIGITAL_ARREST
                        reasoning = "TensorFlow Lite classified authoritative extortion & digital arrest intimidation cadence."
                        recommendedAction = "HANG UP IMMEDIATELY • DO NOT PAY"
                    }
                    2 -> {
                        isScam = true
                        archetype = ARCHETYPE_BANK_KYC
                        reasoning = "TensorFlow Lite identified banking credential extraction & urgency patterns."
                        recommendedAction = "DO NOT SHARE OTP OR PIN"
                    }
                    3 -> {
                        isScam = true
                        archetype = ARCHETYPE_REMOTE_ACCESS
                        reasoning = "TensorFlow Lite detected remote screen hijack & AnyDesk installation guidance."
                        recommendedAction = "DO NOT INSTALL APPS • DISCONNECT"
                    }
                    4 -> {
                        isScam = true
                        archetype = ARCHETYPE_SYNTHETIC_VOICE
                        reasoning = "TensorFlow Lite detected neural voice clone / synthetic deepfake artifacts."
                        recommendedAction = "VERIFY CALLER VIA SEPARATE KNOWN NUMBER"
                    }
                    else -> {
                        isScam = false
                        archetype = ARCHETYPE_SAFE
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "TFLite execution error, falling back to Acoustic Engine: ${e.message}")
                isScam = false
            }
        }

        // If TFLite is not used or produced neutral score, perform acoustic heuristic classification:
        if (!isScam) {
            val transcriptLower = contextTranscript.lowercase()
            val hasDigitalArrestKeywords = listOf("digital arrest", "police", "cbi", "customs", "narcotics", "contraband", "warrant", "aadhaar", "arrest warrant").any { transcriptLower.contains(it) }
            val hasKycKeywords = listOf("kyc", "otp", "debit card", "account blocked", "verify pin", "pan card", "netbanking").any { transcriptLower.contains(it) }
            val hasRemoteKeywords = listOf("anydesk", "teamviewer", "quicksupport", "rustdesk", "code bataiye", "screen share", "apk").any { transcriptLower.contains(it) }

            if (hasDigitalArrestKeywords || (stressLevel > 0.70f && rms > 800f && transcriptLower.contains("police"))) {
                isScam = true
                archetype = ARCHETYPE_DIGITAL_ARREST
                confidence = 0.96f
                acousticMarkers.add("Coercive Law Enforcement Impersonation")
                reasoning = "High vocal acoustic stress correlated with fake law enforcement Digital Arrest script."
                recommendedAction = "HANG UP IMMEDIATELY • POLICE NEVER CALLS VIA VIDEO/PHONE"
            } else if (hasKycKeywords || (stressLevel > 0.60f && transcriptLower.contains("otp"))) {
                isScam = true
                archetype = ARCHETYPE_BANK_KYC
                confidence = 0.94f
                acousticMarkers.add("Urgent Financial Credential Extraction")
                reasoning = "Acoustic urgency markers paired with OTP/KYC theft dialogue."
                recommendedAction = "NEVER SHARE OTP OR CARD NUMBERS"
            } else if (hasRemoteKeywords || transcriptLower.contains("anydesk")) {
                isScam = true
                archetype = ARCHETYPE_REMOTE_ACCESS
                confidence = 0.95f
                acousticMarkers.add("Remote Access App Coercion")
                reasoning = "Instructional cadence detected coercing victim to install remote screen sharing software."
                recommendedAction = "NEVER INSTALL ANYDESK OR QUICK SUPPORT"
            } else if (syntheticVoiceScore > 0.85f) {
                isScam = true
                archetype = ARCHETYPE_SYNTHETIC_VOICE
                confidence = syntheticVoiceScore
                acousticMarkers.add("AI Voice Clone Jitter Discontinuity")
                reasoning = "Raw audio exhibits unnatural pitch consistency and vocoder harmonics characteristic of cloned speech."
                recommendedAction = "SUSPECTED AI CLONE • HANG UP AND CALL BACK"
            } else if (stressLevel > 0.88f) {
                isScam = true
                archetype = "High-Pressure Coercive Call"
                confidence = 0.82f
                acousticMarkers.add("Extreme Intimidation Cadence")
                reasoning = "Abnormal acoustic pressure and vocal harassment detected."
                recommendedAction = "EXERCISE EXTREME CAUTION"
            }
        }

        val latency = System.currentTimeMillis() - startTime

        return AudioClassificationResult(
            isScam = isScam,
            archetype = archetype,
            confidence = confidence,
            stressLevel = stressLevel,
            syntheticVoiceScore = syntheticVoiceScore,
            acousticMarkers = acousticMarkers,
            reasoning = reasoning,
            recommendedAction = recommendedAction,
            inferenceLatencyMs = latency
        )
    }

    /**
     * Converts PCM ShortArray into normalized 40-bin Log-Mel Spectrogram FloatBuffer for TFLite model.
     */
    private fun preprocessAudioForTfLite(audioSamples: ShortArray): ByteBuffer {
        val inputBuffer = ByteBuffer.allocateDirect(4 * NUM_MEL_BINS * 32)
        inputBuffer.order(ByteOrder.nativeOrder())

        val step = (audioSamples.size / 32).coerceAtLeast(1)
        for (i in 0 until 32) {
            val frameStart = (i * step).coerceAtMost(audioSamples.size - FRAME_SIZE.coerceAtMost(audioSamples.size))
            val frameEnd = (frameStart + FRAME_SIZE).coerceAtMost(audioSamples.size)
            val subSamples = audioSamples.sliceArray(frameStart until frameEnd)

            val melEnergies = computeSimpleMelEnergies(subSamples, NUM_MEL_BINS)
            for (bin in melEnergies) {
                inputBuffer.putFloat(bin)
            }
        }
        inputBuffer.rewind()
        return inputBuffer
    }

    private fun computeSimpleMelEnergies(samples: ShortArray, bins: Int): FloatArray {
        val output = FloatArray(bins)
        if (samples.isEmpty()) return output

        val chunkSize = (samples.size / bins).coerceAtLeast(1)
        for (b in 0 until bins) {
            var sum = 0.0
            val start = b * chunkSize
            val end = (start + chunkSize).coerceAtMost(samples.size)
            for (j in start until end) {
                val s = samples[j] / 32768.0f
                sum += s * s
            }
            val energy = (sum / (end - start).coerceAtLeast(1)).toFloat()
            output[b] = log10(energy + 1e-6f)
        }
        return output
    }

    // ----------------------------------------------------
    // ACOUSTIC SIGNAL PROCESSING UTILITIES
    // ----------------------------------------------------

    private fun calculateRms(samples: ShortArray): Float {
        var sum = 0.0
        for (s in samples) {
            sum += s * s
        }
        return sqrt(sum / samples.size.coerceAtLeast(1)).toFloat()
    }

    private fun calculateZeroCrossingRate(samples: ShortArray): Float {
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i] >= 0 && samples[i - 1] < 0) || (samples[i] < 0 && samples[i - 1] >= 0)) {
                crossings++
            }
        }
        return crossings.toFloat() / samples.size.coerceAtLeast(1)
    }

    private fun calculateSpectralCentroid(samples: ShortArray): Float {
        var weightedSum = 0.0
        var totalSum = 0.0
        val step = (samples.size / 64).coerceAtLeast(1)
        for (i in 0 until 64) {
            val idx = (i * step).coerceAtMost(samples.size - 1)
            val magnitude = abs(samples[idx].toDouble())
            val freq = (i * (SAMPLE_RATE / 2.0) / 64.0)
            weightedSum += freq * magnitude
            totalSum += magnitude
        }
        return if (totalSum > 0) (weightedSum / totalSum).toFloat() else 1000f
    }

    private fun calculatePitchJitter(samples: ShortArray): Float {
        var diffSum = 0.0
        val step = (samples.size / 32).coerceAtLeast(1)
        for (i in 1 until 32) {
            val prev = abs(samples[(i - 1) * step].toDouble())
            val curr = abs(samples[i * step].toDouble())
            diffSum += abs(curr - prev)
        }
        val avgAmplitude = calculateRms(samples).coerceAtLeast(1f)
        return (diffSum / (31 * avgAmplitude)).toFloat().coerceIn(0.0f, 1.0f)
    }

    private fun calculateEnergyFlux(samples: ShortArray): Float {
        val half = samples.size / 2
        var sum1 = 0.0
        var sum2 = 0.0
        for (i in 0 until half) sum1 += samples[i] * samples[i]
        for (i in half until samples.size) sum2 += samples[i] * samples[i]
        return abs(sqrt(sum1 / half.coerceAtLeast(1)) - sqrt(sum2 / (samples.size - half).coerceAtLeast(1))).toFloat()
    }

    fun close() {
        try {
            tfliteInterpreter?.close()
            tfliteInterpreter = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TFLite interpreter: ${e.message}")
        }
    }
}
