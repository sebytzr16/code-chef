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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stockwidget.app.data.PreferencesStore
import com.stockwidget.app.data.model.Stock
import com.stockwidget.app.ui.theme.StockWidgetTheme

/** Lets the user pick which tracked stock a single-stock widget should display. */
class SingleStockConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the user backs out, the widget should not be added.
        setResult(Activity.RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val store = PreferencesStore(this)
        val stocks = store.getStocks()

        setContent {
            StockWidgetTheme {
                Surface(Modifier.fillMaxSize()) {
                    ConfigContent(
                        stocks = stocks,
                        onPick = { confirm(it.symbol) }
                    )
                }
            }
        }
    }

    private fun confirm(symbol: String) {
        val store = PreferencesStore(this)
        store.setWidgetSymbol(widgetId, symbol)

        val manager = AppWidgetManager.getInstance(this)
        SingleStockWidgetProvider.render(this, manager, widgetId)
        // Make sure live data is fetched right away.
        WidgetUpdater.refreshData(this)

        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        )
        finish()
    }
}

@Composable
private fun ConfigContent(stocks: List<Stock>, onPick: (Stock) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Choose a stock", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (stocks.isEmpty()) {
                "You haven't added any stocks yet. Open the app, add a stock, then place the widget again."
            } else {
                "This widget will show the stock you pick."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(stocks, key = { it.symbol }) { stock ->
                Card(
                    Modifier.fillMaxWidth().clickable { onPick(stock) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stock.symbol.uppercase(),
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
                }
            }
        }
    }
}
