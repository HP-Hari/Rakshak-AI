package com.example.ui.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity

/**
 * FraudAlertOverlayManager
 *
 * Manages the floating system-wide WindowManager overlay (`TYPE_APPLICATION_OVERLAY`).
 * Displays an urgent visual warning banner over any screen or active phone dialer whenever
 * the TensorFlow Lite audio classifier or CallAudioInterceptor detects a phone scam.
 */
class FraudAlertOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var isOverlayShowing = false

    data class OverlayData(
        val callerNumber: String,
        val archetype: String,
        val confidence: Float,
        val stressLevel: Float,
        val reasoning: String,
        val recommendedAction: String,
        val acousticMarkers: List<String>
    )

    companion object {
        private const val TAG = "FraudAlertOverlay"
        private var instance: FraudAlertOverlayManager? = null

        fun getInstance(context: Context): FraudAlertOverlayManager {
            return instance ?: synchronized(this) {
                instance ?: FraudAlertOverlayManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Checks whether the SYSTEM_ALERT_WINDOW permission is granted.
     */
    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Opens the System Settings screen to grant Overlay Permission.
     */
    fun requestOverlayPermission(activityContext: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            activityContext.startActivity(intent)
        }
    }

    /**
     * Displays or updates the floating Fraud Alert Overlay on top of any active screen.
     */
    fun showFraudAlertOverlay(data: OverlayData) {
        if (!canDrawOverlays()) {
            Log.w(TAG, "Cannot draw overlay: SYSTEM_ALERT_WINDOW permission not granted. Triggering heads-up system notification.")
            showFallbackNotification(data)
            return
        }

        try {
            if (isOverlayShowing && overlayView != null) {
                updateOverlayContent(overlayView!!, data)
                return
            }

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 70 // Margin from status bar
            }

            val view = createOverlayView(data, params)
            windowManager.addView(view, params)
            overlayView = view
            isOverlayShowing = true
            Log.i(TAG, "Floating Fraud Alert Overlay displayed for archetype: ${data.archetype}")

        } catch (e: Exception) {
            Log.e(TAG, "Error displaying Fraud Alert Overlay: ${e.message}", e)
        }
    }

    /**
     * Dismisses and removes the floating overlay.
     */
    fun dismissOverlay() {
        try {
            if (isOverlayShowing && overlayView != null) {
                windowManager.removeView(overlayView)
                overlayView = null
                isOverlayShowing = false
                Log.i(TAG, "Fraud Alert Overlay dismissed.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing Fraud Alert Overlay: ${e.message}")
        }
    }

    private fun createOverlayView(data: OverlayData, layoutParams: WindowManager.LayoutParams): View {
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 12, 28, 12)
        }

        val cardBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 36f
            setColor(0xFF1E0B0E.toInt()) // Deep Dark Threat Crimson
            setStroke(3, 0xFFFF3B30.toInt()) // High-contrast Red border
        }

        val cardContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBackground
            setPadding(36, 32, 36, 32)
            elevation = 24f
        }

        // 1. Header Banner
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = ImageView(context).apply {
            setImageResource(android.R.drawable.stat_sys_warning)
            setColorFilter(0xFFFF3B30.toInt())
            val lp = LinearLayout.LayoutParams(60, 60)
            lp.rightMargin = 18
            this.layoutParams = lp
        }

        val titleColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            this.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleText = TextView(context).apply {
            text = "🚨 REAL-TIME SCAM CALL DETECTED"
            setTextColor(0xFFFF3B30.toInt())
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val subtitleText = TextView(context).apply {
            tag = "subtitle_text"
            text = "TensorFlow Lite Audio Sentry • ${(data.confidence * 100).toInt()}% Confidence"
            setTextColor(0xFF8E8E93.toInt())
            textSize = 11f
        }

        titleColumn.addView(titleText)
        titleColumn.addView(subtitleText)

        val closeBtn = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(0xFF8E8E93.toInt())
            this.layoutParams = LinearLayout.LayoutParams(48, 48)
            setOnClickListener { dismissOverlay() }
        }

        headerRow.addView(icon)
        headerRow.addView(titleColumn)
        headerRow.addView(closeBtn)
        cardContainer.addView(headerRow)

        // 2. Archetype & Caller Tag Banner
        val archetypeBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18f
            setColor(0xFF2C1014.toInt())
        }

        val archetypeContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = archetypeBg
            setPadding(24, 20, 24, 20)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 20
            lp.bottomMargin = 14
            this.layoutParams = lp
        }

        val archetypeText = TextView(context).apply {
            tag = "archetype_text"
            text = "⚠️ Threat: ${data.archetype}"
            setTextColor(0xFFFF453A.toInt())
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val markersText = TextView(context).apply {
            tag = "markers_text"
            text = if (data.acousticMarkers.isNotEmpty()) {
                "Acoustic Signals: ${data.acousticMarkers.joinToString(" • ")}"
            } else {
                "Reasoning: ${data.reasoning}"
            }
            setTextColor(0xFFE5E5EA.toInt())
            textSize = 11f
        }

        archetypeContainer.addView(archetypeText)
        archetypeContainer.addView(markersText)
        cardContainer.addView(archetypeContainer)

        // 3. Recommended Action Directive
        val actionText = TextView(context).apply {
            tag = "action_text"
            text = "👉 ${data.recommendedAction}"
            setTextColor(0xFFFFCC00.toInt())
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 20
            this.layoutParams = lp
        }
        cardContainer.addView(actionText)

        // 4. Action Buttons (Hang up / Open Rakshak)
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val hangUpButton = Button(context).apply {
            text = "🔴 HANG UP CALL"
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFFFF3B30.toInt())
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.rightMargin = 10
            this.layoutParams = lp
            setOnClickListener {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
                dismissOverlay()
            }
        }

        val viewDetailsButton = Button(context).apply {
            text = "🛡️ VIEW DETAILS"
            setTextColor(0xFF00E5FF.toInt())
            setBackgroundColor(0xFF1E262B.toInt())
            textSize = 11f
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.leftMargin = 10
            this.layoutParams = lp
            setOnClickListener {
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("NAVIGATE_TO", "CALL_GUARDIAN")
                }
                context.startActivity(openIntent)
                dismissOverlay()
            }
        }

        buttonRow.addView(hangUpButton)
        buttonRow.addView(viewDetailsButton)
        cardContainer.addView(buttonRow)

        rootLayout.addView(cardContainer)

        // Allow user to drag overlay vertically
        var initialY = 0
        var initialTouchY = 0f
        cardContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = layoutParams.y
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager.updateViewLayout(rootLayout, layoutParams)
                    } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        return rootLayout
    }

    private fun updateOverlayContent(view: View, data: OverlayData) {
        val subtitle = view.findViewWithTag<TextView>("subtitle_text")
        val archetype = view.findViewWithTag<TextView>("archetype_text")
        val markers = view.findViewWithTag<TextView>("markers_text")
        val action = view.findViewWithTag<TextView>("action_text")

        subtitle?.text = "NPU & Speech Sentry • ${(data.confidence * 100).toInt()}% Confidence"
        archetype?.text = "⚠️ Threat: ${data.archetype}"
        markers?.text = if (data.acousticMarkers.isNotEmpty()) {
            "Detected Triggers: ${data.acousticMarkers.joinToString(" • ")}"
        } else {
            "Reasoning: ${data.reasoning}"
        }
        action?.text = "👉 ${data.recommendedAction}"
    }

    private fun showFallbackNotification(data: OverlayData) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            val channelId = "rakshak_overlay_fallback_channel"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Rakshak Scam Call Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent high-priority alerts for detected phone scams during active calls"
                    enableVibration(true)
                    enableLights(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("NAVIGATE_TO", "CALL_GUARDIAN")
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("🚨 SCAM CALL DETECTED: ${data.archetype}")
                .setContentText(data.recommendedAction)
                .setStyle(
                    androidx.core.app.NotificationCompat.BigTextStyle().bigText(
                        "Caller: ${data.callerNumber}\n\n⚠️ Threat: ${data.archetype}\n\n👉 Reason: ${data.reasoning}\n\n⛔ ACTION: ${data.recommendedAction}\nDo not share OTP, passwords, or personal details."
                    )
                )
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(7007, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display fallback alert notification: ${e.message}", e)
        }
    }
}
