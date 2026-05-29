package com.stockwidget.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockwidget.app.data.model.StockQuote
import com.stockwidget.app.ui.theme.PriceDown
import com.stockwidget.app.ui.theme.PriceUp

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
    var pendingDelete by remember { mutableStateOf<StockQuote?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stocks", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add stock")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
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
                    contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.quotes, key = { it.symbol }) { quote ->
                        SwipeableStockRow(
                            quote = quote,
                            onClick = { onOpenDetail(quote.symbol) },
                            onRequestDelete = { pendingDelete = quote }
                        )
                    }
                }
            }
            if (state.isLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }

    if (showAdd) {
        AddStockDialog(viewModel = viewModel, onDismiss = { showAdd = false })
    }
    if (showSettings) {
        SettingsDialog(viewModel = viewModel, onDismiss = { showSettings = false })
    }

    pendingDelete?.let { quote ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove ${quote.symbol.uppercase()}?") },
            text = { Text("It will be removed from your list and any widgets.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeStock(quote.symbol)
                    pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableStockRow(
    quote: StockQuote,
    onClick: () -> Unit,
    onRequestDelete: () -> Unit
) {
    // Swiping asks for confirmation rather than deleting outright. We always return
    // false so the row springs back; the dialog handles the actual removal.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRequestDelete()
            }
            false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(PriceDown.copy(alpha = 0.12f))
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = PriceDown)
            }
        }
    ) {
        StockCard(quote = quote, onClick = onClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockCard(quote: StockQuote, onClick: () -> Unit) {
    val up = quote.isUp
    val accent = if (up) PriceUp else PriceDown
    val hasData = quote.hasData || quote.history.isNotEmpty()

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Symbol + name
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
            }

            // Price + percent change
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 14.dp)) {
                Text(
                    if (hasData) money(quote.current) else "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (hasData) {
                    val arrow = if (up) "▲" else "▼"
                    Text(
                        "$arrow ${percent(quote.changePercent)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Chart, on the right of the price
            Sparkline(
                quote = quote,
                modifier = Modifier.width(72.dp).height(40.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(hasKey: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (hasKey) "No stocks yet" else "Welcome",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (hasKey) {
                "Tap + to add a stock, then drop the widget on your home screen."
            } else {
                "Open Settings to add your free Finnhub API key, then start adding stocks."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun money(v: Float): String = "$" + String.format("%,.2f", v)
private fun percent(v: Float): String = String.format("%+.2f%%", v)
