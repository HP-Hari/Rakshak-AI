package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.RakshakDao
import com.example.data.local.entity.AcousticSpoofAlertEntity
import com.example.data.local.entity.CallThreatEntity
import com.example.data.local.entity.KhataEntryEntity
import com.example.data.local.entity.UpiTransactionEntity

@Database(
    entities = [
        UpiTransactionEntity::class,
        KhataEntryEntity::class,
        CallThreatEntity::class,
        AcousticSpoofAlertEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RakshakDatabase : RoomDatabase() {
    abstract fun rakshakDao(): RakshakDao

    companion object {
        @Volatile
        private var INSTANCE: RakshakDatabase? = null

        fun getDatabase(context: Context): RakshakDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RakshakDatabase::class.java,
                    "rakshak_secure_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
