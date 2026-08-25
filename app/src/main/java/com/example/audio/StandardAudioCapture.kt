package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Standard Audio Capture Helper for microphone acoustic stream and speech recognition
 */
class StandardAudioCapture(
    private val context: Context,
    private val sampleRate: Int = 16000,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private val minBufferSize = maxOf(
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
        sampleRate * 2 // Minimum 1-second buffer fallback
    )

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope, onBufferRead: (ByteArray, Int) -> Unit): Boolean {
        if (!hasRecordPermission()) {
            return false
        }

        if (audioRecord != null) return true

        return try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return false
            }

            record.startRecording()
            audioRecord = record

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(2048)
                while (isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val bytesRead = record.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        onBufferRead(buffer.copyOf(bytesRead), bytesRead)
                    }
                }
            }
            true
        } catch (e: Exception) {
            stop()
            false
        }
    }

    fun stop() {
        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.apply {
            try {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    stop()
                }
            } catch (_: Exception) {}
            release()
        }
        audioRecord = null
    }
}
