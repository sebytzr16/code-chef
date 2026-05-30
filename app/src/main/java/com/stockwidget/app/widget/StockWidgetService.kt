package com.stockwidget.app.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.stockwidget.app.R
import com.stockwidget.app.data.StockRepository
import com.stockwidget.app.data.model.StockQuote

/** Provides the row views for the widget's ListView. */
class StockWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        StockRemoteViewsFactory(applicationContext)
}

private class StockRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var quotes: List<StockQuote> = emptyList()
    private val repository = StockRepository(context)

    override fun onCreate() {}

    override fun onDataSetChanged() {
        // Read from cache; the network refresh happens in WidgetUpdater before this is called.
        quotes = repository.cachedQuotes()
    }

    override fun onDestroy() {
        quotes = emptyList()
    }

    override fun getCount(): Int = quotes.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_row)
        if (position !in quotes.indices) return views
        val q = quotes[position]

        views.setTextViewText(R.id.row_symbol, q.displaySymbol)

        if (q.hasData || q.history.isNotEmpty()) {
            views.setTextViewText(R.id.row_price, formatMoney(q.current))
            views.setTextViewText(
                R.id.row_openclose,
                context.getString(
                    R.string.row_open_prevclose,
                    formatMoney(q.open),
                    formatMoney(q.previousClose)
                )
            )
            views.setImageViewBitmap(R.id.row_chart, ChartBitmap.render(q))
            views.setViewVisibility(R.id.row_chart, View.VISIBLE)
        } else {
            // No data yet (e.g. error or never fetched).
            views.setTextViewText(R.id.row_price, "—")
            views.setTextViewText(R.id.row_openclose, q.error ?: "")
            views.setViewVisibility(R.id.row_chart, View.INVISIBLE)
        }

        // Per-row tap fills in the template intent set by the provider.
        val fillIn = Intent().putExtra("symbol", q.symbol)
        views.setOnClickFillInIntent(R.id.row_root, fillIn)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true

    private fun formatMoney(value: Float): String = "$" + String.format("%,.2f", value)
}
