package com.example.ui.components

import android.Manifest
import android.content.Context
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.overlay.FraudAlertOverlayManager
import com.example.ui.theme.MinimalCanvas
import com.example.ui.theme.MinimalGreenContainer
import com.example.ui.theme.MinimalGreenPrimary
import com.example.ui.theme.MinimalSurface
import com.example.ui.theme.MinimalSurfaceBorder
import com.example.ui.theme.MinimalSurfaceElevated
import com.example.ui.theme.MinimalTechCyan
import com.example.ui.theme.MinimalTextMuted
import com.example.ui.theme.MinimalTextPrimary
import com.example.ui.theme.MinimalTextSecondary
import com.example.ui.theme.MinimalThreatRed
import com.example.ui.theme.MinimalThreatRedContainer

@Composable
fun OneTimeSetupDialog(
    onGrantPermissions: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isOverlayGranted = FraudAlertOverlayManager.getInstance(context).canDrawOverlays()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .testTag("one_time_setup_dialog"),
            colors = CardDefaults.cardColors(containerColor = MinimalSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MinimalGreenPrimary.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Row
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
                                .background(MinimalGreenContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MinimalGreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ONE-TIME SETUP",
                                color = MinimalGreenPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Zero-Battery Sentry Shield",
                                color = MinimalTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        color = MinimalGreenContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "ASK ONCE",
                            color = MinimalGreenPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Rakshak AI requests all core security permissions upfront. This ensures the app operates autonomously in the background with zero standby battery drain and can immediately display critical scam overlays over incoming calls.",
                    color = MinimalTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Feature Highlights
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MinimalSurfaceElevated)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PermissionFeatureRow(
                        icon = Icons.Default.Layers,
                        title = "Display Over Other Apps (Floating Overlay)",
                        subtitle = "Displays floating real-time scam threat alerts over phone dialers & apps.",
                        isGranted = isOverlayGranted
                    )
                    PermissionFeatureRow(
                        icon = Icons.Default.PhoneCallback,
                        title = "Call Guardian & Telecom Sentry",
                        subtitle = "Screens unknown numbers and evaluates audio streams via TensorFlow Lite.",
                        isGranted = null
                    )
                    PermissionFeatureRow(
                        icon = Icons.Default.Email,
                        title = "SMS & OTP Phishing Shield",
                        subtitle = "Auto-intercepts phishing links and extortion SMS as they arrive.",
                        isGranted = null
                    )
                    PermissionFeatureRow(
                        icon = Icons.Default.Star,
                        title = "Zero Standby Battery Drain",
                        subtitle = "Dormant event listeners activate strictly upon telecom triggers.",
                        isGranted = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Button(
                    onClick = {
                        onGrantPermissions()
                        if (!isOverlayGranted) {
                            onRequestOverlayPermission()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MinimalGreenPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("grant_one_time_permissions_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (!isOverlayGranted) "Grant Permissions & Enable Overlay" else "Grant Permissions (One-Time)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    Text(
                        text = "Continue to Dashboard",
                        color = MinimalTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionFeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (isGranted == false) MinimalThreatRedContainer else MinimalGreenContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted == false) MinimalThreatRed else MinimalGreenPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = MinimalTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (isGranted == false) {
                    Text(
                        text = "ACTION NEEDED",
                        color = MinimalThreatRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = subtitle,
                color = MinimalTextSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}
