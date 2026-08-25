package com.example.ui.screens.developer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.engine.NpuInferenceEngine
import com.example.ui.theme.MinimalAccentAmber
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

@Composable
fun DevServerSheet(
    npuEngine: NpuInferenceEngine,
    onDismiss: () -> Unit
) {
    val status = npuEngine.hardwareStatus

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            colors = CardDefaults.cardColors(containerColor = MinimalSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorder),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
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
                                .background(MinimalTechCyanContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = MinimalTechCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "DEVELOPER BRIDGE & NPU",
                            color = MinimalTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).testTag("close_dev_server_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MinimalTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Local Server Info Card (PRD 5.2)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MinimalSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MinimalGreenPrimary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "EMBEDDED LOCAL HTTP SERVER (PORT 8080)",
                                color = MinimalGreenPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Connect laptop on local Wi-Fi to audit model accuracy and stream Room DB data:",
                            color = MinimalTextSecondary,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        EndpointPill("GET /api/ledger", "Live UPI transactions JSON")
                        EndpointPill("GET /api/threats", "Intercepted scam classifications")
                        EndpointPill("GET /api/khata", "Bahi Khata customer credits")
                        EndpointPill("GET /api/npu-status", "Snapdragon NPU telemetry")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Snapdragon NPU Hardware Architecture (PRD 5.1)
                Text(
                    text = "HARDWARE NPU ACCELERATION (PRD 5.1)",
                    color = MinimalTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MinimalSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        NpuSpecRow("Accelerator", status.acceleratorName)
                        NpuSpecRow("Engine Backend", status.runtime)
                        NpuSpecRow("Quantization", status.quantization)
                        NpuSpecRow("Average Latency", "${status.averageLatencyMs} ms")
                        NpuSpecRow("RAM Footprint", "${status.memoryFootprintMb} MB")
                        NpuSpecRow("Cloud Telemetry", "${status.cloudCallsTotal} bytes (100% On-Device)")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // OS Services & Listeners status (PRD 5.3)
                Text(
                    text = "CORE OS LISTENERS & PERMISSIONS",
                    color = MinimalGreenPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OsListenerRow("CallScreeningService", "Passive telecom hook (Unknown callers)", true)
                    OsListenerRow("NotificationListener", "Passive UPI parser (PhonePe/GPay)", true)
                    OsListenerRow("AccessibilityService", "Screen-Share Shield (AnyDesk/TeamViewer)", true)
                    OsListenerRow("CameraX & MediaPipe", "Snap-to-Khata on-device OCR", true)
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = MinimalGreenPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text("Close Diagnostics", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EndpointPill(route: String, description: String) {
    Surface(
        color = MinimalSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorder),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = route,
                color = MinimalGreenPrimary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = MinimalTextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun NpuSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MinimalTextSecondary, fontSize = 11.sp)
        Text(
            text = value,
            color = MinimalTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun OsListenerRow(name: String, desc: String, isHooked: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MinimalSurfaceElevated)
            .border(1.dp, MinimalSurfaceBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, color = MinimalTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text(text = desc, color = MinimalTextSecondary, fontSize = 10.sp)
        }
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MinimalGreenPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}
