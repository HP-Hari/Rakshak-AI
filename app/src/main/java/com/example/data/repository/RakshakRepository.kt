package com.example.data.repository

import com.example.data.local.dao.RakshakDao
import com.example.data.local.entity.AcousticSpoofAlertEntity
import com.example.data.local.entity.CallThreatEntity
import com.example.data.local.entity.KhataEntryEntity
import com.example.data.local.entity.UpiTransactionEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class RakshakRepository(private val dao: RakshakDao) {

    private fun getStartOfDay(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    // UPI Transactions
    val allTransactions: Flow<List<UpiTransactionEntity>> = dao.getAllUpiTransactions()
    val todayTransactions: Flow<List<UpiTransactionEntity>> = dao.getTodayUpiTransactions(getStartOfDay())
    val todayTotalCollection: Flow<Double?> = dao.getTodayTotalCollection(getStartOfDay())

    suspend fun recordUpiTransaction(
        payerName: String,
        amount: Double,
        upiApp: String,
        packageName: String,
        referenceId: String,
        isVerified: Boolean = true,
        isSpoofAttempt: Boolean = false,
        rawText: String = ""
    ): Long {
        val entity = UpiTransactionEntity(
            payerName = payerName,
            amount = amount,
            upiApp = upiApp,
            packageName = packageName,
            referenceId = referenceId,
            timestamp = System.currentTimeMillis(),
            isVerified = isVerified,
            isSpoofAttempt = isSpoofAttempt,
            rawNotificationText = rawText
        )
        return dao.insertUpiTransaction(entity)
    }

    suspend fun checkRecentUpiNotification(windowMs: Long = 1500L): UpiTransactionEntity? {
        val now = System.currentTimeMillis()
        return dao.findRecentTransaction(now - windowMs, now + 500L)
    }

    suspend fun deleteUpiTransaction(id: Long) {
        dao.deleteUpiTransaction(id)
    }

    suspend fun clearAllTransactions() {
        dao.clearAllUpiTransactions()
    }

    // Khata Entries
    val allKhataEntries: Flow<List<KhataEntryEntity>> = dao.getAllKhataEntries()
    val unsettledKhataEntries: Flow<List<KhataEntryEntity>> = dao.getUnsettledKhataEntries()

    suspend fun addKhataEntry(
        customerName: String,
        amount: Double,
        entryType: String,
        note: String,
        customerPhone: String? = null
    ): Long {
        val entry = KhataEntryEntity(
            customerName = customerName,
            amount = amount,
            entryType = entryType,
            note = note,
            customerPhone = customerPhone,
            timestamp = System.currentTimeMillis(),
            isSettled = false
        )
        return dao.insertKhataEntry(entry)
    }

    suspend fun addBatchKhataEntries(entries: List<KhataEntryEntity>) {
        dao.insertKhataEntries(entries)
    }

    suspend fun toggleKhataSettlement(entry: KhataEntryEntity) {
        dao.updateKhataEntry(entry.copy(isSettled = !entry.isSettled))
    }

    suspend fun deleteKhataEntry(id: Long) {
        dao.deleteKhataEntry(id)
    }

    // Call Threats
    val allCallThreats: Flow<List<CallThreatEntity>> = dao.getAllCallThreats()
    val scamCallsCount: Flow<Int> = dao.getScamCallsCount()

    suspend fun recordCallThreat(
        phoneNumber: String,
        callerTag: String,
        transcript: String,
        isScam: Boolean,
        confidence: Float,
        triggerWords: List<String>,
        threatCategory: String,
        actionTaken: String
    ): Long {
        val entity = CallThreatEntity(
            phoneNumber = phoneNumber,
            callerTag = callerTag,
            transcript = transcript,
            isScam = isScam,
            confidence = confidence,
            triggerWords = triggerWords.joinToString(", "),
            threatCategory = threatCategory,
            actionTaken = actionTaken,
            timestamp = System.currentTimeMillis()
        )
        return dao.insertCallThreat(entity)
    }

    suspend fun deleteCallThreat(id: Long) {
        dao.deleteCallThreat(id)
    }

    suspend fun clearAllCallThreats() {
        dao.clearAllCallThreats()
    }

    // Acoustic Spoof Alerts
    val allAcousticAlerts: Flow<List<AcousticSpoofAlertEntity>> = dao.getAllAcousticAlerts()

    suspend fun recordAcousticSpoofAlert(
        app: String,
        amount: Double,
        hasNotification: Boolean,
        latencyMs: Long,
        confidence: Float,
        statusText: String
    ): Long {
        val entity = AcousticSpoofAlertEntity(
            detectedChimeApp = app,
            detectedAmount = amount,
            hasMatchingUpiNotification = hasNotification,
            latencyWindowMs = latencyMs,
            audioConfidence = confidence,
            timestamp = System.currentTimeMillis(),
            statusText = statusText
        )
        return dao.insertAcousticAlert(entity)
    }
}
