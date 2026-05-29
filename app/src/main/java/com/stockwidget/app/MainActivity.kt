package com.stockwidget.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.stockwidget.app.ui.StockListScreen
import com.stockwidget.app.ui.StockViewModel
import com.stockwidget.app.ui.theme.StockWidgetTheme

class MainActivity : ComponentActivity() {

    private val viewModel: StockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StockWidgetTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StockListScreen(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up any changes (e.g. after returning from another app) and refresh.
        viewModel.refresh()
    }
}
