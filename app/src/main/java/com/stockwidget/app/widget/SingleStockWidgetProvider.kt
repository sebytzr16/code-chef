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
import com.stockwidget.app.util.Money

/**
 * A compact widget that shows a single stock chosen at placement time (via
 * [SingleStockConfigActivity]). Renders from cached data, so it draws instantly.
 */
class SingleStockWidgetProvider : AppWidgetProvider() {

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
        appWidgetIds.forEach { store.removeWidgetSymbol(it) }
    }

    companion object {

        fun renderAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            ids(context, manager).forEach { render(context, manager, it) }
        }

        fun ids(context: Context, manager: AppWidgetManager): IntArray =
            manager.getAppWidgetIds(
                ComponentName(context, SingleStockWidgetProvider::class.java)
            )

        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_single)
            val store = PreferencesStore(context)
            val symbol = store.getWidgetSymbol(widgetId)

            val repo = StockRepository(context)
            val quote = symbol?.let { repo.cachedQuote(it) }

            if (symbol == null || quote == null) {
                // Not configured (or its stock was removed): prompt to configure.
                views.setViewVisibility(R.id.single_content, View.GONE)
                views.setViewVisibility(R.id.single_empty, View.VISIBLE)
                views.setOnClickPendingIntent(R.id.single_root, configIntent(context, widgetId))
                manager.updateAppWidget(widgetId, views)
                return
            }

            views.setViewVisibility(R.id.single_content, View.VISIBLE)
            views.setViewVisibility(R.id.single_empty, View.GONE)

            val hasData = quote.hasData || quote.history.isNotEmpty()

            views.setTextViewText(R.id.single_symbol, quote.displaySymbol)

            if (hasData) {
                val arrow = if (quote.isUp) "▲" else "▼"
                views.setTextViewText(
                    R.id.single_change,
                    "$arrow ${Money.percentChange(quote.changePercent)}"
                )
                views.setTextColor(
                    R.id.single_change,
                    if (quote.isUp) ChartBitmap.GREEN else ChartBitmap.RED
                )
                views.setViewVisibility(R.id.single_change, View.VISIBLE)

                views.setTextViewText(R.id.single_price, Money.format(quote.current, quote.currency))
                if (quote.currency.isNotBlank()) {
                    views.setTextViewText(R.id.single_currency, quote.currency.uppercase())
                    views.setViewVisibility(R.id.single_currency, View.VISIBLE)
                } else {
                    views.setViewVisibility(R.id.single_currency, View.GONE)
                }
                views.setTextViewText(
                    R.id.single_openclose,
                    context.getString(
                        R.string.row_open_prevclose_short,
                        Money.format(quote.open, quote.currency),
                        Money.format(quote.previousClose, quote.currency)
                    )
                )
                views.setImageViewBitmap(
                    R.id.single_chart,
                    ChartBitmap.render(quote, widthDp = 84, heightDp = 46)
                )
                views.setViewVisibility(R.id.single_chart, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.single_change, View.GONE)
                views.setTextViewText(R.id.single_price, "—")
                views.setViewVisibility(R.id.single_currency, View.GONE)
                views.setTextViewText(R.id.single_openclose, quote.error ?: "")
                views.setViewVisibility(R.id.single_chart, View.INVISIBLE)
            }

            // Tapping opens the app straight to this stock's detail screen.
            views.setOnClickPendingIntent(R.id.single_root, openDetailIntent(context, quote.symbol))

            manager.updateAppWidget(widgetId, views)
        }

        private fun configIntent(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, SingleStockConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return PendingIntent.getActivity(
                context, widgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openDetailIntent(context: Context, symbol: String): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_SYMBOL, symbol)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context, symbol.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

    }
}
