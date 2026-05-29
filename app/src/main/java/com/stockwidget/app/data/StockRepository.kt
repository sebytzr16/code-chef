package com.stockwidget.app.data

import android.content.Context
import com.stockwidget.app.data.model.PricePoint
import com.stockwidget.app.data.model.QuoteSnapshot
import com.stockwidget.app.data.model.Stock
import com.stockwidget.app.data.model.StockQuote
import com.stockwidget.app.data.remote.FinnhubApi
import com.stockwidget.app.data.remote.FinnhubClient
import com.stockwidget.app.data.remote.SymbolMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coordinates remote quotes (Finnhub) with the locally persisted symbols and history.
 */
class StockRepository(
    context: Context,
    private val api: FinnhubApi = FinnhubClient.api
) {
    private val store = PreferencesStore(context)

    val preferences: PreferencesStore get() = store

    /**
     * Fetches the latest quote for each tracked symbol, records a history sample, and
     * returns the combined view models. Network failures are captured per-symbol so one
     * bad ticker doesn't blank the whole widget.
     */
    suspend fun refreshAll(): List<StockQuote> = withContext(Dispatchers.IO) {
        val token = store.apiKey
        store.getStocks().map { stock ->
            if (token.isBlank()) {
                return@map StockQuote(
                    symbol = stock.symbol,
                    displayName = stock.displayName,
                    error = "No API key"
                )
            }
            try {
                val quote = api.getQuote(stock.symbol, token)
                if (quote.current <= 0f && quote.open <= 0f) {
                    // Finnhub returns all-zeros for unknown/unsupported symbols.
                    StockQuote(
                        symbol = stock.symbol,
                        displayName = stock.displayName,
                        error = "No data",
                        history = store.getHistory(stock.symbol)
                    )
                } else {
                    val now = System.currentTimeMillis()
                    store.appendPricePoint(stock.symbol, PricePoint(now, quote.current))
                    // Persist the full snapshot so the app/widgets render instantly offline.
                    store.saveSnapshot(
                        stock.symbol,
                        QuoteSnapshot(
                            current = quote.current,
                            open = quote.open,
                            previousClose = quote.previousClose,
                            high = quote.high,
                            low = quote.low,
                            updatedAt = now
                        )
                    )
                    StockQuote(
                        symbol = stock.symbol,
                        displayName = stock.displayName,
                        current = quote.current,
                        open = quote.open,
                        previousClose = quote.previousClose,
                        high = quote.high,
                        low = quote.low,
                        updatedAt = now,
                        history = store.getHistory(stock.symbol)
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

    suspend fun searchSymbols(query: String): List<SymbolMatch> = withContext(Dispatchers.IO) {
        val token = store.apiKey
        if (token.isBlank() || query.isBlank()) return@withContext emptyList()
        runCatching { api.search(query, token).result }.getOrDefault(emptyList())
    }
}
