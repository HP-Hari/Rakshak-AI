package com.example.ui.screens.vyapar

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.KhataEntryEntity
import com.example.data.local.entity.UpiTransactionEntity
import com.example.data.model.KhataEntryType
import com.example.data.model.SnapKhataItem
import com.example.engine.AcousticDetectionEvent
import com.example.ui.RakshakMainViewModel
import com.example.ui.SmsAiAnalysisState
import com.example.ui.VyaparTab
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.theme.MinimalAccentAmber
import com.example.ui.theme.MinimalAmberContainer
import com.example.ui.theme.MinimalCanvas
import com.example.ui.theme.MinimalGreenContainer
import com.example.ui.theme.MinimalGreenPrimary
import com.example.ui.theme.MinimalOnGreenPrimary
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
fun SmartVyaparScreen(
    viewModel: RakshakMainViewModel,
    modifier: Modifier = Modifier
) {
    val activeTab by viewModel.activeVyaparTab.collectAsStateWithLifecycle()
    val todayTotal by viewModel.todayTotalCollection.collectAsStateWithLifecycle()
    val totalInflow by viewModel.totalInflowReceived.collectAsStateWithLifecycle()
    val upiList by viewModel.allTransactions.collectAsStateWithLifecycle()
    val latestAlert by viewModel.latestAlertFeedItem.collectAsStateWithLifecycle()
    val smsAiState by viewModel.smsAiState.collectAsStateWithLifecycle()

    // Received Amounts Tracker Flows
    val todayReceived by viewModel.todayReceivedTotal.collectAsStateWithLifecycle()
    val thisWeekReceived by viewModel.thisWeekReceivedTotal.collectAsStateWithLifecycle()
    val thisMonthReceived by viewModel.thisMonthReceivedTotal.collectAsStateWithLifecycle()
    val allTimeReceived by viewModel.allTimeReceivedTotal.collectAsStateWithLifecycle()
    val smsReceived by viewModel.smsReceivedTotal.collectAsStateWithLifecycle()
    val upiAppsReceived by viewModel.upiAppsReceivedTotal.collectAsStateWithLifecycle()
    val khataJamaReceived by viewModel.khataJamaTotal.collectAsStateWithLifecycle()
    val avgTransactionSize by viewModel.averageTransactionSize.collectAsStateWithLifecycle()
    val highestTransaction by viewModel.highestTransactionAmount.collectAsStateWithLifecycle()
    val totalTransactionsCount by viewModel.totalReceivedTransactionsCount.collectAsStateWithLifecycle()

    var showManualAddUpi by remember { mutableStateOf(false) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MinimalCanvas)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Hero Summary Card: Real ₹ Inflow + In-Device SQLite Sync
            HeroVyaparCard(
                totalInflow = totalInflow,
                todayTotal = todayTotal ?: 0.0,
                upiCount = upiList.size
            )

            // Live Alert Banner (Animated entry upon Bank SMS or UPI Push Alert intercept)
            AnimatedVisibility(
                visible = latestAlert != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                latestAlert?.let { alert ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("live_alert_banner"),
                        colors = CardDefaults.cardColors(containerColor = MinimalSurfaceElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalGreenPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MinimalGreenContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (alert.source == com.example.data.model.TransactionSource.BANK_SMS) Icons.Default.Email else Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = MinimalGreenPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = alert.title,
                                        color = MinimalGreenPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = alert.description,
                                        color = MinimalTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.dismissLatestAlert() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Dismiss",
                                    tint = MinimalTextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-tabs: Transactions & AI SMS | Payment Analytics
            TabRow(
                selectedTabIndex = activeTab.ordinal,
                containerColor = MinimalSurfaceElevated,
                contentColor = MinimalGreenPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab.ordinal]),
                        color = MinimalGreenPrimary,
                        height = 3.dp
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MinimalSurfaceBorder, RoundedCornerShape(12.dp))
            ) {
                VyaparTab.values().forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { viewModel.setVyaparTab(tab) },
                        text = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Medium,
                                color = if (activeTab == tab) MinimalGreenPrimary else MinimalTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Content Area by Active Tab
            when (activeTab) {
                VyaparTab.UPI_LIVE -> {
                    UpiLiveFeedTab(
                        transactions = upiList,
                        onOpenRecordPayment = { showManualAddUpi = true },
                        onReplayAudio = { amount, payer, prov ->
                            viewModel.replaySoundboxAnnouncement(amount, payer, prov)
                        },
                        onDeleteTransaction = { viewModel.deleteUpiTransaction(it) },
                        onClearAllHistory = { showClearHistoryConfirm = true }
                    )
                }
                VyaparTab.RECEIVED_TRACKER -> {
                    ReceivedAmountsTrackerTab(
                        todayAmount = todayReceived,
                        thisWeekAmount = thisWeekReceived,
                        thisMonthAmount = thisMonthReceived,
                        allTimeAmount = allTimeReceived,
                        smsAmount = smsReceived,
                        upiAppsAmount = upiAppsReceived,
                        khataJamaAmount = khataJamaReceived,
                        averageAmount = avgTransactionSize,
                        highestAmount = highestTransaction,
                        totalTxnCount = totalTransactionsCount,
                        transactions = upiList,
                        onDeleteTransaction = { viewModel.deleteUpiTransaction(it) }
                    )
                }
            }
        }

        // Manual Record Real UPI Payment Dialog
        if (showManualAddUpi) {
            ManualRecordUpiPaymentDialog(
                onDismiss = { showManualAddUpi = false },
                onSave = { payer, amt, app ->
                    viewModel.recordDirectUpiPayment(payer, amt, app)
                    showManualAddUpi = false
                }
            )
        }

        // Confirmation Dialog for clearing all transaction history
        if (showClearHistoryConfirm) {
            AlertDialog(
                onDismissRequest = { showClearHistoryConfirm = false },
                title = { Text("Clear Transaction History?", fontWeight = FontWeight.Bold) },
                text = { Text("This will remove all recorded transactions from the on-device database. This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllTransactions()
                            showClearHistoryConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MinimalThreatRed)
                    ) {
                        Text("Clear All", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// ----------------------------------------------------
// HERO COMPONENT: TOTAL COLLECTION SUMMARY CARD
// ----------------------------------------------------

@Composable
private fun HeroVyaparCard(
    totalInflow: Double,
    todayTotal: Double,
    upiCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MinimalSurface),
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
                        text = "TOTAL COLLECTIONS RECEIVED",
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
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MinimalGreenPrimary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ROOM DB SYNCED",
                            color = MinimalGreenPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Big Currency Amount (Total Inflow)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "₹",
                        color = MinimalGreenPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format(Locale.getDefault(), "%,.0f", totalInflow),
                        color = MinimalTextPrimary,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = ".00",
                        color = MinimalTextSecondary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Surface(
                    color = MinimalSurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorder)
                ) {
                    Text(
                        text = "REAL-TIME INFLOW",
                        color = MinimalGreenPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metric Pillars: Today's Inflow vs Total Txns vs Sentry Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MinimalSurfaceElevated)
                    .border(1.dp, MinimalSurfaceBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MetricColumn(
                    label = "Today's Inflow",
                    value = "₹${todayTotal.toInt()}",
                    valueColor = MinimalGreenPrimary
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(MinimalSurfaceBorder)
                )
                MetricColumn(
                    label = "Verified Receipts",
                    value = "$upiCount txns",
                    valueColor = MinimalTechCyan
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(MinimalSurfaceBorder)
                )
                MetricColumn(
                    label = "AI Voice Sentry",
                    value = "Active",
                    valueColor = MinimalGreenPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Live Engine Listeners Status Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EngineStatusPill(
                    icon = Icons.Default.Email,
                    label = "Bank SMS AI Sentry",
                    isActive = true
                )
                EngineStatusPill(
                    icon = Icons.Default.Notifications,
                    label = "UPI Push Sentry",
                    isActive = true
                )
                EngineStatusPill(
                    icon = Icons.Default.AccountBalance,
                    label = "Room SQLite",
                    isActive = true
                )
            }
        }
    }
}

@Composable
private fun EngineStatusPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MinimalSurfaceElevated)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) MinimalGreenPrimary else MinimalTextMuted,
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = MinimalTextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = MinimalTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = valueColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ----------------------------------------------------
// TAB 1: TRANSACTIONS & HISTORY (CLEAN, REAL DATA ONLY)
// ----------------------------------------------------

@Composable
private fun UpiLiveFeedTab(
    transactions: List<UpiTransactionEntity>,
    onOpenRecordPayment: () -> Unit,
    onReplayAudio: (Double, String, String) -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    onClearAllHistory: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredTransactions = remember(transactions, selectedFilter) {
        when (selectedFilter) {
            "SMS" -> transactions.filter { it.packageName.contains("sms") || it.upiApp.contains("SMS") }
            "UPI_APP" -> transactions.filter { !it.packageName.contains("sms") && !it.upiApp.contains("SMS") }
            else -> transactions
        }
    }

    val smsCount = transactions.count { it.packageName.contains("sms") || it.upiApp.contains("SMS") }
    val pushCount = transactions.size - smsCount

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Top Action Bar: Title & Manual Entry Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TRANSACTION HISTORY",
                        color = MinimalTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Auto-populated by Real SMS & Notification Listeners",
                        color = MinimalTextMuted,
                        fontSize = 10.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (transactions.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onClearAllHistory,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MinimalThreatRed),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MinimalThreatRed.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MinimalThreatRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clear",
                                color = MinimalThreatRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Button(
                        onClick = onOpenRecordPayment,
                        colors = ButtonDefaults.buttonColors(containerColor = MinimalGreenPrimary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("record_payment_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "+ Record",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Filter Chips Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipItem(
                    label = "All (${transactions.size})",
                    isSelected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" }
                )
                FilterChipItem(
                    label = "📩 Bank SMS ($smsCount)",
                    isSelected = selectedFilter == "SMS",
                    onClick = { selectedFilter = "SMS" }
                )
                FilterChipItem(
                    label = "🔔 App Alerts ($pushCount)",
                    isSelected = selectedFilter == "UPI_APP",
                    onClick = { selectedFilter = "UPI_APP" }
                )
            }
        }

        // Transaction List / Empty State
        if (filteredTransactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MinimalSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(MinimalGreenContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = MinimalGreenPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Awaiting Auto-Populated Transactions",
                            color = MinimalTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Incoming Bank SMS (HDFC, SBI, ICICI, etc.) and UPI push alerts (PhonePe, Google Pay, Paytm) are automatically processed by AI, announced aloud, and stored securely in your on-device database.",
                            color = MinimalTextSecondary,
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        } else {
            items(filteredTransactions, key = { it.id }) { txn ->
                UpiTransactionCard(
                    txn = txn,
                    onReplayAudio = onReplayAudio,
                    onDelete = { onDeleteTransaction(txn.id) }
                )
            }
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MinimalGreenContainer else MinimalSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) MinimalGreenPrimary else MinimalSurfaceBorder
        ),
        modifier = Modifier.height(30.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (isSelected) MinimalGreenPrimary else MinimalTextSecondary,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun UpiTransactionCard(
    txn: UpiTransactionEntity,
    onReplayAudio: (Double, String, String) -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val timeFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(txn.timestamp))
    val isSms = txn.packageName.contains("sms") || txn.upiApp.contains("SMS")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(containerColor = MinimalSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    isSms -> Color(0xFFE0F2FE)
                                    txn.upiApp.contains("PhonePe") -> Color(0xFFF3E8FF)
                                    txn.upiApp.contains("Paytm") -> Color(0xFFE0F2FE)
                                    txn.upiApp.contains("Google") -> Color(0xFFDCFCE7)
                                    else -> MinimalAmberContainer
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSms) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = Color(0xFF0369A1),
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = txn.upiApp.take(2).uppercase(),
                                color = when {
                                    txn.upiApp.contains("PhonePe") -> Color(0xFF6B21A8)
                                    txn.upiApp.contains("Paytm") -> Color(0xFF0369A1)
                                    txn.upiApp.contains("Google") -> Color(0xFF15803D)
                                    else -> MinimalAccentAmber
                                },
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = txn.payerName,
                            color = MinimalTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${txn.upiApp} • $formattedTime",
                                color = MinimalTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = "Ref: ${txn.referenceId}",
                            color = MinimalTextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "+₹${txn.amount.toInt()}",
                            color = MinimalGreenPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Surface(
                            color = MinimalGreenContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "VERIFIED IN-DB",
                                color = MinimalGreenPrimary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = { onReplayAudio(txn.amount, txn.payerName, txn.upiApp) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Speak Announcement",
                            tint = MinimalGreenPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MinimalTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Expandable raw payload details
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MinimalSurfaceElevated)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (isSms) "RAW BANK SMS LOG (INTERCEPTED ON-DEVICE):" else "RAW NOTIFICATION LOG:",
                        color = MinimalTextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = txn.rawNotificationText.ifBlank { "Direct UPI Record • Reference ID: ${txn.referenceId}" },
                        color = MinimalTextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 2: RECEIVED AMOUNTS TRACKER (REAL-TIME METRICS)
// ----------------------------------------------------

@Composable
private fun ReceivedAmountsTrackerTab(
    todayAmount: Double,
    thisWeekAmount: Double,
    thisMonthAmount: Double,
    allTimeAmount: Double,
    smsAmount: Double,
    upiAppsAmount: Double,
    khataJamaAmount: Double,
    averageAmount: Double,
    highestAmount: Double,
    totalTxnCount: Int,
    transactions: List<UpiTransactionEntity>,
    onDeleteTransaction: (Long) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredList = remember(transactions, searchQuery) {
        if (searchQuery.isBlank()) transactions
        else transactions.filter {
            it.payerName.contains(searchQuery, ignoreCase = true) ||
            it.upiApp.contains(searchQuery, ignoreCase = true) ||
            it.referenceId.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Section 1: Period Metrics Grid (Today, This Week, This Month, All Time)
        item {
            Column {
                Text(
                    text = "RECEIVED AMOUNTS SUMMARY",
                    color = MinimalTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TrackerPeriodCard(
                        title = "Today",
                        amount = todayAmount,
                        icon = Icons.Default.Payments,
                        color = MinimalGreenPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    TrackerPeriodCard(
                        title = "This Week",
                        amount = thisWeekAmount,
                        icon = Icons.Default.DateRange,
                        color = MinimalTechCyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TrackerPeriodCard(
                        title = "This Month",
                        amount = thisMonthAmount,
                        icon = Icons.Default.CalendarMonth,
                        color = MinimalAccentAmber,
                        modifier = Modifier.weight(1f)
                    )
                    TrackerPeriodCard(
                        title = "All Time",
                        amount = allTimeAmount,
                        icon = Icons.Default.TrendingUp,
                        color = MinimalGreenPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Section 2: Channel Breakdown (Bank SMS vs UPI Apps vs Khata Cash)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MinimalSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorder),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "COLLECTION BY CHANNEL",
                        color = MinimalTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ChannelRow(
                        title = "Bank SMS Inflow (HDFC, SBI, ICICI, etc.)",
                        amount = smsAmount,
                        total = allTimeAmount,
                        color = Color(0xFF0284C7)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ChannelRow(
                        title = "UPI Apps (PhonePe, GPay, Paytm)",
                        amount = upiAppsAmount,
                        total = allTimeAmount,
                        color = MinimalGreenPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ChannelRow(
                        title = "Khata Cash Jama Payments",
                        amount = khataJamaAmount,
                        total = allTimeAmount + khataJamaAmount,
                        color = MinimalAccentAmber
                    )
                }
            }
        }

        // Section 3: Performance Insights (Avg Ticket, Peak Receipt, Total Count)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MinimalSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorder),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    InsightStat(
                        label = "Avg Receipt",
                        value = "₹${averageAmount.toInt()}",
                        color = MinimalTechCyan
                    )
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(MinimalSurfaceBorder))
                    InsightStat(
                        label = "Highest Receipt",
                        value = "₹${highestAmount.toInt()}",
                        color = MinimalGreenPrimary
                    )
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(MinimalSurfaceBorder))
                    InsightStat(
                        label = "Total Receipts",
                        value = "$totalTxnCount",
                        color = MinimalTextPrimary
                    )
                }
            }
        }

        // Section 4: Filterable Receipts List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECEIPTS BREAKDOWN (${filteredList.size})",
                    color = MinimalTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by customer, bank, or UTR...", fontSize = 12.sp, color = MinimalTextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MinimalTextMuted, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MinimalTextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MinimalGreenPrimary,
                    unfocusedBorderColor = MinimalSurfaceBorder,
                    focusedContainerColor = MinimalSurfaceElevated,
                    unfocusedContainerColor = MinimalSurfaceElevated
                )
            )
        }

        if (filteredList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MinimalSurfaceElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "No verified receipts recorded yet." else "No receipts match '$searchQuery'",
                            color = MinimalTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(filteredList, key = { it.id }) { txn ->
                ReceiptItemCard(
                    txn = txn,
                    onDelete = { onDeleteTransaction(txn.id) }
                )
            }
        }
    }
}

@Composable
private fun TrackerPeriodCard(
    title: String,
    amount: Double,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MinimalSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = MinimalTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "₹${String.format(Locale.getDefault(), "%,.0f", amount)}",
                color = MinimalTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun ChannelRow(
    title: String,
    amount: Double,
    total: Double,
    color: Color
) {
    val percentage = if (total > 0) (amount / total).coerceIn(0.0, 1.0).toFloat() else 0f
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = MinimalTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "₹${amount.toInt()} (${(percentage * 100).toInt()}%)",
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MinimalSurfaceElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percentage)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun InsightStat(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = MinimalTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ReceiptItemCard(
    txn: UpiTransactionEntity,
    onDelete: () -> Unit
) {
    val timeFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(txn.timestamp))
    val isSms = txn.packageName.contains("sms") || txn.upiApp.contains("SMS")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MinimalSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isSms) Color(0xFFE0F2FE) else MinimalGreenContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSms) Icons.Default.Email else Icons.Default.Payments,
                        contentDescription = null,
                        tint = if (isSms) Color(0xFF0369A1) else MinimalGreenPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = txn.payerName,
                        color = MinimalTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${txn.upiApp} • $formattedTime",
                        color = MinimalTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "+₹${txn.amount.toInt()}",
                    color = MinimalGreenPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(8.dp))
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
    }
}

@Composable
private fun ManualRecordUpiPaymentDialog(
    onDismiss: () -> Unit,
    onSave: (payerName: String, amount: Double, upiApp: String) -> Unit
) {
    var payer by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf("PhonePe") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MinimalSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Record Real UPI Payment",
                    color = MinimalTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = payer,
                    onValueChange = { payer = it },
                    label = { Text("Customer / Payer Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "UPI Provider:", color = MinimalTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("PhonePe", "Paytm", "Google Pay").forEach { app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { selectedApp = app }
                        ) {
                            RadioButton(
                                selected = selectedApp == app,
                                onClick = { selectedApp = app },
                                colors = RadioButtonDefaults.colors(selectedColor = MinimalGreenPrimary)
                            )
                            Text(text = app, fontSize = 11.sp, color = MinimalTextPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MinimalTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull() ?: 0.0
                            if (payer.isNotBlank() && amt > 0) {
                                onSave(payer, amt, selectedApp)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MinimalGreenPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Record & Announce", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
