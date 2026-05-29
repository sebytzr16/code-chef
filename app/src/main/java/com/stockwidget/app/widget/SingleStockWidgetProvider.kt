package com.stockwidget.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.stockwidget.app.MainActivity
import com.stockwidget.app.R
import com.stockwidget.app.data.PreferencesStore
import com.stockwidget.app.data.StockRepository
import kotlin.math.abs

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

            // Refresh button (shared action) and tap-to-open behaviour.
            views.setOnClickPendingIntent(R.id.single_refresh, refreshIntent(context))

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

            val up = quote.isUp
            val accent = if (up) ChartBitmap.GREEN else ChartBitmap.RED
            val hasData = quote.hasData || quote.history.isNotEmpty()

            views.setTextViewText(R.id.single_symbol, quote.symbol.uppercase())
            views.setTextViewText(R.id.single_name, quote.displayName)

            if (hasData) {
                views.setTextViewText(R.id.single_price, money(quote.current))
                val arrow = if (up) "▲" else "▼"
                views.setTextViewText(
                    R.id.single_change,
                    "$arrow ${money(abs(quote.change))}  ${percent(quote.changePercent)}"
                )
                views.setTextColor(R.id.single_change, accent)
                views.setTextViewText(
                    R.id.single_openclose,
                    context.getString(
                        R.string.row_open_prevclose,
                        money(quote.open),
                        money(quote.previousClose)
                    )
                )
                views.setImageViewBitmap(
                    R.id.single_chart,
                    ChartBitmap.render(quote, widthDp = 110, heightDp = 64)
                )
                views.setViewVisibility(R.id.single_chart, View.VISIBLE)
            } else {
                views.setTextViewText(R.id.single_price, "—")
                views.setTextViewText(R.id.single_change, quote.error ?: "Loading…")
                views.setTextColor(R.id.single_change, Color.GRAY)
                views.setTextViewText(R.id.single_openclose, "")
                views.setViewVisibility(R.id.single_chart, View.INVISIBLE)
            }

            // Tapping opens the app straight to this stock's detail screen.
            views.setOnClickPendingIntent(R.id.single_root, openDetailIntent(context, quote.symbol))

            manager.updateAppWidget(widgetId, views)
        }

        private fun refreshIntent(context: Context): PendingIntent {
            val intent = Intent(context, SingleStockWidgetProvider::class.java).apply {
                action = StockWidgetProvider.ACTION_REFRESH
            }
            return PendingIntent.getBroadcast(
                context, 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
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

        private fun money(v: Float): String = "$" + String.format("%,.2f", v)
        private fun percent(v: Float): String = String.format("%+.2f%%", v)
    }
}
