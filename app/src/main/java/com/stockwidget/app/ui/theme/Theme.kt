package com.stockwidget.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// A restrained, mostly-neutral palette: near-white/charcoal surfaces with a single
// muted teal accent. Green/red are reserved for price direction only.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6B70),
    onPrimary = Color.White,
    background = Color(0xFFFBFBFA),
    onBackground = Color(0xFF1A1C1C),
    surface = Color(0xFFFBFBFA),
    onSurface = Color(0xFF1A1C1C),
    surfaceVariant = Color(0xFFEAEBEA),
    onSurfaceVariant = Color(0xFF6B6F6F),
    outlineVariant = Color(0xFFDADCDB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF66D2D8),
    onPrimary = Color(0xFF00363A),
    background = Color(0xFF111312),
    onBackground = Color(0xFFE2E3E2),
    surface = Color(0xFF111312),
    onSurface = Color(0xFFE2E3E2),
    surfaceVariant = Color(0xFF2A2D2C),
    onSurfaceVariant = Color(0xFFA8ABAA),
    outlineVariant = Color(0xFF3A3D3C),
)

/** Up/down accent colors shared across the app and matching the widget. */
val PriceUp = Color(0xFF2E7D32)
val PriceDown = Color(0xFFC62828)

@Composable
fun StockWidgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default to keep a consistent, minimal palette across devices.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    // Match the status/navigation bar icon colour to the theme, so the clock and
    // notification icons stay visible: dark icons on a light app, light icons on dark.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
