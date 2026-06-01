package com.stockwidget.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.stockwidget.app.R
import com.stockwidget.app.data.StockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Helpers for refreshing widget data and telling the launcher to redraw. */
object WidgetUpdater {

    /** Notify all widget instances that their data changed (forces re-render). */
    fun notifyDataChanged(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = widgetIds(context, manager)
        if (ids.isNotEmpty()) {
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }
        // Single- and two-stock widgets are static layouts; redraw them directly.
        SingleStockWidgetProvider.renderAll(context)
        TwoStockWidgetProvider.renderAll(context)
    }

    /** Fetch fresh quotes off the main thread, then redraw. */
    fun refreshData(context: Context, onDone: (() -> Unit)? = null) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { StockRepository(appContext).refreshAll() }
            notifyDataChanged(appContext)
            onDone?.invoke()
        }
    }

    fun widgetIds(context: Context, manager: AppWidgetManager): IntArray =
        manager.getAppWidgetIds(ComponentName(context, StockWidgetProvider::class.java))
}
