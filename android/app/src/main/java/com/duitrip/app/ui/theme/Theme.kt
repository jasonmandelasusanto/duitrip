package com.duitrip.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Duitrip ships a single dark theme (the web app renders <html class="dark"> only).
private val DuitripColorScheme = darkColorScheme(
    primary = Teal,
    onPrimary = BgBase,
    primaryContainer = TealDark,
    onPrimaryContainer = TextPrimary,
    secondary = Amber,
    onSecondary = BgBase,
    background = BgBase,
    onBackground = TextPrimary,
    surface = BgSurface,
    onSurface = TextPrimary,
    surfaceVariant = BgElevated,
    onSurfaceVariant = TextSecondary,
    outline = BgBorder,
    error = Danger,
    onError = TextPrimary,
)

@Composable
fun DuitripTheme(
    // Kept for API symmetry; the app is dark-only regardless of the system setting.
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BgBase.toArgb()
            window.navigationBarColor = BgBase.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = DuitripColorScheme,
        typography = Typography,
    ) {
        // Wrap in a Surface so the default content (text/icon) color is the theme's
        // light color everywhere — otherwise bare Text() falls back to black.
        Surface(modifier = Modifier.fillMaxSize(), color = BgBase, contentColor = TextPrimary) {
            content()
        }
    }
}
