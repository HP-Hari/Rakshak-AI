package com.example.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenShareAlert(
    val remoteAppPackage: String,
    val remoteAppName: String,
    val bankingAppPackage: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

object ScreenShareDetector {

    val REMOTE_ACCESS_PACKAGES = mapOf(
        "com.teamviewer.host.market" to "TeamViewer QuickSupport",
        "com.teamviewer.teamviewer.market.mobile" to "TeamViewer",
        "com.anydesk.anydeskandroid" to "AnyDesk Remote Control",
        "com.rustdesk.rustdesk" to "RustDesk",
        "com.zoho.assist" to "Zoho Assist",
        "com.logmein.rescuesecurity" to "LogMeIn Rescue",
        "com.sand.airdroid" to "AirDroid"
    )

    val BANKING_PACKAGES = setOf(
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "net.one97.paytm",
        "com.bharatpe.app",
        "in.org.npci.upiapp",
        "com.sbi.upi",
        "com.axis.mobile",
        "com.icicibank.mobile",
        "com.msf.kbank.mobile",
        "com.hdfcbank.payzapp"
    )

    private val _activeShieldAlert = MutableStateFlow<ScreenShareAlert?>(null)
    val activeShieldAlert: StateFlow<ScreenShareAlert?> = _activeShieldAlert.asStateFlow()

    private val _isShieldEnabled = MutableStateFlow(true)
    val isShieldEnabled: StateFlow<Boolean> = _isShieldEnabled.asStateFlow()

    fun setShieldEnabled(enabled: Boolean) {
        _isShieldEnabled.value = enabled
    }

    fun triggerShield(remotePackage: String, bankingPackage: String) {
        val appName = REMOTE_ACCESS_PACKAGES[remotePackage] ?: "Remote Desktop Tool ($remotePackage)"
        _activeShieldAlert.value = ScreenShareAlert(
            remoteAppPackage = remotePackage,
            remoteAppName = appName,
            bankingAppPackage = bankingPackage,
            timestamp = System.currentTimeMillis(),
            isActive = true
        )
    }

    fun dismissShield() {
        _activeShieldAlert.value = null
    }
}
