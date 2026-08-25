package com.example.data.model

enum class ThreatCategory(val displayName: String, val severityLevel: String) {
    OTP_THEFT("OTP Extraction Fraud", "CRITICAL"),
    APK_SIDELOAD("Malicious APK Sideload", "SEVERE"),
    URGENT_FINE("Fake Electricity/KYC Urgency", "HIGH"),
    LOTTERY_PRIZE("Lottery & Prize Scam", "MEDIUM"),
    SAFE_CALL("Verified Contact / Normal Call", "LOW")
}

enum class ThreatAction(val label: String) {
    BLOCKED("Auto Blocked"),
    WARNED("Interrupted with Alarm"),
    ALLOWED("Allowed")
}

enum class UpiProvider(val appName: String, val packageName: String) {
    GOOGLE_PAY("Google Pay", "com.google.android.apps.nbu.paisa.user"),
    PHONEPE("PhonePe", "com.phonepe.app"),
    PAYTM("Paytm", "net.one97.paytm"),
    BHARATPE("BharatPe", "com.bharatpe.app"),
    BHIM("BHIM UPI", "in.org.npci.upiapp"),
    OTHER("UPI Soundbox", "unknown.soundbox")
}

enum class KhataEntryType(val label: String) {
    GAVE_CREDIT("Udhaar Diya (Gave Credit)"),
    RECEIVED_PAYMENT("Jama Kiya (Received Payment)")
}

data class ScamAnalysisResult(
    val isScam: Boolean,
    val confidence: Float,
    val triggerWords: List<String>,
    val threatCategory: ThreatCategory,
    val reasoning: String,
    val suggestedAction: String
)

data class NpuHardwareStatus(
    val isNpuActive: Boolean = true,
    val acceleratorName: String = "Qualcomm Snapdragon Hexagon NPU",
    val runtime: String = "PyTorch ExecuTorch (QNN Backend)",
    val quantization: String = "INT4 Quantized (Phi-3-mini / Gemma 2B)",
    val averageLatencyMs: Int = 18,
    val memoryFootprintMb: Int = 142,
    val cloudCallsTotal: Int = 0,
    val onDeviceAccuracy: Float = 0.982f
)

data class SnapKhataItem(
    val customerName: String,
    val amount: Double,
    val itemsDescription: String,
    val confidence: Float
)

enum class TransactionSource(val label: String) {
    BANK_SMS("Bank SMS"),
    UPI_NOTIFICATION("UPI Alert"),
    KHATA_ENTRY("Bahi Khata")
}

data class UnifiedTransactionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val amount: Double,
    val isCreditInflow: Boolean, // true = Money received (+₹), false = Credit given / Udhaar (-₹)
    val source: TransactionSource,
    val sourceLabel: String,
    val referenceId: String,
    val timestamp: Long,
    val isVerified: Boolean = true,
    val rawText: String = ""
)

data class LiveAlertFeedItem(
    val id: String,
    val title: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val source: TransactionSource,
    val amount: Double = 0.0
)
