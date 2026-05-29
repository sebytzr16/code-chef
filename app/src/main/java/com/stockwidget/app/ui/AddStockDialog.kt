package com.stockwidget.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stockwidget.app.data.remote.SymbolMatch
import kotlinx.coroutines.delay

@Composable
fun AddStockDialog(
    viewModel: StockViewModel,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SymbolMatch>>(emptyList()) }

    // Debounced symbol search (only works once an API key is set).
    LaunchedEffect(query) {
        if (query.length < 1 || !viewModel.hasApiKey) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        results = viewModel.search(query).take(8)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a stock") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.uppercase() },
                    label = { Text("Ticker symbol (e.g. AAPL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (results.isNotEmpty()) {
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(results) { match ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addStock(match.symbol, match.description)
                                        onDismiss()
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(match.symbol, fontWeight = FontWeight.Bold)
                                if (match.description.isNotBlank()) {
                                    Text(match.description)
                                }
                            }
                            Divider()
                        }
                    }
                } else if (!viewModel.hasApiKey) {
                    Text(
                        "Tip: add your Finnhub API key in Settings to search by name.",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = query.isNotBlank(),
                onClick = {
                    viewModel.addStock(query, query)
                    onDismiss()
                }
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
