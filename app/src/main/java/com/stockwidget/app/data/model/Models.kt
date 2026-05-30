package com.stockwidget.app.data.model

/**
 * A stock the user is tracking, as configured in the app.
 */
data class Stock(
    val symbol: String,
    val displayName: String = symbol,
    val pinned: Boolean = false
)

/**
 * A single intraday price point (epoch millis + price) from the Yahoo chart series,
 * used to draw the sparkline.
 */
data class PricePoint(
    val timestamp: Long,
    val price: Float
)

/**
 * The latest quote for a symbol plus the rolling intraday history used for the chart.
 * This is the unit the widget renders.
 */
data class StockQuote(
    val symbol: String,
    val displayName: String,
    val current: Float = 0f,
    val open: Float = 0f,
    val previousClose: Float = 0f,
    val high: Float = 0f,
    val low: Float = 0f,
    val updatedAt: Long = 0L,
    val history: List<PricePoint> = emptyList(),
    val pinned: Boolean = false,
    val currency: String = "",
    val exchange: String = "",
    val error: String? = null
) {
    /** Change vs. the day's opening price. */
    val change: Float get() = current - open
    val changePercent: Float get() = if (open != 0f) (change / open) * 100f else 0f

    /** Up days render green, down days render red. Based on current vs. open. */
    val isUp: Boolean get() = current >= open

    val hasData: Boolean get() = updatedAt > 0L && error == null

    /** Symbol for display: index tickers like "^GSPC" show without the caret. */
    val displaySymbol: String get() = symbol.removePrefix("^").uppercase()
}

/**
 * Everything the app persists: the tracked symbols and the captured price history,
 * keyed by symbol.
 */
data class StockData(
    val stocks: List<Stock> = emptyList(),
    val history: Map<String, List<PricePoint>> = emptyMap()
)

/**
 * A persisted snapshot of the last successful quote for a symbol. Saved on every refresh
 * so the app and widgets can render full data instantly, offline, without re-fetching.
 */
data class QuoteSnapshot(
    val current: Float = 0f,
    val open: Float = 0f,
    val previousClose: Float = 0f,
    val high: Float = 0f,
    val low: Float = 0f,
    val updatedAt: Long = 0L,
    val currency: String = "",
    val exchange: String = ""
)

/** A symbol-search result shown when the user is adding a stock. */
data class SearchResult(
    val symbol: String,
    val name: String,
    val exchange: String = ""
)

/** User's chosen appearance: follow the system, or force light/dark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }
