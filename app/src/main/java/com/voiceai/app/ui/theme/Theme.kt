package com.voiceai.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colorScheme = darkColorScheme(
    primary = Color(0xFF42A5F5),
    secondary = Color(0xFFAB47BC),
    tertiary = Color(0xFFFF7043),
    background = Color(0xFF080808),
    surface = Color(0xFF111111),
)

@Composable
fun VoiceAITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
