package com.stockwidget.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stockwidget.app.data.StockRepository
import com.stockwidget.app.data.model.SearchResult
import com.stockwidget.app.data.model.Stock
import com.stockwidget.app.data.model.StockQuote
import com.stockwidget.app.data.model.ThemeMode
import com.stockwidget.app.widget.WidgetUpdater
import com.stockwidget.app.work.RefreshWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val quotes: List<StockQuote> = emptyList(),
    val isLoading: Boolean = false,
    val refreshMinutes: Int = 30,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lastRefreshAt: Long = 0L,
    val message: String? = null
)

class StockViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = StockRepository(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        // Show cached data instantly, then refresh from the network.
        _state.value = _state.value.copy(
            quotes = repository.cachedQuotes(),
            refreshMinutes = repository.preferences.refreshMinutes,
            themeMode = repository.preferences.themeMode,
            lastRefreshAt = repository.preferences.lastRefreshAt
        )
        refresh()
    }

    fun refresh() {
        if (repository.preferences.getStocks().isEmpty()) {
            _state.value = _state.value.copy(quotes = emptyList())
            return
        }
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            val quotes = repository.refreshAll()
            _state.value = _state.value.copy(
                quotes = quotes,
                isLoading = false,
                lastRefreshAt = repository.preferences.lastRefreshAt
            )
            WidgetUpdater.notifyDataChanged(getApplication())
        }
    }

    fun addStock(symbol: String, name: String) {
        val clean = symbol.trim().uppercase()
        if (clean.isEmpty()) return
        repository.preferences.addStock(Stock(clean, name.ifBlank { clean }))
        _state.value = _state.value.copy(quotes = repository.cachedQuotes())
        refresh()
    }

    fun removeStock(symbol: String) {
        repository.preferences.removeStock(symbol)
        _state.value = _state.value.copy(quotes = repository.cachedQuotes())
        WidgetUpdater.notifyDataChanged(getApplication())
    }

    fun isTracked(symbol: String): Boolean =
        repository.preferences.getStocks().any { it.symbol.equals(symbol, ignoreCase = true) }

    fun togglePin(symbol: String) = applyOrderChange { repository.preferences.togglePin(symbol) }

    fun moveUp(symbol: String) = applyOrderChange { repository.preferences.moveStock(symbol, up = true) }

    fun moveDown(symbol: String) = applyOrderChange { repository.preferences.moveStock(symbol, up = false) }

    /** Run a reorder/pin change, then refresh the on-screen list and the widgets. */
    private fun applyOrderChange(change: () -> Unit) {
        change()
        _state.value = _state.value.copy(quotes = repository.cachedQuotes())
        WidgetUpdater.notifyDataChanged(getApplication())
    }

    /** Set how often the app/widgets auto-refresh (e.g. 30 or 60 minutes). */
    fun setRefreshMinutes(minutes: Int) {
        repository.preferences.refreshMinutes = minutes
        RefreshWorker.schedule(getApplication())
        _state.value = _state.value.copy(refreshMinutes = minutes)
    }

    /** Set the light/dark appearance. */
    fun setThemeMode(mode: ThemeMode) {
        repository.preferences.themeMode = mode
        _state.value = _state.value.copy(themeMode = mode)
    }

    /** Cached quote for the detail screen — no network, instantly available. */
    fun cachedQuote(symbol: String): StockQuote? = repository.cachedQuote(symbol)

    suspend fun search(query: String): List<SearchResult> = repository.searchSymbols(query)

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
