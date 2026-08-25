package com.example.ui

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.IndustrialTopBar
import com.example.ui.components.OneTimeSetupDialog
import com.example.ui.components.ScreenShareBlackoutOverlay
import com.example.ui.screens.developer.DevServerSheet
import com.example.ui.screens.guardian.CallGuardianScreen
import com.example.ui.screens.vyapar.SmartVyaparScreen
import com.example.ui.theme.MinimalCanvas
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RakshakApp(
    viewModel: RakshakMainViewModel = viewModel()
) {
    val currentProfile by viewModel.currentProfile.collectAsStateWithLifecycle()
    val liveLatencyMs by viewModel.liveNpuLatencyMs.collectAsStateWithLifecycle()
    val activeScreenShield by viewModel.activeScreenShield.collectAsStateWithLifecycle()
    val isDevServerOpen by viewModel.isDevServerSheetOpen.collectAsStateWithLifecycle()
    val isInitialSetupDone by viewModel.isInitialSetupDone.collectAsStateWithLifecycle()

    // Permissions check for OS Listeners (Phone, SMS, Audio, Camera, Contacts, Notifications)
    val permissionsToRequest = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val permissionsState = rememberMultiplePermissionsState(permissions = permissionsToRequest)

    val context = androidx.compose.ui.platform.LocalContext.current
    val isOverlayGranted = com.example.ui.overlay.FraudAlertOverlayManager.getInstance(context).canDrawOverlays()

    // Only prompt once during initial installation setup if permissions or overlay are missing
    val showOneTimeSetup = !isInitialSetupDone && (!permissionsState.allPermissionsGranted || !isOverlayGranted)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MinimalCanvas,
        topBar = {
            IndustrialTopBar(
                currentProfile = currentProfile,
                onProfileSelected = { viewModel.setProfile(it) },
                onOpenDevServer = { viewModel.toggleDevServerSheet(true) },
                liveLatencyMs = liveLatencyMs
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MinimalCanvas)
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentProfile,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "profileTransition"
            ) { profile ->
                when (profile) {
                    AppProfile.SMART_VYAPAR -> SmartVyaparScreen(viewModel = viewModel)
                    AppProfile.CALL_GUARDIAN -> CallGuardianScreen(viewModel = viewModel)
                }
            }

            // Screen-Share Security Shield Blackout Dialog
            if (activeScreenShield != null) {
                ScreenShareBlackoutOverlay(
                    alert = activeScreenShield!!,
                    onDismiss = { viewModel.dismissScreenShield() }
                )
            }

            // Developer Bridge & Diagnostics Sheet
            if (isDevServerOpen) {
                DevServerSheet(
                    npuEngine = viewModel.npuEngine,
                    onDismiss = { viewModel.toggleDevServerSheet(false) }
                )
            }

            // One-Time Installation Setup & Zero-Battery Protection Dialog
            if (showOneTimeSetup) {
                OneTimeSetupDialog(
                    onGrantPermissions = {
                        permissionsState.launchMultiplePermissionRequest()
                        viewModel.markInitialSetupComplete()
                    },
                    onRequestOverlayPermission = {
                        com.example.ui.overlay.FraudAlertOverlayManager.getInstance(context).requestOverlayPermission(context)
                    },
                    onDismiss = {
                        viewModel.markInitialSetupComplete()
                    }
                )
            }
        }
    }
}
