package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upi_transactions")
data class UpiTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payerName: String,
    val amount: Double,
    val upiApp: String,
    val packageName: String,
    val referenceId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isVerified: Boolean = true,
    val isSpoofAttempt: Boolean = false,
    val rawNotificationText: String = ""
)

@Entity(tableName = "khata_entries")
data class KhataEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerName: String,
    val amount: Double,
    val entryType: String, // GAVE_CREDIT or RECEIVED_PAYMENT
    val note: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSettled: Boolean = false,
    val customerPhone: String? = null
)

@Entity(tableName = "call_threats")
data class CallThreatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val callerTag: String,
    val transcript: String,
    val isScam: Boolean,
    val confidence: Float,
    val triggerWords: String, // Comma-separated
    val threatCategory: String,
    val actionTaken: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "acoustic_spoof_alerts")
data class AcousticSpoofAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val detectedChimeApp: String,
    val detectedAmount: Double,
    val hasMatchingUpiNotification: Boolean,
    val latencyWindowMs: Long,
    val audioConfidence: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val statusText: String
)
