package com.stockwidget.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stockwidget.app.data.PreferencesStore
import com.stockwidget.app.data.model.Stock
import com.stockwidget.app.ui.theme.PriceUp
import com.stockwidget.app.ui.theme.StockWidgetTheme

/** Lets the user pick up to two stocks for a 2x2 widget. */
class TwoStockConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val stocks = PreferencesStore(this).getStocks()

        setContent {
            StockWidgetTheme {
                Surface(Modifier.fillMaxSize()) {
                    ConfigContent(stocks = stocks, onConfirm = ::confirm)
                }
            }
        }
    }

    private fun confirm(symbols: List<String>) {
        if (symbols.isEmpty()) return
        val store = PreferencesStore(this)
        store.setWidgetSymbols(widgetId, symbols)

        val manager = AppWidgetManager.getInstance(this)
        TwoStockWidgetProvider.render(this, manager, widgetId)
        WidgetUpdater.refreshData(this)

        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        )
        finish()
    }
}

@Composable
private fun ConfigContent(stocks: List<Stock>, onConfirm: (List<String>) -> Unit) {
    var selected by remember { mutableStateOf(listOf<String>()) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Choose two stocks", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (stocks.isEmpty()) {
                "You haven't added any stocks yet. Open the app, add some, then place the widget again."
            } else {
                "Pick up to 2 stocks (${selected.size}/2 selected)."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(stocks, key = { it.symbol }) { stock ->
                val isSelected = selected.contains(stock.symbol)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        selected = when {
                            isSelected -> selected - stock.symbol
                            selected.size >= 2 -> selected // already at the limit
                            else -> selected + stock.symbol
                        }
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stock.symbol.removePrefix("^").uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (stock.displayName.isNotBlank() && stock.displayName != stock.symbol) {
                                Text(
                                    stock.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            if (isSelected) Icons.Filled.CheckCircle
                            else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected" else "Not selected",
                            tint = if (isSelected) PriceUp else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Button(
            onClick = { onConfirm(selected) },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Text(if (selected.size == 2) "Add widget" else "Add (${selected.size}/2)")
        }
    }
}
