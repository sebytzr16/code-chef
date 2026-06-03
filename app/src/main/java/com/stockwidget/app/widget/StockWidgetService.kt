package com.stockwidget.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.stockwidget.app.MainActivity
import com.stockwidget.app.R
import com.stockwidget.app.data.StockRepository
import com.stockwidget.app.data.model.StockQuote
import com.stockwidget.app.util.Money

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
            val arrow = if (q.isUp) "▲" else "▼"
            views.setTextViewText(R.id.row_change, "$arrow ${Money.percentChange(q.changePercent)}")
            views.setTextColor(R.id.row_change, if (q.isUp) ChartBitmap.GREEN else ChartBitmap.RED)
            views.setViewVisibility(R.id.row_change, View.VISIBLE)

            views.setTextViewText(R.id.row_price, Money.format(q.current, q.currency))
            if (q.currency.isNotBlank()) {
                views.setTextViewText(R.id.row_currency, q.currency.uppercase())
                views.setViewVisibility(R.id.row_currency, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.row_currency, View.GONE)
            }
            views.setTextViewText(
                R.id.row_openclose,
                context.getString(
                    R.string.row_open_prevclose,
                    Money.format(q.open, q.currency),
                    Money.format(q.previousClose, q.currency)
                )
            )
            views.setImageViewBitmap(R.id.row_chart, ChartBitmap.render(q))
            views.setViewVisibility(R.id.row_chart, View.VISIBLE)
        } else {
            // No data yet (e.g. error or never fetched).
            views.setViewVisibility(R.id.row_change, View.GONE)
            views.setTextViewText(R.id.row_price, "—")
            views.setViewVisibility(R.id.row_currency, View.GONE)
            views.setTextViewText(R.id.row_openclose, q.error ?: "")
            views.setViewVisibility(R.id.row_chart, View.INVISIBLE)
        }

        // Per-row tap fills in the template intent so it opens this stock's detail.
        val fillIn = Intent().apply {
            putExtra(MainActivity.EXTRA_OPEN_SYMBOL, q.symbol)
            // Unique data so each row's fill-in is treated as distinct.
            data = Uri.parse("stockwidget://open/${q.symbol}")
        }
        views.setOnClickFillInIntent(R.id.row_root, fillIn)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
