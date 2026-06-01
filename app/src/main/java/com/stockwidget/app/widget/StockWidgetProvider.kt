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

/**
 * Home-screen widget: a title header plus a scrollable list of tracked stocks fed by
 * [StockWidgetService]. Tapping a row opens that stock's detail screen.
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

            // Template for row taps. MUTABLE so each row's fill-in intent (carrying the
            // symbol) is applied; opens the tapped stock's detail screen.
            val template = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val templatePending = PendingIntent.getActivity(
                context, 0, template,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, templatePending)

            // Title taps open the app (home).
            val openHome = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val homePending = PendingIntent.getActivity(
                context, 1, openHome,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, homePending)

            manager.updateAppWidget(widgetId, views)
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)
        }
    }
}
