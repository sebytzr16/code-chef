package com.stockwidget.app.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockwidget.app.data.model.StockQuote
import com.stockwidget.app.ui.theme.PriceDown
import com.stockwidget.app.ui.theme.PriceUp
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockListScreen(
    viewModel: StockViewModel,
    onOpenDetail: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My Stocks", style = MaterialTheme.typography.headlineSmall)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add stock") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.quotes.isEmpty()) {
                EmptyState(
                    hasKey = viewModel.hasApiKey,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.quotes, key = { it.symbol }) { quote ->
                        StockCard(
                            quote = quote,
                            onClick = { onOpenDetail(quote.symbol) },
                            onRemove = { viewModel.removeStock(quote.symbol) }
                        )
                    }
                }
            }
            if (state.isLoading) {
                CircularProgressIndicator(
                    Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                )
            }
        }
    }

    if (showAdd) {
        AddStockDialog(
            viewModel = viewModel,
            onDismiss = { showAdd = false }
        )
    }
    if (showSettings) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettings = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockCard(quote: StockQuote, onClick: () -> Unit, onRemove: () -> Unit) {
    val up = quote.isUp
    val accent = if (up) PriceUp else PriceDown
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: symbol + name + open/prev close.
            Column(Modifier.weight(1f)) {
                Text(
                    quote.symbol.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    quote.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                if (quote.hasData || quote.history.isNotEmpty()) {
                    Text(
                        "Open ${money(quote.open)}  ·  Prev ${money(quote.previousClose)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        quote.error ?: "Loading…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Middle: price + change.
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (quote.hasData || quote.history.isNotEmpty()) money(quote.current) else "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (quote.hasData || quote.history.isNotEmpty()) {
                    val arrow = if (up) "▲" else "▼"
                    Text(
                        "$arrow ${money(abs(quote.change))} (${percent(quote.changePercent)})",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Right: chart.
            Sparkline(
                quote = quote,
                modifier = Modifier.width(84.dp).height(48.dp)
            )

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyState(hasKey: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (hasKey) "No stocks yet" else "Welcome 👋",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasKey) {
                "Tap \"Add stock\" to start tracking, then add the widget to your home screen."
            } else {
                "Open Settings to paste your free Finnhub API key, then add the stocks you own."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun money(v: Float): String = "$" + String.format("%,.2f", v)
private fun percent(v: Float): String = String.format("%+.2f%%", v)
