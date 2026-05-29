package com.stockwidget.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.stockwidget.app.data.model.PricePoint
import com.stockwidget.app.data.model.QuoteSnapshot
import com.stockwidget.app.data.model.Stock

/**
 * Lightweight persistence over SharedPreferences. Stores the API key, the tracked
 * symbols, and the per-symbol intraday price history (JSON via Gson).
 */
class PreferencesStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    var apiKey: String
        get() = prefs.getString(KEY_API, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    /** How often the widget refreshes, in minutes. */
    var refreshMinutes: Int
        get() = prefs.getInt(KEY_REFRESH, DEFAULT_REFRESH_MIN)
        set(value) = prefs.edit().putInt(KEY_REFRESH, value).apply()

    fun getStocks(): List<Stock> {
        val json = prefs.getString(KEY_STOCKS, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<Stock>>() {}.type
            gson.fromJson<List<Stock>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun saveStocks(stocks: List<Stock>) {
        prefs.edit().putString(KEY_STOCKS, gson.toJson(stocks)).apply()
    }

    fun addStock(stock: Stock) {
        val current = getStocks().toMutableList()
        if (current.none { it.symbol.equals(stock.symbol, ignoreCase = true) }) {
            current.add(stock)
            saveStocks(current)
        }
    }

    fun removeStock(symbol: String) {
        saveStocks(getStocks().filterNot { it.symbol.equals(symbol, ignoreCase = true) })
        // Drop history and snapshot for the removed symbol too.
        val map = getHistoryMap().toMutableMap()
        map.remove(symbol.uppercase())
        saveHistoryMap(map)
        val snaps = getSnapshotMap().toMutableMap()
        snaps.remove(symbol.uppercase())
        saveSnapshotMap(snaps)
    }

    fun moveStock(from: Int, to: Int) {
        val list = getStocks().toMutableList()
        if (from in list.indices && to in list.indices) {
            list.add(to, list.removeAt(from))
            saveStocks(list)
        }
    }

    // ---- Price history -----------------------------------------------------

    private fun getHistoryMap(): Map<String, List<PricePoint>> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, List<PricePoint>>>() {}.type
            gson.fromJson<Map<String, List<PricePoint>>>(json, type) ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    private fun saveHistoryMap(map: Map<String, List<PricePoint>>) {
        prefs.edit().putString(KEY_HISTORY, gson.toJson(map)).apply()
    }

    fun getHistory(symbol: String): List<PricePoint> =
        getHistoryMap()[symbol.uppercase()].orEmpty()

    /**
     * Append a sample to a symbol's intraday history. History from previous days is
     * discarded so the sparkline only reflects the current session, and the list is
     * capped at [MAX_POINTS].
     */
    fun appendPricePoint(symbol: String, point: PricePoint) {
        val key = symbol.uppercase()
        val map = getHistoryMap().toMutableMap()
        val existing = map[key].orEmpty().filter { isSameDay(it.timestamp, point.timestamp) }
        val updated = (existing + point).takeLast(MAX_POINTS)
        map[key] = updated
        saveHistoryMap(map)
    }

    private fun isSameDay(a: Long, b: Long): Boolean {
        val dayMs = 24L * 60 * 60 * 1000
        // Compare by local-day buckets (rough; good enough for a sparkline reset).
        return (a + LOCAL_OFFSET_MS) / dayMs == (b + LOCAL_OFFSET_MS) / dayMs
    }

    // ---- Quote snapshots (full last-known data, for instant offline render) -

    private fun getSnapshotMap(): Map<String, QuoteSnapshot> {
        val json = prefs.getString(KEY_SNAPSHOTS, null) ?: return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, QuoteSnapshot>>() {}.type
            gson.fromJson<Map<String, QuoteSnapshot>>(json, type) ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    private fun saveSnapshotMap(map: Map<String, QuoteSnapshot>) {
        prefs.edit().putString(KEY_SNAPSHOTS, gson.toJson(map)).apply()
    }

    fun getSnapshot(symbol: String): QuoteSnapshot? = getSnapshotMap()[symbol.uppercase()]

    fun saveSnapshot(symbol: String, snapshot: QuoteSnapshot) {
        val map = getSnapshotMap().toMutableMap()
        map[symbol.uppercase()] = snapshot
        saveSnapshotMap(map)
    }

    // ---- Single-stock widget: which symbol each widget instance shows -------

    private fun getWidgetSymbolMap(): Map<String, String> {
        val json = prefs.getString(KEY_WIDGET_SYMBOLS, null) ?: return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(json, type) ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    private fun saveWidgetSymbolMap(map: Map<String, String>) {
        prefs.edit().putString(KEY_WIDGET_SYMBOLS, gson.toJson(map)).apply()
    }

    fun getWidgetSymbol(widgetId: Int): String? = getWidgetSymbolMap()[widgetId.toString()]

    fun setWidgetSymbol(widgetId: Int, symbol: String) {
        val map = getWidgetSymbolMap().toMutableMap()
        map[widgetId.toString()] = symbol.uppercase()
        saveWidgetSymbolMap(map)
    }

    fun removeWidgetSymbol(widgetId: Int) {
        val map = getWidgetSymbolMap().toMutableMap()
        map.remove(widgetId.toString())
        saveWidgetSymbolMap(map)
    }

    companion object {
        private const val PREFS_NAME = "stock_widget_prefs"
        private const val KEY_API = "api_key"
        private const val KEY_STOCKS = "stocks"
        private const val KEY_HISTORY = "history"
        private const val KEY_SNAPSHOTS = "snapshots"
        private const val KEY_WIDGET_SYMBOLS = "widget_symbols"
        private const val KEY_REFRESH = "refresh_minutes"
        private const val DEFAULT_REFRESH_MIN = 30
        private const val MAX_POINTS = 120
        private val LOCAL_OFFSET_MS = java.util.TimeZone.getDefault().rawOffset.toLong()
    }
}
