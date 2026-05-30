package com.stockwidget.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockwidget.app.data.model.SearchResult
import com.stockwidget.app.data.model.StockQuote
import com.stockwidget.app.data.model.ThemeMode
import com.stockwidget.app.ui.theme.PriceDown
import com.stockwidget.app.ui.theme.PriceUp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockListScreen(
    viewModel: StockViewModel,
    onOpenDetail: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<StockQuote?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }

    // Editing/searching can't coexist meaningfully; searching exits edit mode.
    val inSearch = query.isNotBlank()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Debounced symbol search.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isBlank()) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(300)
        results = viewModel.search(q)
        searching = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stocks", fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (editMode) {
                        TextButton(onClick = { editMode = false }) { Text("Done") }
                    } else {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Refresh now") },
                                onClick = { menuOpen = false; viewModel.refresh() }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit stocks") },
                                onClick = { menuOpen = false; editMode = true }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Update every 30 min") },
                                trailingIcon = {
                                    if (state.refreshMinutes == 30) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                                onClick = { menuOpen = false; viewModel.setRefreshMinutes(30) }
                            )
                            DropdownMenuItem(
                                text = { Text("Update every hour") },
                                trailingIcon = {
                                    if (state.refreshMinutes == 60) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                },
                                onClick = { menuOpen = false; viewModel.setRefreshMinutes(60) }
                            )
                            HorizontalDivider()
                            ThemeMenuItem("System theme", ThemeMode.SYSTEM, state.themeMode) {
                                menuOpen = false; viewModel.setThemeMode(it)
                            }
                            ThemeMenuItem("Light theme", ThemeMode.LIGHT, state.themeMode) {
                                menuOpen = false; viewModel.setThemeMode(it)
                            }
                            ThemeMenuItem("Dark theme", ThemeMode.DARK, state.themeMode) {
                                menuOpen = false; viewModel.setThemeMode(it)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search a stock to add") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (inSearch) {
                    SearchResults(
                        results = results,
                        searching = searching,
                        isTracked = { viewModel.isTracked(it) },
                        onAdd = { r ->
                            viewModel.addStock(r.symbol, r.name)
                            query = ""
                        }
                    )
                } else if (state.quotes.isEmpty()) {
                    EmptyState(Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.quotes, key = { it.symbol }) { quote ->
                            StockCard(
                                quote = quote,
                                editMode = editMode,
                                onClick = { onOpenDetail(quote.symbol) },
                                onDelete = { pendingDelete = quote }
                            )
                        }
                    }
                }

                if (state.isLoading && !inSearch) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
        }
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

@Composable
private fun ThemeMenuItem(
    label: String,
    mode: ThemeMode,
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = {
            if (current == mode) Icon(Icons.Filled.Check, contentDescription = null)
        },
        onClick = { onSelect(mode) }
    )
}

@Composable
private fun SearchResults(
    results: List<SearchResult>,
    searching: Boolean,
    isTracked: (String) -> Boolean,
    onAdd: (SearchResult) -> Unit
) {
    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (searching) "Searching…" else "No matches",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(results, key = { it.symbol }) { r ->
            val tracked = isTracked(r.symbol)
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = !tracked) { onAdd(r) },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            r.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (r.exchange.isNotBlank()) "${r.name} · ${r.exchange}" else r.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        if (tracked) Icons.Filled.Check else Icons.Filled.Add,
                        contentDescription = if (tracked) "Added" else "Add",
                        tint = if (tracked) PriceUp else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockCard(
    quote: StockQuote,
    editMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val up = quote.isUp
    val accent = if (up) PriceUp else PriceDown
    val hasData = quote.hasData || quote.history.isNotEmpty()
    // Card tints green when up, light red when down.
    val container = if (up) PriceUp.copy(alpha = 0.15f) else PriceDown.copy(alpha = 0.15f)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            Sparkline(
                quote = quote,
                modifier = Modifier.width(72.dp).height(40.dp)
            )

            if (editMode) {
                IconButton(onClick = onDelete, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = PriceDown)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Add your stocks",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Search above to add the stocks you want to see, then drop the widget on your home screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun money(v: Float): String = "$" + String.format("%,.2f", v)
private fun percent(v: Float): String = String.format("%+.2f%%", v)
