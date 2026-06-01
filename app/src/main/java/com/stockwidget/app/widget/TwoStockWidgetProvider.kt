package com.stockwidget.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.stockwidget.app.MainActivity
import com.stockwidget.app.R
import com.stockwidget.app.data.PreferencesStore
import com.stockwidget.app.data.StockRepository
import com.stockwidget.app.data.model.StockQuote
import com.stockwidget.app.util.Money

/**
 * A 2x2 widget that shows two stocks chosen at placement time (via
 * [TwoStockConfigActivity]). Renders from cached data, so it draws instantly.
 */
class TwoStockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
        WidgetUpdater.refreshData(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == StockWidgetProvider.ACTION_REFRESH) {
            WidgetUpdater.refreshData(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = PreferencesStore(context)
        appWidgetIds.forEach { store.removeWidgetSymbols(it) }
    }

    private data class BlockIds(
        val root: Int,
        val symbol: Int,
        val price: Int,
        val currency: Int,
        val openClose: Int,
        val chart: Int
    )

    companion object {

        private val BLOCK_1 = BlockIds(
            R.id.two_block1, R.id.two_symbol1, R.id.two_price1,
            R.id.two_currency1, R.id.two_openclose1, R.id.two_chart1
        )
        private val BLOCK_2 = BlockIds(
            R.id.two_block2, R.id.two_symbol2, R.id.two_price2,
            R.id.two_currency2, R.id.two_openclose2, R.id.two_chart2
        )

        fun renderAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            ids(context, manager).forEach { render(context, manager, it) }
        }

        fun ids(context: Context, manager: AppWidgetManager): IntArray =
            manager.getAppWidgetIds(ComponentName(context, TwoStockWidgetProvider::class.java))

        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_two)
            val store = PreferencesStore(context)
            val symbols = store.getWidgetSymbols(widgetId)

            if (symbols.isEmpty()) {
                views.setViewVisibility(R.id.two_content, View.GONE)
                views.setViewVisibility(R.id.two_empty, View.VISIBLE)
                views.setOnClickPendingIntent(R.id.two_root, configIntent(context, widgetId))
                manager.updateAppWidget(widgetId, views)
                return
            }

            views.setViewVisibility(R.id.two_content, View.VISIBLE)
            views.setViewVisibility(R.id.two_empty, View.GONE)

            val repo = StockRepository(context)
            val quote1 = symbols.getOrNull(0)?.let { repo.cachedQuote(it) }
            val quote2 = symbols.getOrNull(1)?.let { repo.cachedQuote(it) }

            if (quote1 == null && quote2 == null) {
                // Both chosen stocks were removed from the list — prompt to reconfigure.
                views.setViewVisibility(R.id.two_content, View.GONE)
                views.setViewVisibility(R.id.two_empty, View.VISIBLE)
                views.setOnClickPendingIntent(R.id.two_root, configIntent(context, widgetId))
                manager.updateAppWidget(widgetId, views)
                return
            }

            renderBlock(context, views, BLOCK_1, quote1)
            renderBlock(context, views, BLOCK_2, quote2)

            manager.updateAppWidget(widgetId, views)
        }

        private fun renderBlock(
            context: Context,
            views: RemoteViews,
            ids: BlockIds,
            quote: StockQuote?
        ) {
            if (quote == null) {
                views.setViewVisibility(ids.root, View.GONE)
                return
            }
            views.setViewVisibility(ids.root, View.VISIBLE)
            views.setTextViewText(ids.symbol, quote.displaySymbol)

            val hasData = quote.hasData || quote.history.isNotEmpty()
            if (hasData) {
                views.setTextViewText(ids.price, Money.format(quote.current, quote.currency))
                if (quote.currency.isNotBlank()) {
                    views.setTextViewText(ids.currency, quote.currency.uppercase())
                    views.setViewVisibility(ids.currency, View.VISIBLE)
                } else {
                    views.setViewVisibility(ids.currency, View.GONE)
                }
                views.setTextViewText(
                    ids.openClose,
                    context.getString(
                        R.string.row_open_prevclose_short,
                        Money.format(quote.open, quote.currency),
                        Money.format(quote.previousClose, quote.currency)
                    )
                )
                views.setImageViewBitmap(ids.chart, ChartBitmap.render(quote, widthDp = 116, heightDp = 54))
                views.setViewVisibility(ids.chart, View.VISIBLE)
            } else {
                views.setTextViewText(ids.price, "—")
                views.setViewVisibility(ids.currency, View.GONE)
                views.setTextViewText(ids.openClose, quote.error ?: "")
                views.setViewVisibility(ids.chart, View.INVISIBLE)
            }

            // Tapping a block opens that stock's detail screen.
            views.setOnClickPendingIntent(ids.root, openDetailIntent(context, quote.symbol))
        }

        private fun configIntent(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, TwoStockConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return PendingIntent.getActivity(
                context, 1_000_000 + widgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openDetailIntent(context: Context, symbol: String): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_SYMBOL, symbol)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context, 2_000_000 + symbol.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
