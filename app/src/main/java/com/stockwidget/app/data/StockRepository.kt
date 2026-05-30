package com.stockwidget.app.data

import android.content.Context
import com.stockwidget.app.data.model.PricePoint
import com.stockwidget.app.data.model.QuoteSnapshot
import com.stockwidget.app.data.model.SearchResult
import com.stockwidget.app.data.model.Stock
import com.stockwidget.app.data.model.StockQuote
import com.stockwidget.app.data.remote.YahooApi
import com.stockwidget.app.data.remote.YahooClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordinates keyless Yahoo Finance quotes with the locally persisted symbols, snapshots
 * and intraday history. No API key required.
 */
class StockRepository(
    context: Context,
    private val api: YahooApi = YahooClient.api
) {
    private val store = PreferencesStore(context)

    val preferences: PreferencesStore get() = store

    /**
     * Fetches a fresh intraday chart for each tracked symbol, stores a snapshot + the
     * series, and returns the view models. One bad ticker won't blank the others.
     */
    suspend fun refreshAll(): List<StockQuote> = withContext(Dispatchers.IO) {
        store.getStocks().map { stock ->
            try {
                val response = api.getChart(stock.symbol, "1d", "5m")
                val result = response.chart.result?.firstOrNull()
                val price = result?.meta?.regularMarketPrice
                if (result == null || price == null) {
                    StockQuote(
                        symbol = stock.symbol,
                        displayName = stock.displayName,
                        error = response.chart.error?.description ?: "No data",
                        history = store.getHistory(stock.symbol)
                    )
                } else {
                    val meta = result.meta
                    val series = result.indicators.quote?.firstOrNull()
                    val points = buildPoints(result.timestamp, series?.close)

                    val open = series?.open?.firstOrNull { it != null }
                        ?: points.firstOrNull()?.price ?: price
                    val previousClose = meta.chartPreviousClose ?: meta.previousClose ?: 0f
                    val high = meta.regularMarketDayHigh
                        ?: series?.high?.filterNotNull()?.maxOrNull() ?: 0f
                    val low = meta.regularMarketDayLow
                        ?: series?.low?.filterNotNull()?.minOrNull() ?: 0f
                    val updatedAt = meta.regularMarketTime?.let { it * 1000 }
                        ?: System.currentTimeMillis()

                    store.saveHistory(stock.symbol, points)
                    store.saveSnapshot(
                        stock.symbol,
                        QuoteSnapshot(price, open, previousClose, high, low, updatedAt)
                    )

                    StockQuote(
                        symbol = stock.symbol,
                        displayName = stock.displayName,
                        current = price,
                        open = open,
                        previousClose = previousClose,
                        high = high,
                        low = low,
                        updatedAt = updatedAt,
                        history = points
                    )
                }
            } catch (e: Exception) {
                StockQuote(
                    symbol = stock.symbol,
                    displayName = stock.displayName,
                    error = e.message ?: "Network error",
                    history = store.getHistory(stock.symbol)
                )
            }
        }
    }

    private fun buildPoints(timestamps: List<Long>?, closes: List<Float?>?): List<PricePoint> {
        if (timestamps == null || closes == null) return emptyList()
        return timestamps.indices.mapNotNull { i ->
            val price = closes.getOrNull(i)
            if (price != null && price > 0f) PricePoint(timestamps[i] * 1000, price) else null
        }
    }

    /** Builds quotes from the cached snapshot + history (no network). Renders instantly. */
    fun cachedQuotes(): List<StockQuote> = store.getStocks().map { buildCached(it) }

    /** Cached quote for a single symbol, or null if it isn't tracked. */
    fun cachedQuote(symbol: String): StockQuote? =
        store.getStocks().firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
            ?.let { buildCached(it) }

    private fun buildCached(stock: Stock): StockQuote {
        val history = store.getHistory(stock.symbol)
        val snap = store.getSnapshot(stock.symbol)
        return StockQuote(
            symbol = stock.symbol,
            displayName = stock.displayName,
            current = snap?.current ?: history.lastOrNull()?.price ?: 0f,
            open = snap?.open ?: history.firstOrNull()?.price ?: 0f,
            previousClose = snap?.previousClose ?: 0f,
            high = snap?.high ?: 0f,
            low = snap?.low ?: 0f,
            updatedAt = snap?.updatedAt ?: history.lastOrNull()?.timestamp ?: 0L,
            history = history
        )
    }

    suspend fun searchSymbols(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            api.search(query).quotes.orEmpty().mapNotNull { q ->
                val symbol = q.symbol
                val type = q.quoteType
                if (symbol.isNullOrBlank() || type == null || type !in TRADEABLE_TYPES) {
                    null
                } else {
                    SearchResult(
                        symbol = symbol.uppercase(),
                        name = q.longname ?: q.shortname ?: symbol,
                        exchange = q.exchDisp.orEmpty()
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private val TRADEABLE_TYPES =
            setOf("EQUITY", "ETF", "INDEX", "MUTUALFUND", "CURRENCY", "CRYPTOCURRENCY", "FUTURE")
    }
}
