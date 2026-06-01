package com.stockwidget.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stockwidget.app.data.model.PricePoint
import com.stockwidget.app.data.model.StockQuote
import com.stockwidget.app.ui.theme.PriceDown
import com.stockwidget.app.ui.theme.PriceUp
import com.stockwidget.app.util.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    viewModel: StockViewModel,
    symbol: String,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    // Cached snapshot is read once (no network) so the screen is populated immediately.
    val cached = remember(symbol) { viewModel.cachedQuote(symbol) }
    // Prefer the live list (updates on refresh), then the cached snapshot.
    val quote: StockQuote = state.quotes.firstOrNull { it.symbol.equals(symbol, true) }
        ?: cached
        ?: StockQuote(symbol = symbol, displayName = symbol)

    val hasData = quote.hasData || quote.history.isNotEmpty()

    // The point currently being scrubbed on the chart (null when not touching it).
    var scrubbed by remember(symbol) { mutableStateOf<PricePoint?>(null) }
    val shownPrice = scrubbed?.price ?: quote.current
    val shownChange = shownPrice - quote.open
    val shownPct = if (quote.open != 0f) shownChange / quote.open * 100f else 0f
    val up = shownPrice >= quote.open
    val accent = if (up) PriceUp else PriceDown
    val dayAccent = if (quote.isUp) PriceUp else PriceDown

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(quote.displaySymbol) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                quote.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (hasData) Money.format(shownPrice, quote.currency) else "—",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (hasData && quote.currency.isNotBlank()) {
                    Text(
                        quote.currency.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }
            }
            if (hasData) {
                val arrow = if (up) "▲" else "▼"
                val suffix = if (scrubbed == null) "  today" else ""
                Text(
                    "$arrow ${Money.format(abs(shownChange), quote.currency)}  (${percent(shownPct)})$suffix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    quote.error ?: "No data yet — pull a refresh from the list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(20.dp))

            // Large interactive chart — drag across it to inspect prices through the day.
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                InteractiveChart(
                    quote = quote,
                    onScrub = { scrubbed = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(16.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            // Detached time-of-day label that tracks the scrubbed point.
            Text(
                text = when {
                    !hasData -> ""
                    scrubbed != null -> timeOfDay(scrubbed!!.timestamp)
                    else -> "Swipe the chart to see prices through the day"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (scrubbed != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (scrubbed != null) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            // Stats — all from cached data, no network.
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(Modifier.padding(4.dp)) {
                    StatRow("Open", Money.format(quote.open, quote.currency))
                    StatRow("Previous close", Money.format(quote.previousClose, quote.currency))
                    StatRow("Day high", Money.format(quote.high, quote.currency))
                    StatRow("Day low", Money.format(quote.low, quote.currency))
                    StatRow(
                        "Change",
                        "${Money.format(quote.change, quote.currency)} (${percent(quote.changePercent)})",
                        dayAccent
                    )
                    if (quote.exchange.isNotBlank()) StatRow("Exchange", quote.exchange)
                    if (quote.currency.isNotBlank()) StatRow("Currency", quote.currency.uppercase())
                    if (quote.updatedAt > 0L) {
                        StatRow("Last updated", timeText(quote.updatedAt))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color? = null) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

private fun percent(v: Float): String = String.format("%+.2f%%", v)
private fun timeText(ts: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ts))
private fun timeOfDay(ts: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ts))
