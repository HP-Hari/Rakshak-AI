package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.RakshakApplication
import com.example.data.local.entity.KhataEntryEntity
import com.example.data.local.entity.UpiTransactionEntity
import com.example.data.model.KhataEntryType
import com.example.data.model.LiveAlertFeedItem
import com.example.data.model.NpuHardwareStatus
import com.example.data.model.ScamAnalysisResult
import com.example.data.model.SnapKhataItem
import com.example.data.model.ThreatCategory
import com.example.data.model.TransactionSource
import com.example.data.model.UnifiedTransactionItem
import com.example.engine.AcousticDetectionEvent
import com.example.engine.AcousticSpoofClassifier
import com.example.engine.GeminiAiService
import com.example.engine.LiveSpeechRecognizerManager
import com.example.engine.LocalTtsManager
import com.example.engine.NpuInferenceEngine
import com.example.engine.ScreenShareAlert
import com.example.engine.ScreenShareDetector
import com.example.service.LiveUpiNotificationEvent
import com.example.service.RakshakCallScreeningService
import com.example.service.RakshakNotificationListenerService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppProfile(val title: String, val subtitle: String) {
    SMART_VYAPAR("Smart Vyapar", "Real-Time UPI & Bank SMS • AI Voice Announcer"),
    CALL_GUARDIAN("Call Guardian", "AI Anti-Scam • Telecom Screener • Screen Shield")
}

enum class VyaparTab(val title: String) {
    UPI_LIVE("Transactions & AI SMS"),
    RECEIVED_TRACKER("Payment Analytics")
}

data class SmsAiAnalysisState(
    val smsText: String = "",
    val sender: String = "VK-SBIINB",
    val isAnalyzing: Boolean = false,
    val result: GeminiAiService.AiPaymentSmsResult? = null,
    val errorMessage: String? = null
)

data class LiveCallSimulationState(
    val isActive: Boolean = false,
    val callerNumber: String = "",
    val callerLabel: String = "",
    val transcriptStream: String = "",
    val latestChunk: String = "",
    val analysisResult: ScamAnalysisResult? = null,
    val aiAnalysis: GeminiAiService.AiCallAnalysis? = null,
    val isInterrupted: Boolean = false,
    val isRealTimeScreened: Boolean = false,
    val elapsedSeconds: Int = 0
)

data class LiveMicScanState(
    val isScanning: Boolean = false,
    val audioLevel: Float = 0f,
    val liveTranscript: String = "",
    val detectedCategory: ThreatCategory = ThreatCategory.SAFE_CALL,
    val threatConfidence: Float = 0f,
    val isThreatAlarmActive: Boolean = false,
    val statusMessage: String = "Ready to scan ambient audio / call speaker"
)

data class ManualThreatAnalysisState(
    val query: String = "",
    val isAnalyzing: Boolean = false,
    val result: ScamAnalysisResult? = null,
    val aiAnalysis: GeminiAiService.AiCallAnalysis? = null
)

class RakshakMainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as RakshakApplication
    private val repository = app.repository
    private val ttsManager = app.ttsManager
    val npuEngine = NpuInferenceEngine()
    val liveNpuLatencyMs: StateFlow<Float> = npuEngine.liveLatencyMs
    val acousticClassifier = AcousticSpoofClassifier()
    val geminiAiService = GeminiAiService()

    val liveSpeechRecognizer = LiveSpeechRecognizerManager(app)
    val isLiveSpeechListening: StateFlow<Boolean> = liveSpeechRecognizer.isListening
    val liveSpeechAudioRms: StateFlow<Float> = liveSpeechRecognizer.audioRms
    val liveSpeechTranscript: StateFlow<String> = liveSpeechRecognizer.liveTranscript
    val liveSpeechError: StateFlow<String?> = liveSpeechRecognizer.errorMessage

    private val _currentProfile = MutableStateFlow(AppProfile.SMART_VYAPAR)
    val currentProfile: StateFlow<AppProfile> = _currentProfile.asStateFlow()

    private val _activeVyaparTab = MutableStateFlow(VyaparTab.UPI_LIVE)
    val activeVyaparTab: StateFlow<VyaparTab> = _activeVyaparTab.asStateFlow()

    // Room DB StateFlows
    val allTransactions: StateFlow<List<UpiTransactionEntity>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayTotalCollection: StateFlow<Double?> = repository.todayTotalCollection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val khataEntries: StateFlow<List<KhataEntryEntity>> = repository.allKhataEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unified Transaction Stream (Auto-Populated by SMS & Push Notifications & Khata)
    val unifiedTransactionsFeed: StateFlow<List<UnifiedTransactionItem>> = combine(
        allTransactions,
        khataEntries
    ) { upiList, khataList ->
        val upiItems = upiList.map { entity ->
            val isSms = entity.packageName.contains("sms") || entity.packageName.contains("telephony") || entity.upiApp.contains("SMS")
            val source = if (isSms) TransactionSource.BANK_SMS else TransactionSource.UPI_NOTIFICATION
            UnifiedTransactionItem(
                id = "UPI_${entity.id}",
                title = entity.payerName,
                subtitle = if (isSms) "Auto-Populated via Bank SMS Sentry" else "Auto-Populated via ${entity.upiApp} Sentry",
                amount = entity.amount,
                isCreditInflow = true,
                source = source,
                sourceLabel = entity.upiApp,
                referenceId = entity.referenceId,
                timestamp = entity.timestamp,
                isVerified = entity.isVerified,
                rawText = entity.rawNotificationText
            )
        }

        val khataItems = khataList.map { entity ->
            val isPayment = entity.entryType == KhataEntryType.RECEIVED_PAYMENT.name
            UnifiedTransactionItem(
                id = "KHATA_${entity.id}",
                title = entity.customerName,
                subtitle = if (isPayment) "Khata Jama (Payment Received)" else "Khata Udhaar (${entity.note})",
                amount = entity.amount,
                isCreditInflow = isPayment,
                source = TransactionSource.KHATA_ENTRY,
                sourceLabel = if (isPayment) "Khata Jama" else "Khata Udhaar",
                referenceId = "KHATA#${entity.id}",
                timestamp = entity.timestamp,
                isVerified = true,
                rawText = "Bahi Khata: ${entity.customerName} • ₹${entity.amount.toInt()} (${entity.note})"
            )
        }

        (upiItems + khataItems).sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Ledger Balance & Received Tracker Computations (In-Device SQLite)
    val netLedgerBalance: StateFlow<Double> = combine(
        allTransactions,
        khataEntries
    ) { upiList, khataList ->
        val totalReceivedUpi = upiList.filter { it.isVerified }.sumOf { it.amount }
        val totalKhataJama = khataList.filter { it.entryType == KhataEntryType.RECEIVED_PAYMENT.name }.sumOf { it.amount }
        val totalUnsettledUdhaar = khataList.filter { !it.isSettled && it.entryType == KhataEntryType.GAVE_CREDIT.name }.sumOf { it.amount }
        (totalReceivedUpi + totalKhataJama) - totalUnsettledUdhaar
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalInflowReceived: StateFlow<Double> = combine(
        allTransactions,
        khataEntries
    ) { upiList, khataList ->
        val totalReceivedUpi = upiList.filter { it.isVerified }.sumOf { it.amount }
        val totalKhataJama = khataList.filter { it.entryType == KhataEntryType.RECEIVED_PAYMENT.name }.sumOf { it.amount }
        totalReceivedUpi + totalKhataJama
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Received Amounts Tracker: Today, This Week, This Month, All Time
    val todayReceivedTotal: StateFlow<Double> = allTransactions.map { list ->
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        list.filter { it.isVerified && it.timestamp >= startOfDay }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val thisWeekReceivedTotal: StateFlow<Double> = allTransactions.map { list ->
        val startOfWeek = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        list.filter { it.isVerified && it.timestamp >= startOfWeek }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val thisMonthReceivedTotal: StateFlow<Double> = allTransactions.map { list ->
        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        list.filter { it.isVerified && it.timestamp >= startOfMonth }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val allTimeReceivedTotal: StateFlow<Double> = allTransactions.map { list ->
        list.filter { it.isVerified }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Breakdown By Source
    val smsReceivedTotal: StateFlow<Double> = allTransactions.map { list ->
        list.filter { it.isVerified && (it.packageName.contains("sms") || it.upiApp.contains("SMS")) }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val upiAppsReceivedTotal: StateFlow<Double> = allTransactions.map { list ->
        list.filter { it.isVerified && !it.packageName.contains("sms") && !it.upiApp.contains("SMS") }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val khataJamaTotal: StateFlow<Double> = khataEntries.map { list ->
        list.filter { it.entryType == KhataEntryType.RECEIVED_PAYMENT.name }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val averageTransactionSize: StateFlow<Double> = allTransactions.map { list ->
        val verified = list.filter { it.isVerified }
        if (verified.isEmpty()) 0.0 else verified.sumOf { it.amount } / verified.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val highestTransactionAmount: StateFlow<Double> = allTransactions.map { list ->
        list.filter { it.isVerified }.maxOfOrNull { it.amount } ?: 0.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalReceivedTransactionsCount: StateFlow<Int> = allTransactions.map { list ->
        list.count { it.isVerified }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalOutflowUdhaar: StateFlow<Double> = khataEntries.map { list ->
        list.filter { !it.isSettled && it.entryType == KhataEntryType.GAVE_CREDIT.name }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Latest Live Alert Banner for SMS / Notification Events
    private val _latestAlertFeedItem = MutableStateFlow<LiveAlertFeedItem?>(null)
    val latestAlertFeedItem: StateFlow<LiveAlertFeedItem?> = _latestAlertFeedItem.asStateFlow()

    val callThreats = repository.allCallThreats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scamCount = repository.scamCallsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val acousticEvents: StateFlow<List<AcousticDetectionEvent>> = acousticClassifier.detectionEvents

    val activeScreenShield: StateFlow<ScreenShareAlert?> = ScreenShareDetector.activeShieldAlert

    // Live Call Simulation State (Call Guardian)
    private val _callSimulationState = MutableStateFlow(LiveCallSimulationState())
    val callSimulationState: StateFlow<LiveCallSimulationState> = _callSimulationState.asStateFlow()
    private var callJob: Job? = null

    // Live Mic Audio Scanner State
    private val _liveMicScanState = MutableStateFlow(LiveMicScanState())
    val liveMicScanState: StateFlow<LiveMicScanState> = _liveMicScanState.asStateFlow()
    private var micScanJob: Job? = null

    // Manual Scam / Number Checker State
    private val _manualThreatState = MutableStateFlow(ManualThreatAnalysisState())
    val manualThreatState: StateFlow<ManualThreatAnalysisState> = _manualThreatState.asStateFlow()

    // SMS AI Payment Parser & Voice Announcer State
    private val _smsAiState = MutableStateFlow(SmsAiAnalysisState())
    val smsAiState: StateFlow<SmsAiAnalysisState> = _smsAiState.asStateFlow()

    // Voice Khata State
    private val _isVoiceRecording = MutableStateFlow(false)
    val isVoiceRecording: StateFlow<Boolean> = _isVoiceRecording.asStateFlow()
    private val _voiceTranscript = MutableStateFlow("")
    val voiceTranscript: StateFlow<String> = _voiceTranscript.asStateFlow()
    private val _parsedVoiceKhata = MutableStateFlow<KhataEntryEntity?>(null)
    val parsedVoiceKhata: StateFlow<KhataEntryEntity?> = _parsedVoiceKhata.asStateFlow()

    // Snap-to-Khata Vision State
    private val _isSnapKhataLoading = MutableStateFlow(false)
    val isSnapKhataLoading: StateFlow<Boolean> = _isSnapKhataLoading.asStateFlow()
    private val _extractedSnapItems = MutableStateFlow<List<SnapKhataItem>>(emptyList())
    val extractedSnapItems: StateFlow<List<SnapKhataItem>> = _extractedSnapItems.asStateFlow()

    // Dev Server Dialog & Info
    private val _isDevServerSheetOpen = MutableStateFlow(false)
    val isDevServerSheetOpen: StateFlow<Boolean> = _isDevServerSheetOpen.asStateFlow()

    // One-Time Setup & Background Defense State (Stored in SharedPreferences)
    private val sharedPrefs = app.getSharedPreferences("rakshak_security_prefs", Context.MODE_PRIVATE)
    private val _isInitialSetupDone = MutableStateFlow(sharedPrefs.getBoolean("has_completed_initial_setup", false))
    val isInitialSetupDone: StateFlow<Boolean> = _isInitialSetupDone.asStateFlow()

    fun markInitialSetupComplete() {
        sharedPrefs.edit().putBoolean("has_completed_initial_setup", true).apply()
        _isInitialSetupDone.value = true
    }

    init {
        // 1. Observe real-time UPI notifications from NotificationListenerService
        viewModelScope.launch {
            RakshakNotificationListenerService.liveUpiFlow.collect { event ->
                // Auto acoustic validation can latch to this timestamp
            }
        }

        // 2. Observe real-time CallScreeningService events
        viewModelScope.launch {
            RakshakCallScreeningService.callEvents.collect { event ->
                handleIncomingCallScreeningEvent(event)
            }
        }

        // 3. Observe real-time CallBroadcastReceiver phone state events
        viewModelScope.launch {
            com.example.service.CallBroadcastReceiver.fraudCallEvents.collect { (number, fraudResult) ->
                handleIncomingFraudCallBroadcastEvent(number, fraudResult)
            }
        }
    }

    private fun handleIncomingFraudCallBroadcastEvent(
        phoneNumber: String,
        fraudResult: com.example.service.CallBroadcastReceiver.RealTimeCallFraudResult
    ) {
        if (fraudResult.isFraudulent) {
            _currentProfile.value = AppProfile.CALL_GUARDIAN
            callJob?.cancel()

            val analysis = ScamAnalysisResult(
                isScam = true,
                confidence = fraudResult.confidence,
                threatCategory = ThreatCategory.entries.find { it.name == fraudResult.threatCategory } ?: ThreatCategory.URGENT_FINE,
                triggerWords = fraudResult.triggerFlags,
                reasoning = fraudResult.reasoning,
                suggestedAction = fraudResult.recommendedAction
            )

            _callSimulationState.value = LiveCallSimulationState(
                isActive = true,
                callerNumber = phoneNumber,
                callerLabel = "⚠️ ${fraudResult.callerProfile}",
                transcriptStream = "🚨 REAL-TIME CALL INTERCEPT: ${fraudResult.reasoning}\n👉 Suggested Action: ${fraudResult.recommendedAction}",
                analysisResult = analysis,
                isInterrupted = true,
                isRealTimeScreened = true
            )
        }
    }

    private fun handleIncomingCallScreeningEvent(event: com.example.service.IncomingCallEvent) {
        _currentProfile.value = AppProfile.CALL_GUARDIAN
        callJob?.cancel()

        val label = event.callerName?.takeIf { it.isNotBlank() } ?: "Incoming (${event.phoneNumber})"
        val initialSnippet = if (event.isAutoBlocked) {
            "🚨 FRAUD DETECTED: Caller number matches known high-risk spoof prefix. CallScreeningService blocked ringing."
        } else {
            "🛡️ Unknown caller connected. Telecom hook active — on-device NPU listening for OTP requests, fake arrest threats, and APK download prompts."
        }

        val analysis = if (event.isAutoBlocked) {
            ScamAnalysisResult(
                isScam = true,
                confidence = event.riskScore,
                threatCategory = ThreatCategory.URGENT_FINE,
                triggerWords = listOf("spoofed_origin", "telecom_interception"),
                reasoning = "Call auto-blocked by Rakshak Telecom CallScreeningService.",
                suggestedAction = "REJECT & REPORT TO TRAI"
            )
        } else null

        _callSimulationState.value = LiveCallSimulationState(
            isActive = true,
            callerNumber = event.phoneNumber,
            callerLabel = label,
            transcriptStream = initialSnippet,
            analysisResult = analysis,
            isInterrupted = event.isAutoBlocked,
            isRealTimeScreened = true
        )
    }

    fun setProfile(profile: AppProfile) {
        _currentProfile.value = profile
    }

    fun setVyaparTab(tab: VyaparTab) {
        _activeVyaparTab.value = tab
    }

    fun toggleDevServerSheet(open: Boolean) {
        _isDevServerSheetOpen.value = open
    }

    // ----------------------------------------------------
    // PROFILE B: SMART VYAPAR ACTIONS
    // ----------------------------------------------------

    fun triggerSimulatedUpiNotification(
        payer: String = "Ravi Teja",
        amount: Double = 350.0,
        app: String = "PhonePe"
    ) {
        viewModelScope.launch {
            val pkg = when (app) {
                "PhonePe" -> "com.phonepe.app"
                "Paytm" -> "net.one97.paytm"
                "Google Pay" -> "com.google.android.apps.nbu.paisa.user"
                else -> "com.bharatpe.app"
            }
            val raw = "$app: Received ₹${amount.toInt()} from $payer on UPI QR"
            val extracted = npuEngine.extractUpiPayment(pkg, app, raw)

            if (extracted != null) {
                val now = System.currentTimeMillis()
                val event = LiveUpiNotificationEvent(
                    payerName = extracted.payerName,
                    amount = extracted.amount,
                    upiApp = extracted.upiApp,
                    packageName = pkg,
                    referenceId = extracted.referenceId,
                    rawText = extracted.rawText,
                    timestamp = now
                )
                RakshakNotificationListenerService.broadcastManualUpiPayment(event)

                repository.recordUpiTransaction(
                    payerName = extracted.payerName,
                    amount = extracted.amount,
                    upiApp = extracted.upiApp,
                    packageName = pkg,
                    referenceId = extracted.referenceId,
                    isVerified = true,
                    isSpoofAttempt = false,
                    rawText = extracted.rawText
                )

                _latestAlertFeedItem.value = LiveAlertFeedItem(
                    id = "NOTIF_${now}",
                    title = "UPI Alert Processed ($app)",
                    description = "₹${extracted.amount.toInt()} received from ${extracted.payerName}",
                    timestamp = now,
                    source = TransactionSource.UPI_NOTIFICATION,
                    amount = extracted.amount
                )

                ttsManager.speakPaymentReceived(extracted.amount, extracted.payerName, extracted.upiApp)
            }
        }
    }

    fun simulateIncomingBankSms(
        bankName: String = "HDFC Bank",
        payer: String = "Suresh Patel",
        amount: Double = 650.0
    ) {
        viewModelScope.launch {
            val sender = when (bankName) {
                "HDFC Bank" -> "VK-HDFCBK"
                "SBI" -> "AD-SBIINB"
                "ICICI Bank" -> "VM-ICICIB"
                "Axis Bank" -> "AX-AXISBK"
                else -> "BP-KOTAKB"
            }
            val refId = "928" + (System.currentTimeMillis() % 100000000)
            val dateStr = SimpleDateFormat("dd-MMM-yy", Locale.getDefault()).format(Date())
            val rawBody = "Dear Customer, A/c *4589 is credited with Rs $amount on $dateStr by UPI/$refId/$payer. Net Avail Bal: Rs 48,250.00 - $bankName"

            val extracted = npuEngine.extractUpiPayment("sms.telephony", "SMS from $sender", rawBody)
            if (extracted != null) {
                val now = System.currentTimeMillis()
                repository.recordUpiTransaction(
                    payerName = extracted.payerName,
                    amount = extracted.amount,
                    upiApp = "Bank SMS ($bankName)",
                    packageName = "sms.telephony",
                    referenceId = extracted.referenceId,
                    isVerified = true,
                    isSpoofAttempt = false,
                    rawText = rawBody
                )

                _latestAlertFeedItem.value = LiveAlertFeedItem(
                    id = "SMS_${now}",
                    title = "Bank SMS Intercepted ($bankName)",
                    description = "₹${extracted.amount.toInt()} credited from ${extracted.payerName}",
                    timestamp = now,
                    source = TransactionSource.BANK_SMS,
                    amount = extracted.amount
                )

                ttsManager.speakPaymentReceived(extracted.amount, extracted.payerName, "Bank SMS")
            }
        }
    }

    fun dismissLatestAlert() {
        _latestAlertFeedItem.value = null
    }

    fun testAcousticSoundboxPayment(isLegitimate: Boolean, soundboxApp: String = "Paytm Soundbox", amount: Double = 500.0) {
        viewModelScope.launch {
            if (isLegitimate) {
                // 1. Genuine payment: notification arrives first
                triggerSimulatedUpiNotification(
                    payer = "Karan Johar",
                    amount = amount,
                    app = "Paytm"
                )
                delay(300) // Soundbox chimes within 300ms of notification
                val event = acousticClassifier.evaluateAudioPayment(
                    soundboxApp = soundboxApp,
                    amount = amount,
                    lastNotificationTimestamp = RakshakNotificationListenerService.lastNotificationTimestamp
                )
                repository.recordAcousticSpoofAlert(
                    app = soundboxApp,
                    amount = amount,
                    hasNotification = true,
                    latencyMs = event.latencyMs,
                    confidence = 0.98f,
                    statusText = event.message
                )
            } else {
                // 2. Spoof attack: Scammer plays fake audio without bank notification (delta > 800ms)
                val event = acousticClassifier.evaluateAudioPayment(
                    soundboxApp = soundboxApp,
                    amount = amount,
                    lastNotificationTimestamp = null // No notification!
                )
                repository.recordAcousticSpoofAlert(
                    app = soundboxApp,
                    amount = amount,
                    hasNotification = false,
                    latencyMs = 99999L,
                    confidence = 0.95f,
                    statusText = event.message
                )
                ttsManager.speakSpoofAlert(amount)
            }
        }
    }

    fun startVoiceKhataRecording() {
        _isVoiceRecording.value = true
        _voiceTranscript.value = ""
        _parsedVoiceKhata.value = null

        liveSpeechRecognizer.onPartialWordCallback = { text: String ->
            _voiceTranscript.value = text
        }
        liveSpeechRecognizer.onFinalSpeechCallback = { text: String ->
            _voiceTranscript.value = text
            viewModelScope.launch {
                val result = npuEngine.parseVoiceKhataTranscript(text)
                val entity = KhataEntryEntity(
                    customerName = result.customerName,
                    amount = result.amount,
                    entryType = result.entryType,
                    note = result.note,
                    timestamp = System.currentTimeMillis(),
                    isSettled = result.entryType == KhataEntryType.RECEIVED_PAYMENT.name
                )
                _parsedVoiceKhata.value = entity
                _isVoiceRecording.value = false
            }
        }
        liveSpeechRecognizer.startListening()
    }

    fun stopVoiceKhataRecording() {
        liveSpeechRecognizer.stopListening()
        val text = _voiceTranscript.value
        if (text.isNotBlank()) {
            viewModelScope.launch {
                val result = npuEngine.parseVoiceKhataTranscript(text)
                val entity = KhataEntryEntity(
                    customerName = result.customerName,
                    amount = result.amount,
                    entryType = result.entryType,
                    note = result.note,
                    timestamp = System.currentTimeMillis(),
                    isSettled = result.entryType == KhataEntryType.RECEIVED_PAYMENT.name
                )
                _parsedVoiceKhata.value = entity
            }
        }
        _isVoiceRecording.value = false
    }

    fun saveVoiceKhataEntry() {
        val entry = _parsedVoiceKhata.value ?: return
        viewModelScope.launch {
            repository.addKhataEntry(
                customerName = entry.customerName,
                amount = entry.amount,
                entryType = entry.entryType,
                note = entry.note
            )
            _parsedVoiceKhata.value = null
            _voiceTranscript.value = ""
            ttsManager.speakCustom("Added ${entry.customerName} khata for ₹${entry.amount.toInt()}")
        }
    }

    fun cancelVoiceKhata() {
        _isVoiceRecording.value = false
        _voiceTranscript.value = ""
        _parsedVoiceKhata.value = null
    }

    fun runSnapToKhataOcr(imageSource: String = "camera_frame") {
        viewModelScope.launch {
            _isSnapKhataLoading.value = true
            val items = npuEngine.parseSnapKhataImage(imageSource)
            _extractedSnapItems.value = items
            _isSnapKhataLoading.value = false
        }
    }

    fun saveAllSnapKhataItems() {
        val items = _extractedSnapItems.value
        if (items.isEmpty()) return
        viewModelScope.launch {
            val entities = items.map {
                KhataEntryEntity(
                    customerName = it.customerName,
                    amount = it.amount,
                    entryType = KhataEntryType.GAVE_CREDIT.name,
                    note = it.itemsDescription,
                    timestamp = System.currentTimeMillis(),
                    isSettled = false
                )
            }
            repository.addBatchKhataEntries(entities)
            _extractedSnapItems.value = emptyList()
            ttsManager.speakCustom("Saved ${entities.size} khata entries from notebook photograph.")
        }
    }

    fun dismissSnapKhata() {
        _extractedSnapItems.value = emptyList()
        _isSnapKhataLoading.value = false
    }

    fun toggleKhataSettlement(entry: KhataEntryEntity) {
        viewModelScope.launch {
            repository.toggleKhataSettlement(entry)
        }
    }

    fun deleteKhataEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteKhataEntry(id)
        }
    }

    fun recordDirectKhataEntry(
        customerName: String,
        amount: Double,
        entryType: String,
        note: String
    ) {
        viewModelScope.launch {
            repository.addKhataEntry(
                customerName = customerName.ifBlank { "Cash Customer" },
                amount = amount,
                entryType = entryType,
                note = note.ifBlank { "General Purchase" }
            )
            ttsManager.speakCustom("Khata entry recorded for $customerName: Rupees ${amount.toInt()}")
        }
    }

    fun deleteUpiTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteUpiTransaction(id)
        }
    }

    fun clearAllTransactions() {
        viewModelScope.launch {
            repository.clearAllTransactions()
            ttsManager.speakCustom("Transaction history cleared.")
        }
    }

    fun deleteCallThreat(id: Long) {
        viewModelScope.launch {
            repository.deleteCallThreat(id)
        }
    }

    fun clearAllCallThreats() {
        viewModelScope.launch {
            repository.clearAllCallThreats()
        }
    }

    // Direct Real UPI Payment Recording
    fun recordDirectUpiPayment(
        payerName: String,
        amount: Double,
        upiApp: String,
        referenceId: String = "UPI${System.currentTimeMillis() % 10000000000L}"
    ) {
        viewModelScope.launch {
            val pkg = when (upiApp) {
                "PhonePe" -> "com.phonepe.app"
                "Paytm" -> "net.one97.paytm"
                "Google Pay" -> "com.google.android.apps.nbu.paisa.user"
                else -> "in.org.npci.upiapp"
            }
            val raw = "$upiApp: Received ₹${amount.toInt()} from $payerName on UPI (Ref $referenceId)"

            repository.recordUpiTransaction(
                payerName = payerName,
                amount = amount,
                upiApp = upiApp,
                packageName = pkg,
                referenceId = referenceId,
                isVerified = true,
                isSpoofAttempt = false,
                rawText = raw
            )

            // Announce vernacular soundbox confirmation
            ttsManager.speakPaymentReceived(amount, payerName, upiApp)
        }
    }

    fun replaySoundboxAnnouncement(amount: Double, payerName: String, provider: String) {
        ttsManager.speakBankPaymentAnnouncement(amount, payerName, provider)
    }

    fun analyzeAndAnnounceSmsWithAi(smsText: String, sender: String = "VK-SBIINB") {
        if (smsText.isBlank()) return
        viewModelScope.launch {
            _smsAiState.value = SmsAiAnalysisState(
                smsText = smsText,
                sender = sender,
                isAnalyzing = true
            )
            try {
                val result = geminiAiService.parsePaymentSmsWithAi(smsBody = smsText, sender = sender)
                _smsAiState.value = SmsAiAnalysisState(
                    smsText = smsText,
                    sender = sender,
                    isAnalyzing = false,
                    result = result
                )

                if (result.isCreditPayment && result.amount > 0) {
                    val now = System.currentTimeMillis()
                    // Record in Room DB
                    repository.recordUpiTransaction(
                        payerName = result.payerName,
                        amount = result.amount,
                        upiApp = result.bankName,
                        packageName = "sms.telephony",
                        referenceId = result.referenceId,
                        isVerified = true,
                        isSpoofAttempt = false,
                        rawText = smsText
                    )

                    // Vocalize announcement: Person Name, Bank Name, and Money
                    ttsManager.speakBankPaymentAnnouncement(
                        amount = result.amount,
                        payerName = result.payerName,
                        bankName = result.bankName
                    )

                    // Post Live Alert Banner
                    _latestAlertFeedItem.value = LiveAlertFeedItem(
                        id = "AI_SMS_$now",
                        title = "AI Extracted: ${result.bankName}",
                        description = "₹${result.amount.toInt()} received from ${result.payerName}",
                        timestamp = now,
                        source = TransactionSource.BANK_SMS,
                        amount = result.amount
                    )
                } else {
                    ttsManager.speakCustom(result.vocalAnnouncement)
                }
            } catch (e: Exception) {
                _smsAiState.value = _smsAiState.value.copy(
                    isAnalyzing = false,
                    errorMessage = "Error: ${e.message}"
                )
            }
        }
    }

    fun resetSmsAiState() {
        _smsAiState.value = SmsAiAnalysisState()
    }

    fun simulateBankSmsReceived(
        payerName: String = "Ramesh Kumar",
        amount: Double = 450.0,
        bankName: String = "HDFC Bank"
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val refId = "UTR${now % 10000000000L}"
            val rawSms = "Dear UPI User, A/C *8921 credited by Rs $amount on ${SimpleDateFormat("dd-MMM-yy", Locale.getDefault()).format(Date())} by $payerName (UPI Ref $refId) - $bankName"
            
            repository.recordUpiTransaction(
                payerName = payerName,
                amount = amount,
                upiApp = "Bank SMS ($bankName)",
                packageName = "sms.telephony",
                referenceId = refId,
                isVerified = true,
                isSpoofAttempt = false,
                rawText = rawSms
            )

            _latestAlertFeedItem.value = LiveAlertFeedItem(
                id = "SMS_$now",
                title = "Bank SMS Processed ($bankName)",
                description = "₹${amount.toInt()} credited from $payerName • Ref $refId",
                timestamp = now,
                source = TransactionSource.BANK_SMS,
                amount = amount
            )

            ttsManager.speakPaymentReceived(amount, payerName, "Bank SMS")
        }
    }

    fun simulateUpiNotificationReceived(
        payerName: String = "Priya Sharma",
        amount: Double = 750.0,
        upiApp: String = "PhonePe"
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val refId = "T${now % 10000000000L}"
            val pkg = when (upiApp) {
                "PhonePe" -> "com.phonepe.app"
                "Paytm" -> "net.one97.paytm"
                "Google Pay" -> "com.google.android.apps.nbu.paisa.user"
                else -> "in.org.npci.upiapp"
            }
            val rawPush = "$upiApp: Received ₹${amount.toInt()} from $payerName (UPI Ref $refId)"

            repository.recordUpiTransaction(
                payerName = payerName,
                amount = amount,
                upiApp = upiApp,
                packageName = pkg,
                referenceId = refId,
                isVerified = true,
                isSpoofAttempt = false,
                rawText = rawPush
            )

            _latestAlertFeedItem.value = LiveAlertFeedItem(
                id = "NOTIF_$now",
                title = "UPI Alert Processed ($upiApp)",
                description = "₹${amount.toInt()} received from $payerName • Ref $refId",
                timestamp = now,
                source = TransactionSource.UPI_NOTIFICATION,
                amount = amount
            )

            ttsManager.speakPaymentReceived(amount, payerName, upiApp)
        }
    }

    // ----------------------------------------------------
    // PROFILE A: CALL GUARDIAN (ANTI-SCAM) REAL-TIME ACTIONS
    // ----------------------------------------------------

    fun startLiveVoiceCallListener(
        phoneNumber: String = "+91 98200 54321",
        callerLabel: String = "Incoming Call (Live Audio)"
    ) {
        callJob?.cancel()
        _currentProfile.value = AppProfile.CALL_GUARDIAN
        _callSimulationState.value = LiveCallSimulationState(
            isActive = true,
            callerNumber = phoneNumber,
            callerLabel = callerLabel,
            transcriptStream = "Listening to live call words via speech recognizer...",
            isRealTimeScreened = true
        )

        liveSpeechRecognizer.onPartialWordCallback = { partial: String ->
            val fullText = liveSpeechRecognizer.fullSessionTranscript.value
            val displayTranscript = if (fullText.isNotBlank()) "$fullText $partial" else partial
            _callSimulationState.value = _callSimulationState.value.copy(
                transcriptStream = displayTranscript,
                latestChunk = partial.split(" ").lastOrNull() ?: ""
            )
            evaluateSpokenWordsRealTime(displayTranscript, phoneNumber, callerLabel, isFinal = false)
        }

        liveSpeechRecognizer.onFinalSpeechCallback = { finalPhrase: String ->
            val fullText = liveSpeechRecognizer.fullSessionTranscript.value
            _callSimulationState.value = _callSimulationState.value.copy(
                transcriptStream = fullText.ifBlank { finalPhrase }
            )
            evaluateSpokenWordsRealTime(fullText.ifBlank { finalPhrase }, phoneNumber, callerLabel, isFinal = false)
        }

        liveSpeechRecognizer.startListening(continuous = true)
    }

    fun stopLiveVoiceCallListener() {
        liveSpeechRecognizer.stopListening()
        val stream = _callSimulationState.value.transcriptStream
        if (stream.isNotBlank()) {
            evaluateSpokenWordsRealTime(
                stream,
                _callSimulationState.value.callerNumber,
                _callSimulationState.value.callerLabel,
                isFinal = true
            )
        }
    }

    private fun evaluateSpokenWordsRealTime(
        transcript: String,
        phoneNumber: String,
        callerLabel: String,
        isFinal: Boolean = false
    ) {
        viewModelScope.launch {
            val aiResult = geminiAiService.analyzeCallOrNumberWithAi(phoneNumber, transcript)
            val category = when (aiResult.threatCategory) {
                "OTP_THEFT" -> ThreatCategory.OTP_THEFT
                "APK_SIDELOAD" -> ThreatCategory.APK_SIDELOAD
                "URGENT_FINE" -> ThreatCategory.URGENT_FINE
                "LOTTERY_PRIZE" -> ThreatCategory.LOTTERY_PRIZE
                "JOB_SCAM" -> ThreatCategory.LOTTERY_PRIZE
                else -> ThreatCategory.SAFE_CALL
            }

            val legacyResult = ScamAnalysisResult(
                isScam = aiResult.isScam,
                confidence = aiResult.confidence,
                threatCategory = category,
                triggerWords = aiResult.redFlags,
                reasoning = aiResult.reasoning,
                suggestedAction = aiResult.suggestedAction
            )

            val wasAlreadyInterrupted = _callSimulationState.value.isInterrupted
            _callSimulationState.value = _callSimulationState.value.copy(
                analysisResult = legacyResult,
                aiAnalysis = aiResult,
                isInterrupted = aiResult.isScam
            )

            // Trigger Important Notice voice alert & floating overlay if newly detected threat
            if (aiResult.isScam && !wasAlreadyInterrupted) {
                val noticeMsg = if (aiResult.importantNotice.isNotBlank()) aiResult.importantNotice
                else "Social engineering trick detected. Disconnect immediately."
                ttsManager.speakImportantNotice(noticeMsg)

                // Pop up awareness overlay
                val overlayData = com.example.ui.overlay.FraudAlertOverlayManager.OverlayData(
                    callerNumber = phoneNumber,
                    archetype = aiResult.callerProfile,
                    confidence = aiResult.confidence,
                    stressLevel = 0.95f,
                    reasoning = aiResult.reasoning,
                    recommendedAction = aiResult.suggestedAction,
                    acousticMarkers = aiResult.redFlags
                )
                com.example.ui.overlay.FraudAlertOverlayManager.getInstance(getApplication()).showFraudAlertOverlay(overlayData)

                // Record to Room DB
                repository.recordCallThreat(
                    phoneNumber = phoneNumber,
                    callerTag = callerLabel,
                    transcript = transcript,
                    isScam = true,
                    confidence = aiResult.confidence,
                    triggerWords = aiResult.redFlags,
                    threatCategory = category.name,
                    actionTaken = "AUTO-INTERRUPTED: ${aiResult.suggestedAction}"
                )
            } else if (isFinal && !aiResult.isScam) {
                repository.recordCallThreat(
                    phoneNumber = phoneNumber,
                    callerTag = callerLabel,
                    transcript = transcript,
                    isScam = false,
                    confidence = aiResult.confidence,
                    triggerWords = emptyList(),
                    threatCategory = ThreatCategory.SAFE_CALL.name,
                    actionTaken = "VERIFIED SAFE CONVERSATION"
                )
            }
        }
    }

    fun startLiveMicThreatScan() {
        micScanJob?.cancel()
        _liveMicScanState.value = LiveMicScanState(
            isScanning = true,
            audioLevel = 0.4f,
            statusMessage = "Listening on device microphone & speakerphone..."
        )

        liveSpeechRecognizer.onPartialWordCallback = { text: String ->
            _liveMicScanState.value = _liveMicScanState.value.copy(
                liveTranscript = text,
                statusMessage = "Transcribing spoken words in real time..."
            )
            viewModelScope.launch {
                val ai = geminiAiService.analyzeCallOrNumberWithAi("Live Mic", text)
                if (ai.isScam) {
                    _liveMicScanState.value = _liveMicScanState.value.copy(
                        isThreatAlarmActive = true,
                        threatConfidence = ai.confidence,
                        statusMessage = "🚨 THREAT DETECTED: ${ai.callerProfile}"
                    )
                    ttsManager.speakImportantNotice(ai.importantNotice)
                }
            }
        }
        liveSpeechRecognizer.startListening()

        micScanJob = viewModelScope.launch {
            while (_liveMicScanState.value.isScanning) {
                delay(200)
                val rms = liveSpeechRecognizer.audioRms.value
                val level = if (rms > 0.05f) rms else (0.15f + (Math.sin(System.currentTimeMillis() / 400.0).toFloat() * 0.1f)).coerceIn(0.05f, 0.9f)
                _liveMicScanState.value = _liveMicScanState.value.copy(
                    audioLevel = level
                )
            }
        }
    }

    fun stopLiveMicThreatScan() {
        liveSpeechRecognizer.stopListening()
        micScanJob?.cancel()
        _liveMicScanState.value = LiveMicScanState(
            isScanning = false,
            audioLevel = 0f,
            statusMessage = "Scanner stopped"
        )
    }

    fun analyzeManualThreatText(query: String, context: String = "") {
        if (query.isBlank()) return
        viewModelScope.launch {
            _manualThreatState.value = ManualThreatAnalysisState(query = query, isAnalyzing = true)

            // Run Gemini AI Analysis (with local NPU fallback)
            val aiResult = geminiAiService.analyzeCallOrNumberWithAi(query, context)

            val category = when (aiResult.threatCategory) {
                "OTP_THEFT" -> ThreatCategory.OTP_THEFT
                "APK_SIDELOAD" -> ThreatCategory.APK_SIDELOAD
                "URGENT_FINE" -> ThreatCategory.URGENT_FINE
                "LOTTERY_PRIZE" -> ThreatCategory.LOTTERY_PRIZE
                "JOB_SCAM" -> ThreatCategory.LOTTERY_PRIZE
                else -> ThreatCategory.SAFE_CALL
            }

            val legacyResult = ScamAnalysisResult(
                isScam = aiResult.isScam,
                confidence = aiResult.confidence,
                threatCategory = category,
                triggerWords = aiResult.redFlags,
                reasoning = aiResult.reasoning,
                suggestedAction = aiResult.suggestedAction
            )

            _manualThreatState.value = ManualThreatAnalysisState(
                query = query,
                isAnalyzing = false,
                result = legacyResult,
                aiAnalysis = aiResult
            )

            // If it's a scam, alert user via TTS with Important Notice
            if (aiResult.isScam) {
                ttsManager.speakImportantNotice(aiResult.importantNotice.ifBlank { "Warning: Potential social engineering fraud detected." })
            }
        }
    }

    fun saveManualAnalysisToThreatLog() {
        val state = _manualThreatState.value
        val ai = state.aiAnalysis ?: return
        viewModelScope.launch {
            repository.recordCallThreat(
                phoneNumber = state.query,
                callerTag = ai.callerProfile,
                transcript = "${ai.reasoning} | Red flags: ${ai.redFlags.joinToString()}",
                isScam = ai.isScam,
                confidence = ai.confidence,
                triggerWords = ai.redFlags,
                threatCategory = ai.threatCategory,
                actionTaken = ai.suggestedAction
            )
            ttsManager.speakCustom("Saved to Call Guardian threat log.")
        }
    }

    fun runLiveAiCallScreeningSimulation(
        phoneNumber: String,
        callerLabel: String,
        dialogue: String
    ) {
        callJob?.cancel()
        _currentProfile.value = AppProfile.CALL_GUARDIAN
        _callSimulationState.value = LiveCallSimulationState(
            isActive = true,
            callerNumber = phoneNumber,
            callerLabel = callerLabel,
            transcriptStream = "Connecting telecom stream with $phoneNumber...",
            isRealTimeScreened = true
        )

        callJob = viewModelScope.launch {
            delay(300)
            val words = dialogue.split(" ")
            var streamedText = ""

            for (w in words) {
                delay(160)
                streamedText = if (streamedText.isEmpty()) w else "$streamedText $w"
                _callSimulationState.value = _callSimulationState.value.copy(
                    transcriptStream = streamedText,
                    latestChunk = w
                )

                // Incremental real-time word-by-word heuristic inspection
                val quickCheck = geminiAiService.analyzeCallOrNumberWithAi(phoneNumber, streamedText)
                if (quickCheck.isScam && !_callSimulationState.value.isInterrupted) {
                    val cat = when (quickCheck.threatCategory) {
                        "OTP_THEFT" -> ThreatCategory.OTP_THEFT
                        "APK_SIDELOAD" -> ThreatCategory.APK_SIDELOAD
                        "URGENT_FINE" -> ThreatCategory.URGENT_FINE
                        "LOTTERY_PRIZE" -> ThreatCategory.LOTTERY_PRIZE
                        else -> ThreatCategory.SAFE_CALL
                    }
                    _callSimulationState.value = _callSimulationState.value.copy(
                        analysisResult = ScamAnalysisResult(
                            isScam = true,
                            confidence = quickCheck.confidence,
                            threatCategory = cat,
                            triggerWords = quickCheck.redFlags,
                            reasoning = quickCheck.reasoning,
                            suggestedAction = quickCheck.suggestedAction
                        ),
                        aiAnalysis = quickCheck,
                        isInterrupted = true
                    )
                    ttsManager.speakImportantNotice(quickCheck.importantNotice)
                }
            }

            delay(250)
            val finalResult = geminiAiService.analyzeCallOrNumberWithAi(phoneNumber, streamedText)
            val category = when (finalResult.threatCategory) {
                "OTP_THEFT" -> ThreatCategory.OTP_THEFT
                "APK_SIDELOAD" -> ThreatCategory.APK_SIDELOAD
                "URGENT_FINE" -> ThreatCategory.URGENT_FINE
                "LOTTERY_PRIZE" -> ThreatCategory.LOTTERY_PRIZE
                "JOB_SCAM" -> ThreatCategory.LOTTERY_PRIZE
                else -> ThreatCategory.SAFE_CALL
            }

            val legacyResult = ScamAnalysisResult(
                isScam = finalResult.isScam,
                confidence = finalResult.confidence,
                threatCategory = category,
                triggerWords = finalResult.redFlags,
                reasoning = finalResult.reasoning,
                suggestedAction = finalResult.suggestedAction
            )

            _callSimulationState.value = _callSimulationState.value.copy(
                analysisResult = legacyResult,
                aiAnalysis = finalResult,
                isInterrupted = finalResult.isScam
            )

            // Record to Room DB
            repository.recordCallThreat(
                phoneNumber = phoneNumber,
                callerTag = callerLabel,
                transcript = streamedText,
                isScam = finalResult.isScam,
                confidence = finalResult.confidence,
                triggerWords = finalResult.redFlags,
                threatCategory = category.name,
                actionTaken = if (finalResult.isScam) "AUTO-INTERRUPTED BY SOCIAL ENGINEERING SENTRY" else "SAFE CALL RECORDED"
            )

            if (!finalResult.isScam) {
                ttsManager.speakCustom("Call verified as safe cellular conversation.")
            }
        }
    }

    fun clearManualThreat() {
        _manualThreatState.value = ManualThreatAnalysisState()
    }

    fun endOrDisconnectCall() {
        liveSpeechRecognizer.stopListening()
        callJob?.cancel()
        _callSimulationState.value = _callSimulationState.value.copy(
            isActive = false,
            isInterrupted = false
        )
    }

    fun dismissScreenShield() {
        ScreenShareDetector.dismissShield()
    }

    // ----------------------------------------------------
    // TENSORFLOW LITE AUDIO INTERCEPTOR & FLOATING OVERLAY
    // ----------------------------------------------------

    private val tfLiteClassifier = com.example.engine.AudioScamTfLiteClassifier(app)

    private val _isAudioInterceptorActive = MutableStateFlow(false)
    val isAudioInterceptorActive: StateFlow<Boolean> = _isAudioInterceptorActive.asStateFlow()

    private val _latestTfLiteResult = MutableStateFlow<com.example.engine.AudioScamTfLiteClassifier.AudioClassificationResult?>(null)
    val latestTfLiteResult: StateFlow<com.example.engine.AudioScamTfLiteClassifier.AudioClassificationResult?> = _latestTfLiteResult.asStateFlow()

    fun testTfLiteAudioClassifier(scenario: Int) {
        viewModelScope.launch {
            val (scenarioName, testPhrase, isStressHigh, isSynthetic) = when (scenario) {
                1 -> Quadruple(
                    "Digital Arrest & Police Extortion",
                    "This is CBI Inspector Rathore, arrest warrant is issued on your Aadhaar card for money laundering. You are under digital arrest. Do not disconnect.",
                    true,
                    false
                )
                2 -> Quadruple(
                    "Banking KYC & OTP Theft Coercion",
                    "Sir your SBI Yono Netbanking account will be blocked in 10 minutes. Read the 6-digit OTP code sent to your mobile immediately to restore access.",
                    true,
                    false
                )
                3 -> Quadruple(
                    "Remote Screen Hijack Support Scam",
                    "Sir please install AnyDesk or QuickSupport application from Google Play Store and share the 9-digit address number to update 5G SIM.",
                    true,
                    false
                )
                4 -> Quadruple(
                    "AI Deepfake Voice Clone / Spoofing",
                    "Hey Dad, my wallet was stolen in emergency at the airport, please send ₹20,000 urgently to this new QR code.",
                    false,
                    true
                )
                else -> Quadruple(
                    "Normal / Safe Cellular Conversation",
                    "Hello Ramesh, are we meeting at the cafe tomorrow evening for the project discussion?",
                    false,
                    false
                )
            }

            // Generate synthetic PCM samples for audio classification
            val sampleRate = 16000
            val numSamples = sampleRate * 2 // 2 seconds of 16kHz audio
            val pcmData = ShortArray(numSamples)
            val freq = if (isSynthetic) 440.0 else 220.0
            val amp = if (isStressHigh) 18000 else 6000

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val rawVal = (amp * Math.sin(2.0 * Math.PI * freq * t) + (if (isStressHigh) 5000 * Math.sin(2.0 * Math.PI * 880.0 * t) else 0.0)).toInt()
                pcmData[i] = rawVal.coerceIn(-32768, 32767).toShort()
            }

            val result = tfLiteClassifier.classifyAudioBuffer(
                audioSamples = pcmData,
                contextTranscript = testPhrase
            )

            _latestTfLiteResult.value = result

            if (result.isScam) {
                // Show floating WindowManager overlay
                triggerFloatingFraudOverlay(
                    archetype = result.archetype,
                    callerNumber = "+91 98765 43210",
                    confidence = result.confidence,
                    reasoning = result.reasoning,
                    recommendedAction = result.recommendedAction,
                    acousticMarkers = result.acousticMarkers
                )

                // Record threat in Room DB
                repository.recordCallThreat(
                    phoneNumber = "+91 98765 43210",
                    callerTag = "TensorFlow Lite Audio Intercept (${result.archetype})",
                    transcript = testPhrase,
                    isScam = true,
                    confidence = result.confidence,
                    triggerWords = result.acousticMarkers,
                    threatCategory = "URGENT_FINE",
                    actionTaken = result.recommendedAction
                )

                ttsManager.speakImportantNotice("Alert! TensorFlow Lite identified ${result.archetype}. Disconnect now.")
            } else {
                ttsManager.speakCustom("TensorFlow Lite verified call audio as safe.")
            }
        }
    }

    fun triggerFloatingFraudOverlay(
        archetype: String = "Digital Arrest & Police Extortion",
        callerNumber: String = "+91 98765 43210",
        confidence: Float = 0.96f,
        reasoning: String = "Authoritative extortion script & vocal intimidation detected.",
        recommendedAction: String = "HANG UP IMMEDIATELY • POLICE NEVER CALLS VIA VIDEO",
        acousticMarkers: List<String> = listOf("Coercive Law Enforcement Script", "Extreme Vocal Pressure", "High Stress Index 88%")
    ) {
        val overlayData = com.example.ui.overlay.FraudAlertOverlayManager.OverlayData(
            callerNumber = callerNumber,
            archetype = archetype,
            confidence = confidence,
            stressLevel = 0.88f,
            reasoning = reasoning,
            recommendedAction = recommendedAction,
            acousticMarkers = acousticMarkers
        )
        com.example.ui.overlay.FraudAlertOverlayManager.getInstance(app).showFraudAlertOverlay(overlayData)
    }

    fun dismissFloatingFraudOverlay() {
        com.example.ui.overlay.FraudAlertOverlayManager.getInstance(app).dismissOverlay()
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
