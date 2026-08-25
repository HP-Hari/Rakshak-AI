package com.example.ui.screens.guardian

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.service.CallAudioInterceptorService
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.CallThreatEntity
import com.example.ui.RakshakMainViewModel
import com.example.ui.components.ActiveCallInterruptionDialog
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.theme.MinimalAccentAmber
import com.example.ui.theme.MinimalAmberContainer
import com.example.ui.theme.MinimalCanvas
import com.example.ui.theme.MinimalGreenContainer
import com.example.ui.theme.MinimalGreenPrimary
import com.example.ui.theme.MinimalSurface
import com.example.ui.theme.MinimalSurfaceBorder
import com.example.ui.theme.MinimalSurfaceElevated
import com.example.ui.theme.MinimalTechCyan
import com.example.ui.theme.MinimalTechCyanContainer
import com.example.ui.theme.MinimalTextMuted
import com.example.ui.theme.MinimalTextPrimary
import com.example.ui.theme.MinimalTextSecondary
import com.example.ui.theme.MinimalThreatRed
import com.example.ui.theme.MinimalThreatRedContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallGuardianScreen(
    viewModel: RakshakMainViewModel,
    modifier: Modifier = Modifier
) {
    val callState by viewModel.callSimulationState.collectAsStateWithLifecycle()
    val threatLogs by viewModel.callThreats.collectAsStateWithLifecycle()
    val scamCount by viewModel.scamCount.collectAsStateWithLifecycle()
    val manualThreatState by viewModel.manualThreatState.collectAsStateWithLifecycle()
    val isLiveSpeechListening by viewModel.isLiveSpeechListening.collectAsStateWithLifecycle()
    val liveSpeechTranscript by viewModel.liveSpeechTranscript.collectAsStateWithLifecycle()
    val liveSpeechRms by viewModel.liveSpeechAudioRms.collectAsStateWithLifecycle()
    val tfLiteResult by viewModel.latestTfLiteResult.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var manualInputText by remember { mutableStateOf("") }
    var showClearLogsDialog by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val isOverlayGranted = remember(context) {
        com.example.ui.overlay.FraudAlertOverlayManager.getInstance(context).canDrawOverlays()
    }

    // Don't auto-grab the physical microphone on simple screen entry; let user control or toggle when on speakerphone
    LaunchedEffect(Unit) {
        // Ready state
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MinimalCanvas)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Upfront Overlay Permission Prompt Banner (if not granted)
            if (!isOverlayGranted) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MinimalThreatRedContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalThreatRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Permission Alert",
                                tint = MinimalThreatRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Floating Overlay Permission Needed",
                                    color = MinimalThreatRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Allow 'Display over other apps' to show instant scam warnings directly over your incoming call screen.",
                                    color = MinimalTextPrimary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    com.example.ui.overlay.FraudAlertOverlayManager.getInstance(context).requestOverlayPermission(context)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MinimalThreatRed),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // 1. Guardian Status Hero Card (Real Status)
            item {
                GuardianHeroStatusCard(
                    scamCount = scamCount,
                    isScreenShieldActive = true
                )
            }

            // 2. Real-Time Telecom Call Interception Alert (If incoming call active)
            if (callState.isActive) {
                item {
                    ActiveCallAlertCard(
                        callState = callState,
                        onDismiss = { viewModel.endOrDisconnectCall() }
                    )
                }
            }

            // 3. Family Co-Pilot & SOS Card
            item {
                FamilyGuardianSosCard()
            }

            // 4. On-Device SLM (Gemma/Phi) Status Card
            item {
                OnDeviceSlmStatusCard()
            }

            // 5. Live Word-by-Word Call Listener & Automatic Social Engineering Sentry
            item {
                LiveCallWordRecorderCard(
                    isListening = isLiveSpeechListening,
                    transcript = if (isLiveSpeechListening) liveSpeechTranscript else callState.transcriptStream,
                    audioRms = liveSpeechRms,
                    aiAnalysis = callState.aiAnalysis,
                    onToggleListening = {
                        if (isLiveSpeechListening) {
                            viewModel.stopLiveVoiceCallListener()
                        } else {
                            viewModel.startLiveVoiceCallListener()
                        }
                    }
                )
            }

            // 4. TensorFlow Lite Call Audio Interceptor & Threat Overlay Sentry
            item {
                TfLiteAudioInterceptorCard(
                    tfLiteResult = tfLiteResult
                )
            }

            // 5. Actual Real-Time Spam & Scam Finder (Gemini AI + On-Device NLP)
            item {
                ActualScamAndSpamFinderCard(
                    inputText = manualInputText,
                    onInputChange = { manualInputText = it },
                    threatState = manualThreatState,
                    onInspect = {
                        keyboardController?.hide()
                        viewModel.analyzeManualThreatText(manualInputText)
                    },
                    onClear = {
                        manualInputText = ""
                        viewModel.clearManualThreat()
                    },
                    onSaveToLogs = {
                        viewModel.saveManualAnalysisToThreatLog()
                    }
                )
            }

            // 5. Intercepted Threats & Verified Call History Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VERIFIED THREAT & CALL LOGS",
                        color = MinimalTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    if (threatLogs.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearLogsDialog = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Clear Logs", color = MinimalThreatRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 6. Threat Logs List (Clean - Only Actual Inspected / Screened Calls)
            if (threatLogs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.MinimalSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorder),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MinimalGreenPrimary.copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "All Calls Verified Safe",
                                color = MinimalTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Continuous call verification and Gemini AI are actively monitoring for threats.",
                                color = MinimalTextSecondary,
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(threatLogs, key = { it.id }) { threat ->
                    ThreatLogCard(
                        threat = threat,
                        onDelete = { viewModel.deleteCallThreat(threat.id) }
                    )
                }
            }
        }

        // Active Emergency Call Interruption Modal
        AnimatedVisibility(
            visible = callState.isInterrupted && callState.analysisResult != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            callState.analysisResult?.let { result ->
                ActiveCallInterruptionDialog(
                    callerNumber = callState.callerNumber,
                    analysis = result,
                    aiAnalysis = callState.aiAnalysis,
                    transcriptSnippet = callState.transcriptStream,
                    onDisconnectCall = { viewModel.endOrDisconnectCall() },
                    onDismiss = { viewModel.endOrDisconnectCall() }
                )
            }
        }

        // Clear Logs Confirmation Dialog
        if (showClearLogsDialog) {
            AlertDialog(
                onDismissRequest = { showClearLogsDialog = false },
                title = { Text("Clear Threat Logs?", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to clear all call screening and threat history logs?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllCallThreats()
                            showClearLogsDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MinimalThreatRed)
                    ) {
                        Text("Clear All", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearLogsDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun GuardianHeroStatusCard(
    scamCount: Int,
    isScreenShieldActive: Boolean
) {
    val context = LocalContext.current
    val roleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
    } else null

    var isCallScreeningRoleHeld by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
                roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
            } else false
        )
    }

    val roleRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
            isCallScreeningRoleHeld = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.MinimalSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorder),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MinimalGreenPrimary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CALL GUARDIAN AI ENGINE",
                        color = MinimalTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Surface(
                    color = MinimalGreenContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MinimalGreenPrimary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "AUTOMATIC VERIFICATION ACTIVE",
                            color = MinimalGreenPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Stat Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "$scamCount",
                        color = if (scamCount > 0) MinimalThreatRed else MinimalGreenPrimary,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = "Scams Auto-Blocked & Intercepted",
                        color = MinimalTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    color = MinimalSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MinimalTechCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Real-Time 24/7",
                            color = MinimalTechCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Real Call Screening Telecom Integration Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MinimalSurfaceElevated)
                    .border(1.dp, MinimalSurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneCallback,
                        contentDescription = null,
                        tint = MinimalGreenPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Automatic Call Screening Watchdog",
                            color = MinimalTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isCallScreeningRoleHeld) "Default telecom screener active" else "Automatically intercepts incoming calls and evaluates caller risk",
                            color = if (isCallScreeningRoleHeld) MinimalGreenPrimary else MinimalTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null && !isCallScreeningRoleHeld) {
                    Button(
                        onClick = {
                            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                            roleRequestLauncher.launch(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MinimalTechCyanContainer),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Enable Telecom Role", color = MinimalTechCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Surface(
                        color = MinimalGreenContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            color = MinimalGreenPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Screen Share Shield Status Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MinimalSurfaceElevated)
                    .border(1.dp, MinimalSurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ScreenShare,
                        contentDescription = null,
                        tint = if (isScreenShieldActive) MinimalGreenPrimary else MinimalTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Screen-Share Protection Shield",
                            color = MinimalTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Blocks AnyDesk / TeamViewer remote overlays during calls",
                            color = MinimalTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    color = MinimalGreenContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "SHIELD ACTIVE",
                        color = MinimalGreenPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// LIVE CALL RECORDER & WORD-BY-WORD SOCIAL ENGINEERING SENTRY
// ----------------------------------------------------

@Composable
private fun LiveCallWordRecorderCard(
    isListening: Boolean,
    transcript: String,
    audioRms: Float,
    aiAnalysis: com.example.engine.GeminiAiService.AiCallAnalysis?,
    onToggleListening: () -> Unit
) {
    val isThreat = aiAnalysis?.isScam == true
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isThreat) MinimalThreatRedContainer.copy(alpha = 0.6f) else MinimalSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isThreat) MinimalThreatRed else if (isListening) MinimalGreenPrimary else MinimalSurfaceBorder
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isListening) MinimalGreenContainer else MinimalTechCyanContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Mic else Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = if (isListening) MinimalGreenPrimary else MinimalTechCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Live Call Threat Sentry",
                            color = MinimalTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (isListening) "Scanning call in real time..." else "Active real-time sentinel for call fraud analysis",
                            color = if (isListening) MinimalGreenPrimary else MinimalTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    color = if (isThreat) MinimalThreatRedContainer else if (isListening) MinimalGreenContainer else MinimalSurfaceElevated,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isThreat) "🚨 THREAT DETECTED" else if (isListening) "● LISTENING LIVE" else "READY",
                        color = if (isThreat) MinimalThreatRed else if (isListening) MinimalGreenPrimary else MinimalTextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Audio Waveform
            if (isListening) {
                AudioWaveformVisualizer(
                    isLive = true,
                    color = if (isThreat) MinimalThreatRed else MinimalGreenPrimary,
                    barCount = 28,
                    maxHeight = 26.dp
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Word Stream Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MinimalSurfaceElevated)
                    .border(1.dp, MinimalSurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "REAL-TIME SPEECH SCAN",
                            color = MinimalTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        if (transcript.isNotBlank()) {
                            Text(
                                text = "${transcript.split(" ").filter { it.isNotBlank() }.size} words scanned",
                                color = MinimalTechCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (transcript.isBlank()) {
                        Text(
                            text = if (isListening) "Sentry is active. Monitoring for fraud vectors..."
                            else "💡 Tip: Activate real-time threat scanning during active calls to detect social engineering and psychological manipulation.",
                            color = MinimalTextMuted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    } else {
                        Text(
                            text = transcript,
                            color = MinimalTextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Important Security Notice Card (if threat caught)
            if (isThreat && aiAnalysis != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MinimalThreatRedContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MinimalThreatRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MinimalThreatRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🚨 IMPORTANT SECURITY NOTICE",
                                color = MinimalThreatRed,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = aiAnalysis.importantNotice.ifBlank { "Social engineering trick detected. Hang up immediately." },
                            color = MinimalThreatRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        if (aiAnalysis.psychologicalTrick.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Psychological Trick: ${aiAnalysis.psychologicalTrick}",
                                color = MinimalTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Toggle Button
            Button(
                onClick = onToggleListening,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening) MinimalSurfaceBorder else MinimalGreenPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("toggle_live_recorder_button")
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (isListening) MinimalTextPrimary else Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isListening) "STOP SCAN" else "START THREAT SCAN",
                    color = if (isListening) MinimalTextPrimary else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ----------------------------------------------------
// ACTUAL SPAM & SCAM FINDER (GEMINI AI + ON-DEVICE NPU)
// ----------------------------------------------------

@Composable
private fun ActualScamAndSpamFinderCard(
    inputText: String,
    onInputChange: (String) -> Unit,
    threatState: com.example.ui.ManualThreatAnalysisState,
    onInspect: () -> Unit,
    onClear: () -> Unit,
    onSaveToLogs: () -> Unit
) {
    var searchMode by remember { mutableStateOf(0) } // 0: Any Phone Number / Caller, 1: Spoken Call Transcript, 2: Suspicious SMS / WhatsApp

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.MinimalSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorder),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MinimalGreenContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MinimalGreenPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Actual Spam & Scam Finder",
                        color = MinimalTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Real-time AI verification for unknown callers, phone numbers & dialogue",
                        color = MinimalTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Search Mode Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MinimalSurfaceElevated)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Phone Number", "Call Dialogue", "SMS / Link").forEachIndexed { index, label ->
                    val isSelected = searchMode == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MinimalGreenPrimary else Color.Transparent)
                            .clickable { searchMode = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else MinimalTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = {
                    val placeholderText = when (searchMode) {
                        0 -> "Enter any 10-digit mobile, unknown caller ID or landline..."
                        1 -> "Enter words spoken by caller (e.g. 'police arrest', 'electricity cut', 'OTP verification')..."
                        else -> "Paste suspicious SMS, WhatsApp message, or payment link..."
                    }
                    Text(
                        text = placeholderText,
                        color = MinimalTextMuted,
                        fontSize = 12.sp
                    )
                },
                trailingIcon = {
                    if (inputText.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MinimalTextMuted)
                        }
                    }
                },
                singleLine = searchMode == 0,
                maxLines = if (searchMode == 0) 1 else 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onInspect() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scam_inspector_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MinimalGreenPrimary,
                    unfocusedBorderColor = MinimalSurfaceBorder,
                    focusedContainerColor = MinimalSurfaceElevated,
                    unfocusedContainerColor = MinimalSurfaceElevated,
                    focusedTextColor = MinimalTextPrimary,
                    unfocusedTextColor = MinimalTextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onInspect,
                enabled = inputText.isNotBlank() && !threatState.isAnalyzing,
                colors = ButtonDefaults.buttonColors(containerColor = MinimalGreenPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("inspect_scam_button")
            ) {
                if (threatState.isAnalyzing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SCANNING FOR SPAM & SCAM...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "FIND SPAM & VERIFY THREATS",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Results Display with Detailed Gemini AI Findings
            if (threatState.aiAnalysis != null) {
                val ai = threatState.aiAnalysis
                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = if (ai.isScam) MinimalThreatRedContainer else MinimalGreenContainer,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (ai.isScam) MinimalThreatRed.copy(alpha = 0.5f) else MinimalGreenPrimary.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (ai.isScam) Icons.Default.GppBad else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (ai.isScam) MinimalThreatRed else MinimalGreenPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (ai.isScam) "🚨 ${ai.riskLevel} SCAM THREAT DETECTED" else "✅ VERIFIED SAFE CALL",
                                    color = if (ai.isScam) MinimalThreatRed else MinimalGreenPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = "Confidence ${(ai.confidence * 100).toInt()}%",
                                color = if (ai.isScam) MinimalThreatRed else MinimalGreenPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Identity / Profile: ${ai.callerProfile}",
                            color = MinimalTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = ai.reasoning,
                            color = MinimalTextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        if (ai.psychologicalTrick.isNotBlank() && ai.psychologicalTrick != "None Detected") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Psychological Trick: ${ai.psychologicalTrick}",
                                color = if (ai.isScam) MinimalThreatRed else MinimalTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (ai.redFlags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Red Flags: ${ai.redFlags.joinToString(" • ")}",
                                color = if (ai.isScam) MinimalThreatRed else MinimalTextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Action: ${ai.suggestedAction}",
                                color = if (ai.isScam) MinimalThreatRed else MinimalGreenPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedButton(
                                onClick = onSaveToLogs,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (ai.isScam) MinimalThreatRed else MinimalGreenPrimary)
                            ) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(13.dp), tint = if (ai.isScam) MinimalThreatRed else MinimalGreenPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save to Logs", fontSize = 10.sp, color = if (ai.isScam) MinimalThreatRed else MinimalGreenPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// ACTIVE CALL CARD & THREAT LOG CARD
// ----------------------------------------------------

@Composable
private fun ActiveCallAlertCard(
    callState: com.example.ui.LiveCallSimulationState,
    onDismiss: () -> Unit
) {
    val isFraud = callState.analysisResult?.isScam == true
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isFraud) MinimalThreatRedContainer else MinimalSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isFraud) MinimalThreatRed else MinimalSurfaceBorder
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PhoneInTalk,
                        contentDescription = null,
                        tint = if (isFraud) MinimalThreatRed else MinimalGreenPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = callState.callerLabel,
                        color = MinimalTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Surface(
                    color = if (isFraud) MinimalThreatRedContainer else MinimalGreenContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isFraud) "THREAT INTERCEPTED" else "SAFE CALL",
                        color = if (isFraud) MinimalThreatRed else MinimalGreenPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = callState.transcriptStream.ifEmpty { "Intercepting caller speech stream..." },
                color = MinimalTextPrimary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = if (isFraud) MinimalThreatRed else MinimalGreenPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Dismiss Call Stream", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ThreatLogCard(
    threat: CallThreatEntity,
    onDelete: () -> Unit
) {
    val timeFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val formattedDate = timeFormat.format(Date(threat.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.MinimalSurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (threat.isScam) MinimalThreatRed.copy(alpha = 0.3f) else MinimalSurfaceBorder
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (threat.isScam) MinimalThreatRedContainer else MinimalGreenContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (threat.isScam) Icons.Default.GppBad else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (threat.isScam) MinimalThreatRed else MinimalGreenPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = threat.callerTag,
                            color = MinimalTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formattedDate,
                            color = MinimalTextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (threat.isScam) MinimalThreatRedContainer else MinimalGreenContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (threat.isScam) "${(threat.confidence * 100).toInt()}% RISK" else "SAFE",
                            color = if (threat.isScam) MinimalThreatRed else MinimalGreenPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MinimalTextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "\"${threat.transcript}\"",
                color = MinimalTextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MinimalSurfaceElevated,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = threat.threatCategory,
                        color = MinimalTextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = threat.actionTaken,
                    color = if (threat.isScam) MinimalThreatRed else MinimalGreenPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TfLiteAudioInterceptorCard(
    tfLiteResult: com.example.engine.AudioScamTfLiteClassifier.AudioClassificationResult?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.MinimalSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalTechCyan.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MinimalTechCyanContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "ML Audio Interceptor",
                            tint = MinimalTechCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "TensorFlow Lite Audio Interceptor",
                            color = MinimalTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "MediaProjection Stream • On-Device Neural Sentry",
                            color = MinimalTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    color = MinimalGreenContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "ON-DEVICE NPU",
                        color = MinimalGreenPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Latest Real-Time Inference Status
            if (tfLiteResult != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (tfLiteResult.isScam) MinimalThreatRedContainer else MinimalGreenContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (tfLiteResult.isScam) "🚨 ${tfLiteResult.archetype}" else "✅ Natural Speech Pattern",
                                color = if (tfLiteResult.isScam) MinimalThreatRed else MinimalGreenPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${(tfLiteResult.confidence * 100).toInt()}% Conf • ${tfLiteResult.inferenceLatencyMs}ms",
                                color = MinimalTextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = tfLiteResult.reasoning,
                            color = MinimalTextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        if (tfLiteResult.acousticMarkers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Acoustic Markers: " + tfLiteResult.acousticMarkers.joinToString(" • "),
                                color = MinimalTextSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MinimalSurfaceElevated)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MinimalGreenPrimary)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Real-time acoustic analysis active. Raw 16kHz audio stream from connected calls is analyzed on-device with sub-35ms latency.",
                        color = MinimalTextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val interceptorContext = LocalContext.current
            val isInterceptorRunning by CallAudioInterceptorService.isInterceptorActive.collectAsStateWithLifecycle()
            val mediaProjectionManager = remember(interceptorContext) {
                interceptorContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            }

            val projectionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    val startIntent = Intent(interceptorContext, CallAudioInterceptorService::class.java).apply {
                        action = CallAudioInterceptorService.ACTION_START
                        putExtra(CallAudioInterceptorService.EXTRA_RESULT_CODE, result.resultCode)
                        putExtra(CallAudioInterceptorService.EXTRA_RESULT_DATA, result.data)
                        putExtra(CallAudioInterceptorService.EXTRA_CALLER_NUMBER, "Live Call Audio Stream")
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        interceptorContext.startForegroundService(startIntent)
                    } else {
                        interceptorContext.startService(startIntent)
                    }
                    Toast.makeText(interceptorContext, "Direct Call Audio Stream Interceptor Activated", Toast.LENGTH_SHORT).show()
                }
            }

            Button(
                onClick = {
                    if (isInterceptorRunning) {
                        val stopIntent = Intent(interceptorContext, CallAudioInterceptorService::class.java).apply {
                            action = CallAudioInterceptorService.ACTION_STOP
                        }
                        interceptorContext.startService(stopIntent)
                        Toast.makeText(interceptorContext, "Call Audio Interceptor Stopped", Toast.LENGTH_SHORT).show()
                    } else {
                        if (mediaProjectionManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            try {
                                projectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                            } catch (e: Exception) {
                                val startIntent = Intent(interceptorContext, CallAudioInterceptorService::class.java).apply {
                                    action = CallAudioInterceptorService.ACTION_START
                                    putExtra(CallAudioInterceptorService.EXTRA_CALLER_NUMBER, "Live Telecom Call")
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    interceptorContext.startForegroundService(startIntent)
                                } else {
                                    interceptorContext.startService(startIntent)
                                }
                            }
                        } else {
                            val startIntent = Intent(interceptorContext, CallAudioInterceptorService::class.java).apply {
                                action = CallAudioInterceptorService.ACTION_START
                                putExtra(CallAudioInterceptorService.EXTRA_CALLER_NUMBER, "Live Telecom Call")
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                interceptorContext.startForegroundService(startIntent)
                            } else {
                                interceptorContext.startService(startIntent)
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isInterceptorRunning) MinimalThreatRed else MinimalTechCyan
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("toggle_call_audio_interceptor_button")
            ) {
                Icon(
                    imageVector = if (isInterceptorRunning) Icons.Default.Block else Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isInterceptorRunning) "STOP RAW AUDIO INTERCEPTOR" else "CAPTURE RAW CALL STREAM (MEDIA PROJECTION)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}


@Composable
fun FamilyGuardianSosCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.MinimalSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.MinimalSurfaceBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Family Guardian",
                    tint = MinimalGreenPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Family Co-Pilot & SOS",
                        color = MinimalTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Auto-alerts family if trapped on a scam call",
                        color = MinimalTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MinimalGreenContainer, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhoneCallback, contentDescription = null, tint = MinimalGreenPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Trusted Contact Active", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MinimalGreenPrimary)
                        Text("+91 98765 43210 (Son)", fontSize = 10.sp, color = MinimalTextPrimary)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "If an OTP is received or AnyDesk is opened during an unknown call, an emergency SMS is dispatched immediately.",
                color = MinimalTextMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun OnDeviceSlmStatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.MinimalSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.example.ui.theme.MinimalSurfaceBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "SLM Active",
                    tint = MinimalAccentAmber,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "On-Device NPU Engine (SLM)",
                        color = MinimalTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Semantic Screen & Message Guardian",
                        color = MinimalTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MinimalAmberContainer, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Model", fontSize = 10.sp, color = MinimalTextSecondary)
                        Text("Gemma-2B (INT4)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MinimalTextPrimary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Status", fontSize = 10.sp, color = MinimalTextSecondary)
                        Text("Running on NPU", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MinimalAccentAmber)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Privacy", fontSize = 10.sp, color = MinimalTextSecondary)
                        Text("100% Offline", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MinimalGreenPrimary)
                    }
                }
            }
        }
    }
}
