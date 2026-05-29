package com.stockwidget.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    viewModel: StockViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var key by remember { mutableStateOf(state.apiKey) }
    var minutes by remember { mutableIntStateOf(state.refreshMinutes) }

    val options = listOf(15, 30, 60, 120)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                Text("Finnhub API key", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Paste your free key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Get one free at finnhub.io → Dashboard.",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Text("Refresh interval", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { opt ->
                        FilterChip(
                            selected = minutes == opt,
                            onClick = { minutes = opt },
                            label = { Text(if (opt < 60) "${opt}m" else "${opt / 60}h") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.saveApiKey(key)
                viewModel.setRefreshMinutes(minutes)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
