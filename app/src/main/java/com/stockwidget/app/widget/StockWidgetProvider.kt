package com.stockwidget.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.stockwidget.app.MainActivity
import com.stockwidget.app.R
import com.stockwidget.app.data.PreferencesStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home-screen widget: a header (title, last-updated, refresh) plus a scrollable list of
 * tracked stocks fed by [StockWidgetService].
 */
class StockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> buildWidget(context, appWidgetManager, id) }
        // Pull fresh data whenever the system asks us to update.
        WidgetUpdater.refreshData(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            // Show a spinner-ish "Updating…" state, then fetch + redraw.
            val manager = AppWidgetManager.getInstance(context)
            val ids = WidgetUpdater.widgetIds(context, manager)
            ids.forEach { setUpdating(context, manager, it) }
            WidgetUpdater.refreshData(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.stockwidget.app.ACTION_REFRESH"

        private fun buildWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_stock)
            val store = PreferencesStore(context)

            // List adapter backed by the collection service.
            val serviceIntent = Intent(context, StockWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                // Make the intent unique per widget so the adapter isn't shared/cached wrongly.
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // Empty-state text.
            val emptyText = if (store.getStocks().isEmpty()) {
                context.getString(R.string.widget_no_stocks)
            } else {
                context.getString(R.string.widget_loading)
            }
            views.setTextViewText(R.id.widget_empty, emptyText)

            // Header timestamp.
            views.setTextViewText(R.id.widget_updated, lastUpdatedText(context))

            // Tapping a row opens the app (template + per-item fill-in intent).
            val openApp = Intent(context, MainActivity::class.java)
            val openPending = PendingIntent.getActivity(
                context, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, openPending)

            // Title taps also open the app.
            views.setOnClickPendingIntent(R.id.widget_title, openPending)

            manager.updateAppWidget(widgetId, views)
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)
        }

        /** Re-render only the header/chrome (used after a data refresh completes). */
        fun refreshChrome(context: Context, manager: AppWidgetManager, ids: IntArray) {
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_stock)
                views.setTextViewText(R.id.widget_updated, lastUpdatedText(context))
                manager.partiallyUpdateAppWidget(id, views)
            }
        }

        private fun setUpdating(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_stock)
            views.setTextViewText(R.id.widget_updated, context.getString(R.string.widget_updating))
            manager.partiallyUpdateAppWidget(id, views)
        }

        private fun lastUpdatedText(context: Context): String {
            // Wall-clock time of the last refresh, so it changes on every update
            // (the market-data timestamp can stay fixed when the market is closed).
            val last = PreferencesStore(context).lastRefreshAt
            return if (last <= 0L) {
                context.getString(R.string.widget_subtitle)
            } else {
                val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(last))
                context.getString(R.string.widget_updated_at, time)
            }
        }
    }
}
