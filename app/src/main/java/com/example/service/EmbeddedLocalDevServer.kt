package com.example.service

import android.util.Log
import com.example.data.local.dao.RakshakDao
import com.example.engine.NpuInferenceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class EmbeddedLocalDevServer(private val dao: RakshakDao) {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val npuEngine = NpuInferenceEngine()

    val port = 8080
    var isRunning = false
        private set

    fun start() {
        if (isRunning) return
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                isRunning = true
                Log.d("DevServer", "Local Developer Bridge running at http://0.0.0.0:$port")

                while (isActive && serverSocket != null && !serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!isActive || serverSocket?.isClosed == true) break
                        Log.e("DevServer", "Socket accept exception: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("DevServer", "Failed to start local developer server: ${e.message}")
            } finally {
                isRunning = false
            }
        }
    }

    fun stop() {
        try {
            isRunning = false
            serverSocket?.close()
            serverSocket = null
            serverJob?.cancel()
            serverJob = null
        } catch (e: Exception) {
            Log.e("DevServer", "Error stopping local server: ${e.message}")
        }
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0]
                val path = parts[1].substringBefore("?")

                if (method == "OPTIONS") {
                    sendResponse(s.getOutputStream(), 204, "No Content", "text/plain", "")
                    return
                }

                when (path) {
                    "/api/ledger" -> {
                        val list = dao.getAllUpiTransactions().first()
                        val array = JSONArray()
                        list.forEach { item ->
                            array.put(JSONObject().apply {
                                put("id", item.id)
                                put("payer_name", item.payerName)
                                put("amount", item.amount)
                                put("upi_app", item.upiApp)
                                put("reference_id", item.referenceId)
                                put("timestamp", item.timestamp)
                                put("is_verified", item.isVerified)
                            })
                        }
                        sendResponse(s.getOutputStream(), 200, "OK", "application/json; charset=UTF-8", array.toString())
                    }
                    "/api/threats" -> {
                        val list = dao.getAllCallThreats().first()
                        val array = JSONArray()
                        list.forEach { item ->
                            array.put(JSONObject().apply {
                                put("id", item.id)
                                put("phone_number", item.phoneNumber)
                                put("caller_tag", item.callerTag)
                                put("transcript", item.transcript)
                                put("is_scam", item.isScam)
                                put("confidence", item.confidence)
                                put("trigger_words", item.triggerWords)
                                put("threat_category", item.threatCategory)
                                put("action_taken", item.actionTaken)
                                put("timestamp", item.timestamp)
                            })
                        }
                        sendResponse(s.getOutputStream(), 200, "OK", "application/json; charset=UTF-8", array.toString())
                    }
                    "/api/khata" -> {
                        val list = dao.getAllKhataEntries().first()
                        val array = JSONArray()
                        list.forEach { item ->
                            array.put(JSONObject().apply {
                                put("id", item.id)
                                put("customer_name", item.customerName)
                                put("amount", item.amount)
                                put("entry_type", item.entryType)
                                put("note", item.note)
                                put("timestamp", item.timestamp)
                                put("is_settled", item.isSettled)
                            })
                        }
                        sendResponse(s.getOutputStream(), 200, "OK", "application/json; charset=UTF-8", array.toString())
                    }
                    "/api/npu-status" -> {
                        val status = npuEngine.hardwareStatus
                        val json = JSONObject().apply {
                            put("is_npu_active", status.isNpuActive)
                            put("accelerator", status.acceleratorName)
                            put("runtime", status.runtime)
                            put("quantization", status.quantization)
                            put("latency_ms", status.averageLatencyMs)
                            put("memory_footprint_mb", status.memoryFootprintMb)
                            put("cloud_calls_total", status.cloudCallsTotal)
                            put("accuracy", status.onDeviceAccuracy)
                        }
                        sendResponse(s.getOutputStream(), 200, "OK", "application/json; charset=UTF-8", json.toString())
                    }
                    "/api/health" -> {
                        val json = JSONObject().apply {
                            put("status", "UP")
                            put("app", "Rakshak AI")
                            put("architecture", "100% On-Device Zero Cloud")
                            put("timestamp", System.currentTimeMillis())
                        }
                        sendResponse(s.getOutputStream(), 200, "OK", "application/json; charset=UTF-8", json.toString())
                    }
                    else -> {
                        sendResponse(s.getOutputStream(), 404, "Not Found", "application/json", "{\"error\":\"Not Found\"}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DevServer", "Error handling HTTP request: ${e.message}")
        }
    }

    private fun sendResponse(
        os: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: String
    ) {
        try {
            val bytes = body.toByteArray(Charsets.UTF_8)
            val header = buildString {
                append("HTTP/1.1 $statusCode $statusText\r\n")
                append("Content-Type: $contentType\r\n")
                append("Content-Length: ${bytes.size}\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
                append("Access-Control-Allow-Headers: Content-Type\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            os.write(header.toByteArray(Charsets.UTF_8))
            if (bytes.isNotEmpty()) {
                os.write(bytes)
            }
            os.flush()
        } catch (e: Exception) {
            Log.e("DevServer", "Error writing response: ${e.message}")
        }
    }
}
