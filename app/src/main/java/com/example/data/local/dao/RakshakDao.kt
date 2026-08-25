package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AcousticSpoofAlertEntity
import com.example.data.local.entity.CallThreatEntity
import com.example.data.local.entity.KhataEntryEntity
import com.example.data.local.entity.UpiTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RakshakDao {

    // UPI Transactions
    @Query("SELECT * FROM upi_transactions ORDER BY timestamp DESC")
    fun getAllUpiTransactions(): Flow<List<UpiTransactionEntity>>

    @Query("SELECT * FROM upi_transactions WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getTodayUpiTransactions(sinceTimestamp: Long): Flow<List<UpiTransactionEntity>>

    @Query("SELECT SUM(amount) FROM upi_transactions WHERE isVerified = 1 AND timestamp >= :sinceTimestamp")
    fun getTodayTotalCollection(sinceTimestamp: Long): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUpiTransaction(transaction: UpiTransactionEntity): Long

    @Query("SELECT * FROM upi_transactions WHERE timestamp >= :minTime AND timestamp <= :maxTime LIMIT 1")
    suspend fun findRecentTransaction(minTime: Long, maxTime: Long): UpiTransactionEntity?

    @Query("DELETE FROM upi_transactions WHERE id = :id")
    suspend fun deleteUpiTransaction(id: Long)

    @Query("DELETE FROM upi_transactions")
    suspend fun clearAllUpiTransactions()

    // Khata Ledger
    @Query("SELECT * FROM khata_entries ORDER BY timestamp DESC")
    fun getAllKhataEntries(): Flow<List<KhataEntryEntity>>

    @Query("SELECT * FROM khata_entries WHERE isSettled = 0 ORDER BY timestamp DESC")
    fun getUnsettledKhataEntries(): Flow<List<KhataEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKhataEntry(entry: KhataEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKhataEntries(entries: List<KhataEntryEntity>)

    @Update
    suspend fun updateKhataEntry(entry: KhataEntryEntity)

    @Query("DELETE FROM khata_entries WHERE id = :id")
    suspend fun deleteKhataEntry(id: Long)

    // Call Threats
    @Query("SELECT * FROM call_threats ORDER BY timestamp DESC")
    fun getAllCallThreats(): Flow<List<CallThreatEntity>>

    @Query("SELECT COUNT(*) FROM call_threats WHERE isScam = 1")
    fun getScamCallsCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallThreat(threat: CallThreatEntity): Long

    @Query("DELETE FROM call_threats WHERE id = :id")
    suspend fun deleteCallThreat(id: Long)

    @Query("DELETE FROM call_threats")
    suspend fun clearAllCallThreats()

    // Acoustic Spoof Alerts
    @Query("SELECT * FROM acoustic_spoof_alerts ORDER BY timestamp DESC")
    fun getAllAcousticAlerts(): Flow<List<AcousticSpoofAlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAcousticAlert(alert: AcousticSpoofAlertEntity): Long
}
