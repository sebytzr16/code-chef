package com.voiceai.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.voiceai.app.viewmodel.MainViewModel

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    val bg = when (uiState.screen) {
        MainViewModel.Screen.LISTENING  -> Color(0xFF0A1520)
        MainViewModel.Screen.SPEAKING   -> Color(0xFF1A0C0A)
        MainViewModel.Screen.THINKING,
        MainViewModel.Screen.SEARCHING  -> Color(0xFF0D0A1A)
        else                            -> Color(0xFF080808)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        when (uiState.screen) {
            MainViewModel.Screen.NEEDS_MODEL  -> ModelSetupScreen(onDownload = viewModel::startModelDownload)
            MainViewModel.Screen.DOWNLOADING  -> DownloadScreen(progress = uiState.downloadProgress)
            else                              -> ConversationScreen(uiState, viewModel::togglePlayStop)
        }
    }
}

// ---- Screens ----

@Composable
private fun ConversationScreen(uiState: MainViewModel.UiState, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(48.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f),
        ) {
            PulsingOrb(screen = uiState.screen)

            Spacer(Modifier.height(28.dp))

            Text(
                text = screenLabel(uiState.screen),
                color = orbColor(uiState.screen),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
            )

            if (uiState.statusText.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = uiState.statusText,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
            }

            if (uiState.partialSpeech.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "\"${uiState.partialSpeech}\"",
                    color = Color(0xFF64B5F6).copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        val showButton = uiState.screen !in setOf(
            MainViewModel.Screen.INITIALIZING,
            MainViewModel.Screen.NEEDS_MODEL,
            MainViewModel.Screen.DOWNLOADING,
        )

        if (showButton) {
            FloatingActionButton(
                onClick = onToggle,
                shape = CircleShape,
                containerColor = orbColor(uiState.screen),
                contentColor = Color.White,
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    imageVector = if (uiState.screen == MainViewModel.Screen.STOPPED)
                        Icons.Default.PlayArrow else Icons.Default.Stop,
                    contentDescription = if (uiState.screen == MainViewModel.Screen.STOPPED)
                        "Resume" else "Stop",
                    modifier = Modifier.size(38.dp),
                )
            }
        } else {
            Spacer(Modifier.size(72.dp))
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ModelSetupScreen(onDownload: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(80.dp)
                .background(Color(0xFF42A5F5).copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .background(Color(0xFF42A5F5).copy(alpha = 0.5f), CircleShape)
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            "AI Model Required",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "The Gemma 2B language model (~1.4 GB) is downloaded once\n" +
                    "and stored locally on your device. Wi-Fi recommended.",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onDownload,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF42A5F5)),
        ) {
            Text("Download Model", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Requires ~1.5 GB free storage",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.35f),
        )
    }
}

@Composable
private fun DownloadScreen(progress: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.size(96.dp),
            color = Color(0xFF42A5F5),
            strokeWidth = 6.dp,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "Downloading model…  $progress%",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.8f),
        )

        Spacer(Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { progress / 100f },
            color = Color(0xFF42A5F5),
            trackColor = Color.White.copy(alpha = 0.15f),
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Keep the app open. This can take a few minutes.",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
        )
    }
}

// ---- Pulsing orb ----

@Composable
private fun PulsingOrb(screen: MainViewModel.Screen) {
    val isAnimated = screen in setOf(
        MainViewModel.Screen.LISTENING,
        MainViewModel.Screen.SPEAKING,
        MainViewModel.Screen.THINKING,
        MainViewModel.Screen.SEARCHING,
    )

    val transition = rememberInfiniteTransition(label = "orb")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isAnimated) when (screen) {
            MainViewModel.Screen.LISTENING -> 1.35f
            MainViewModel.Screen.SPEAKING  -> 1.25f
            else                           -> 1.15f
        } else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (screen) {
                    MainViewModel.Screen.LISTENING -> 550
                    MainViewModel.Screen.SPEAKING  -> 380
                    else                           -> 900
                },
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    val color = orbColor(screen)

    Box(contentAlignment = Alignment.Center) {
        // Outer halo
        if (isAnimated) {
            Box(
                Modifier
                    .size(160.dp)
                    .scale(scale)
                    .background(color.copy(alpha = 0.10f), CircleShape)
            )
        }
        // Mid ring
        Box(
            Modifier
                .size(116.dp)
                .scale(if (isAnimated) scale * 0.92f else 1f)
                .background(color.copy(alpha = 0.22f), CircleShape)
        )
        // Core
        Box(
            Modifier
                .size(76.dp)
                .background(color.copy(alpha = 0.82f), CircleShape)
        )
    }
}

// ---- Helpers ----

private fun screenLabel(screen: MainViewModel.Screen) = when (screen) {
    MainViewModel.Screen.INITIALIZING -> "Initializing"
    MainViewModel.Screen.NEEDS_MODEL  -> "Setup required"
    MainViewModel.Screen.DOWNLOADING  -> "Downloading"
    MainViewModel.Screen.SPEAKING     -> "Speaking"
    MainViewModel.Screen.LISTENING    -> "Listening"
    MainViewModel.Screen.THINKING     -> "Thinking"
    MainViewModel.Screen.SEARCHING    -> "Searching"
    MainViewModel.Screen.STOPPED      -> "Paused"
    MainViewModel.Screen.ERROR        -> "Error"
}

private fun orbColor(screen: MainViewModel.Screen) = when (screen) {
    MainViewModel.Screen.LISTENING -> Color(0xFF42A5F5)  // blue
    MainViewModel.Screen.SPEAKING  -> Color(0xFFFF7043)  // orange
    MainViewModel.Screen.THINKING  -> Color(0xFFAB47BC)  // purple
    MainViewModel.Screen.SEARCHING -> Color(0xFF26C6DA)  // teal
    MainViewModel.Screen.STOPPED   -> Color(0xFF78909C)  // grey
    MainViewModel.Screen.ERROR     -> Color(0xFFEF5350)  // red
    else                           -> Color(0xFF546E7A)  // dark blue-grey
}
