package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppProfile
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

@Composable
fun IndustrialTopBar(
    currentProfile: AppProfile,
    onProfileSelected: (AppProfile) -> Unit,
    onOpenDevServer: () -> Unit,
    liveLatencyMs: Float = 8.4f,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MinimalCanvas,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header Row: Brand title + Hardware Status Pill + Dev Bridge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MinimalGreenContainer)
                            .border(1.dp, MinimalGreenPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Shield",
                            tint = MinimalGreenPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "RAKSHAK",
                                color = MinimalTextPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "AI",
                                color = MinimalGreenPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                        }
                        Text(
                            text = "100% ON-DEVICE • ZERO CLOUD",
                            color = MinimalGreenPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Hardware NPU badge and Dev Button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // NPU Hardware status chip with dynamic real latency
                    Surface(
                        color = MinimalSurfaceElevated,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MinimalGreenPrimary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "NPU ${String.format(java.util.Locale.US, "%.1f", liveLatencyMs)}ms",
                                color = MinimalTechCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // On-Device Engine Diagnostics button
                    IconButton(
                        onClick = onOpenDevServer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "NPU Engine Status",
                            tint = MinimalTechCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Profile Dual Switcher: Smart Vyapar vs Call Guardian
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MinimalSurfaceElevated)
                    .border(1.dp, MinimalSurfaceBorder, RoundedCornerShape(16.dp))
                    .padding(4.dp)
            ) {
                ProfileSegmentButton(
                    title = "Smart Vyapar",
                    subtitle = "Kirana Mode",
                    icon = Icons.Default.Storefront,
                    isSelected = currentProfile == AppProfile.SMART_VYAPAR,
                    activeColor = MinimalGreenPrimary,
                    onClick = { onProfileSelected(AppProfile.SMART_VYAPAR) },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                ProfileSegmentButton(
                    title = "Call Guardian",
                    subtitle = "Anti-Scam Mode",
                    icon = Icons.Default.PhoneInTalk,
                    isSelected = currentProfile == AppProfile.CALL_GUARDIAN,
                    activeColor = MinimalGreenPrimary,
                    onClick = { onProfileSelected(AppProfile.CALL_GUARDIAN) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ProfileSegmentButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MinimalSurface else Color.Transparent,
        label = "segmentBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MinimalSurfaceBorder else Color.Transparent,
        label = "segmentBorder"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) activeColor else MinimalTextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    color = if (isSelected) MinimalTextPrimary else MinimalTextSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp
                )
                Text(
                    text = subtitle,
                    color = if (isSelected) activeColor else MinimalTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

