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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF00696E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF6FF6FE),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6365),
    background = Color(0xFFFAFDFC),
    surface = Color(0xFFFAFDFC),
    surfaceVariant = Color(0xFFDAE4E4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4CD9E2),
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF004F54),
    onPrimaryContainer = Color(0xFF6FF6FE),
    secondary = Color(0xFFB1CBCD),
    background = Color(0xFF191C1C),
    surface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFF3F4948),
)

/** Up/down accent colors shared across the app and matching the widget. */
val PriceUp = Color(0xFF2E7D32)
val PriceDown = Color(0xFFC62828)

@Composable
fun StockWidgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
