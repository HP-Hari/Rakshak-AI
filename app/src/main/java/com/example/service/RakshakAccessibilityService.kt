package com.example.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.engine.ScreenShareDetector

class RakshakAccessibilityService : AccessibilityService() {

    private var activeRemotePackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !ScreenShareDetector.isShieldEnabled.value) return

        val pkgName = event.packageName?.toString() ?: return

        // 1. Detect if remote screen share software is active
        if (ScreenShareDetector.REMOTE_ACCESS_PACKAGES.containsKey(pkgName)) {
            activeRemotePackage = pkgName
            Log.w("AccessibilityShield", "Remote access software active in foreground/background: $pkgName")
        }

        // 2. Detect if sensitive banking or UPI app is launched
        if (ScreenShareDetector.BANKING_PACKAGES.any { pkgName.contains(it) }) {
            if (activeRemotePackage != null) {
                Log.e("AccessibilityShield", "CRITICAL: Banking app $pkgName opened while screen sharing $activeRemotePackage!")
                ScreenShareDetector.triggerShield(
                    remotePackage = activeRemotePackage!!,
                    bankingPackage = pkgName
                )
            }
        }
    }

    override fun onInterrupt() {
        Log.d("AccessibilityShield", "Accessibility Service interrupted.")
    }
}
