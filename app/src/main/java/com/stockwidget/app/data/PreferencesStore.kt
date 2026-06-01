package com.stockwidget.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.stockwidget.app.data.model.PricePoint
import com.stockwidget.app.data.model.QuoteSnapshot
import com.stockwidget.app.data.model.Stock
import com.stockwidget.app.data.model.ThemeMode

/**
 * Lightweight persistence over SharedPreferences. Stores the tracked symbols, per-symbol
 * snapshots + intraday price history, and each single-stock widget's chosen symbol
 * (JSON via Gson).
 */
class PreferencesStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /** How often the widget refreshes, in minutes. */
    var refreshMinutes: Int
        get() = prefs.getInt(KEY_REFRESH, DEFAULT_REFRESH_MIN)
        set(value) = prefs.edit().putInt(KEY_REFRESH, value).apply()

    /** Light/dark appearance preference. */
    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    /** Wall-clock time (millis) of the last refresh attempt — what the widget shows. */
    var lastRefreshAt: Long
        get() = prefs.getLong(KEY_LAST_REFRESH, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_REFRESH, value).apply()

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
            // Keep pinned stocks grouped at the top.
            saveStocks(current.sortedByDescending { it.pinned })
        }
    }

    /** Toggle a stock's pinned state; pinned stocks float to the top of the list. */
    fun togglePin(symbol: String) {
        val list = getStocks().map {
            if (it.symbol.equals(symbol, ignoreCase = true)) it.copy(pinned = !it.pinned) else it
        }
        saveStocks(list.sortedByDescending { it.pinned })
    }

    /**
     * Move a stock one position up or down. Reordering only happens within the same
     * pinned group, so pinned stocks always stay above unpinned ones.
     */
    fun moveStock(symbol: String, up: Boolean) {
        val list = getStocks().toMutableList()
        val i = list.indexOfFirst { it.symbol.equals(symbol, ignoreCase = true) }
        if (i < 0) return
        val j = if (up) i - 1 else i + 1
        if (j !in list.indices) return
        if (list[i].pinned != list[j].pinned) return
        list[i] = list[j].also { list[j] = list[i] }
        saveStocks(list)
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

    /** Replace a symbol's intraday series with the freshly fetched points (capped). */
    fun saveHistory(symbol: String, points: List<PricePoint>) {
        val map = getHistoryMap().toMutableMap()
        map[symbol.uppercase()] = points.takeLast(MAX_POINTS)
        saveHistoryMap(map)
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

    // ---- Multi-stock widgets (e.g. the 2-stock 2x2): symbols per widget ------

    private fun getWidgetSymbolsMap(): Map<String, List<String>> {
        val json = prefs.getString(KEY_WIDGET_SYMBOLS_MULTI, null) ?: return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            gson.fromJson<Map<String, List<String>>>(json, type) ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    private fun saveWidgetSymbolsMap(map: Map<String, List<String>>) {
        prefs.edit().putString(KEY_WIDGET_SYMBOLS_MULTI, gson.toJson(map)).apply()
    }

    fun getWidgetSymbols(widgetId: Int): List<String> =
        getWidgetSymbolsMap()[widgetId.toString()].orEmpty()

    fun setWidgetSymbols(widgetId: Int, symbols: List<String>) {
        val map = getWidgetSymbolsMap().toMutableMap()
        map[widgetId.toString()] = symbols.map { it.uppercase() }
        saveWidgetSymbolsMap(map)
    }

    fun removeWidgetSymbols(widgetId: Int) {
        val map = getWidgetSymbolsMap().toMutableMap()
        map.remove(widgetId.toString())
        saveWidgetSymbolsMap(map)
    }

    companion object {
        private const val PREFS_NAME = "stock_widget_prefs"
        private const val KEY_STOCKS = "stocks"
        private const val KEY_HISTORY = "history"
        private const val KEY_SNAPSHOTS = "snapshots"
        private const val KEY_WIDGET_SYMBOLS = "widget_symbols"
        private const val KEY_WIDGET_SYMBOLS_MULTI = "widget_symbols_multi"
        private const val KEY_REFRESH = "refresh_minutes"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LAST_REFRESH = "last_refresh_at"
        private const val DEFAULT_REFRESH_MIN = 30
        private const val MAX_POINTS = 120
    }
}
