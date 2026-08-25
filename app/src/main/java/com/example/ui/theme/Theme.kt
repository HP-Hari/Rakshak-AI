package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CleanMinimalismColorScheme = lightColorScheme(
    primary = MinimalGreenPrimary,
    onPrimary = MinimalOnGreenPrimary,
    primaryContainer = MinimalGreenContainer,
    onPrimaryContainer = MinimalOnGreenContainer,
    secondary = MinimalAccentAmber,
    onSecondary = MinimalOnAmber,
    secondaryContainer = MinimalAmberContainer,
    onSecondaryContainer = Color(0xFF241A00),
    tertiary = MinimalTechCyan,
    onTertiary = Color.White,
    tertiaryContainer = MinimalTechCyanContainer,
    onTertiaryContainer = Color(0xFF001F25),
    error = MinimalThreatRed,
    onError = MinimalOnThreatRed,
    errorContainer = MinimalThreatRedContainer,
    onErrorContainer = Color(0xFF410002),
    background = MinimalCanvas,
    onBackground = MinimalTextPrimary,
    surface = MinimalSurface,
    onSurface = MinimalTextPrimary,
    surfaceVariant = MinimalSurfaceVariant,
    onSurfaceVariant = MinimalTextSecondary,
    outline = MinimalSurfaceBorder,
    outlineVariant = MinimalTextMuted
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = CleanMinimalismColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = MinimalCanvas.toArgb()
                window.navigationBarColor = MinimalCanvas.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

