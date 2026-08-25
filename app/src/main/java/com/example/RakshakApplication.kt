package com.example

import android.app.Application
import com.example.data.local.RakshakDatabase
import com.example.data.local.entity.CallThreatEntity
import com.example.data.local.entity.KhataEntryEntity
import com.example.data.local.entity.UpiTransactionEntity
import com.example.data.repository.RakshakRepository
import com.example.engine.LocalTtsManager
import com.example.service.EmbeddedLocalDevServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RakshakApplication : Application() {

    lateinit var database: RakshakDatabase
        private set

    lateinit var repository: RakshakRepository
        private set

    lateinit var ttsManager: LocalTtsManager
        private set

    lateinit var devServer: EmbeddedLocalDevServer
        private set

    private val applicationScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        database = RakshakDatabase.getDatabase(this)
        repository = RakshakRepository(database.rakshakDao())
        ttsManager = LocalTtsManager(this)
        devServer = EmbeddedLocalDevServer(database.rakshakDao())
        devServer.start()

        // Start with clean on-device database; genuine SMS, UPI alerts and calls are recorded in real-time.
    }

    override fun onTerminate() {
        devServer.stop()
        ttsManager.shutdown()
        super.onTerminate()
    }
}
