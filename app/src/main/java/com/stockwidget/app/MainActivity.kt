package com.stockwidget.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import com.stockwidget.app.data.model.ThemeMode
import com.stockwidget.app.ui.StockDetailScreen
import com.stockwidget.app.ui.StockListScreen
import com.stockwidget.app.ui.StockViewModel
import com.stockwidget.app.ui.theme.StockWidgetTheme

class MainActivity : ComponentActivity() {

    private val viewModel: StockViewModel by viewModels()

    // Symbol to open straight into a detail screen (set when launched from a widget).
    private val deepLinkSymbol = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkSymbol.value = intent?.getStringExtra(EXTRA_OPEN_SYMBOL)
        setContent {
            val state by viewModel.state.collectAsState()
            val darkTheme = when (state.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            StockWidgetTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Lightweight in-memory navigation: null = list, else the detail symbol.
                    val requested by deepLinkSymbol
                    var detailSymbol by remember { mutableStateOf<String?>(null) }

                    // Honor a deep-link request from a widget tap.
                    LaunchedEffect(requested) {
                        if (requested != null) {
                            detailSymbol = requested
                            deepLinkSymbol.value = null
                        }
                    }

                    val current = detailSymbol
                    if (current == null) {
                        StockListScreen(
                            viewModel = viewModel,
                            onOpenDetail = { detailSymbol = it }
                        )
                    } else {
                        BackHandler { detailSymbol = null }
                        StockDetailScreen(
                            viewModel = viewModel,
                            symbol = current,
                            onBack = { detailSymbol = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkSymbol.value = intent.getStringExtra(EXTRA_OPEN_SYMBOL)
    }

    override fun onResume() {
        super.onResume()
        // Pick up any changes (e.g. after returning from another app) and refresh.
        viewModel.refresh()
    }

    companion object {
        const val EXTRA_OPEN_SYMBOL = "open_symbol"
    }
}
