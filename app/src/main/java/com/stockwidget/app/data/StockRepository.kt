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
                        history = store.getHistory(stock.symbol),
                        pinned = stock.pinned
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
                        history = points,
                        pinned = stock.pinned
                    )
                }
            } catch (e: Exception) {
                StockQuote(
                    symbol = stock.symbol,
                    displayName = stock.displayName,
                    error = e.message ?: "Network error",
                    history = store.getHistory(stock.symbol),
                    pinned = stock.pinned
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
            history = history,
            pinned = stock.pinned
        )
    }

    suspend fun searchSymbols(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()

        // Curated popular indices first (so e.g. "S&P 500" reliably surfaces ^GSPC),
        // then live Yahoo results (equities, ETFs, index funds, …), de-duplicated.
        val curated = curatedIndexMatches(q)
        val remote = runCatching {
            api.search(q).quotes.orEmpty().mapNotNull { item ->
                val symbol = item.symbol
                val type = item.quoteType
                if (symbol.isNullOrBlank() || type == null || type !in TRADEABLE_TYPES) {
                    null
                } else {
                    SearchResult(
                        symbol = symbol.uppercase(),
                        name = item.longname ?: item.shortname ?: symbol,
                        exchange = item.exchDisp.orEmpty()
                    )
                }
            }
        }.getOrDefault(emptyList())

        (curated + remote).distinctBy { it.symbol }
    }

    private fun curatedIndexMatches(query: String): List<SearchResult> {
        if (query.length < 2) return emptyList()
        val q = query.lowercase()
        // Match when the query is a prefix of the name/ticker/a keyword, so company
        // searches like "spotify" don't accidentally surface an index.
        return POPULAR_INDICES.filter { idx ->
            idx.name.lowercase().contains(q) ||
                idx.symbol.removePrefix("^").lowercase().startsWith(q) ||
                idx.keywords.any { it.startsWith(q) }
        }.map { SearchResult(symbol = it.symbol, name = it.name, exchange = "Index") }
    }

    private data class IndexInfo(val symbol: String, val name: String, val keywords: List<String>)

    companion object {
        private val TRADEABLE_TYPES =
            setOf("EQUITY", "ETF", "INDEX", "MUTUALFUND", "CURRENCY", "CRYPTOCURRENCY", "FUTURE")

        private val POPULAR_INDICES = listOf(
            IndexInfo("^GSPC", "S&P 500", listOf("s&p", "sp", "sp500", "s&p 500", "spx", "500")),
            IndexInfo("^IXIC", "NASDAQ Composite", listOf("nasdaq", "ixic", "composite")),
            IndexInfo("^DJI", "Dow Jones Industrial Average", listOf("dow", "dji", "jones", "industrial")),
            IndexInfo("^RUT", "Russell 2000", listOf("russell", "rut", "2000")),
            IndexInfo("^VIX", "CBOE Volatility Index", listOf("vix", "volatility")),
            IndexInfo("^FTSE", "FTSE 100", listOf("ftse", "footsie", "uk 100")),
            IndexInfo("^GDAXI", "DAX", listOf("dax", "german")),
            IndexInfo("^FCHI", "CAC 40", listOf("cac", "cac 40", "french")),
            IndexInfo("^N225", "Nikkei 225", listOf("nikkei", "n225", "japan")),
            IndexInfo("^STOXX50E", "Euro Stoxx 50", listOf("stoxx", "eurostoxx", "euro stoxx"))
        )
    }
}
