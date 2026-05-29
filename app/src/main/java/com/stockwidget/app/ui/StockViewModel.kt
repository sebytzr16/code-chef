package com.stockwidget.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stockwidget.app.data.StockRepository
import com.stockwidget.app.data.model.Stock
import com.stockwidget.app.data.model.StockQuote
import com.stockwidget.app.data.remote.SymbolMatch
import com.stockwidget.app.widget.WidgetUpdater
import com.stockwidget.app.work.RefreshWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val quotes: List<StockQuote> = emptyList(),
    val apiKey: String = "",
    val refreshMinutes: Int = 30,
    val isLoading: Boolean = false,
    val message: String? = null
)

class StockViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = StockRepository(app)
    private val store = repository.preferences

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val hasApiKey: Boolean get() = store.hasApiKey

    init {
        loadFromCache()
        refresh()
    }

    private fun loadFromCache() {
        _state.value = _state.value.copy(
            quotes = repository.cachedQuotes(),
            apiKey = store.apiKey,
            refreshMinutes = store.refreshMinutes
        )
    }

    fun refresh() {
        if (!store.hasApiKey) {
            _state.value = _state.value.copy(
                quotes = repository.cachedQuotes(),
                message = if (store.getStocks().isEmpty()) null else "Add your Finnhub API key to load prices"
            )
            return
        }
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            val quotes = repository.refreshAll()
            _state.value = _state.value.copy(quotes = quotes, isLoading = false)
            WidgetUpdater.notifyDataChanged(getApplication())
        }
    }

    fun addStock(symbol: String, name: String) {
        val clean = symbol.trim().uppercase()
        if (clean.isEmpty()) return
        store.addStock(Stock(clean, name.ifBlank { clean }))
        loadFromCache()
        refresh()
    }

    fun removeStock(symbol: String) {
        store.removeStock(symbol)
        loadFromCache()
        WidgetUpdater.notifyDataChanged(getApplication())
    }

    fun saveApiKey(key: String) {
        store.apiKey = key
        _state.value = _state.value.copy(apiKey = key, message = "API key saved")
        RefreshWorker.schedule(getApplication())
        refresh()
    }

    fun setRefreshMinutes(minutes: Int) {
        store.refreshMinutes = minutes
        _state.value = _state.value.copy(refreshMinutes = minutes)
        RefreshWorker.schedule(getApplication())
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    suspend fun search(query: String): List<SymbolMatch> = repository.searchSymbols(query)
}
