package com.stockwidget.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.stockwidget.app.data.model.StockQuote
import com.stockwidget.app.ui.theme.PriceDown
import com.stockwidget.app.ui.theme.PriceUp

/** In-app sparkline matching the widget's chart style. */
@Composable
fun Sparkline(
    quote: StockQuote,
    modifier: Modifier = Modifier
) {
    val up = quote.isUp
    val color = if (up) PriceUp else PriceDown

    val prices: List<Float> = rememberPrices(quote)

    Canvas(modifier = modifier) {
        if (prices.size < 2) {
            // Flat indicator.
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
            return@Canvas
        }

        val pad = 6f
        val chartW = size.width - pad * 2
        val chartH = size.height - pad * 2

        var min = prices.min().coerceAtMost(quote.open)
        var max = prices.max().coerceAtLeast(quote.open)
        if (max - min < 0.0001f) {
            max += 1f; min -= 1f
        }
        val range = max - min

        fun x(i: Int) = pad + chartW * i / (prices.size - 1)
        fun y(v: Float) = pad + chartH * (1f - (v - min) / range)

        // Opening-price baseline.
        val baseY = y(quote.open)
        drawLine(
            color = Color.Gray.copy(alpha = 0.3f),
            start = Offset(pad, baseY),
            end = Offset(size.width - pad, baseY),
            strokeWidth = 2f
        )

        // Filled gradient area.
        val area = Path().apply {
            moveTo(x(0), y(prices[0]))
            for (i in 1 until prices.size) lineTo(x(i), y(prices[i]))
            lineTo(x(prices.size - 1), size.height - pad)
            lineTo(x(0), size.height - pad)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.25f), Color.Transparent)
            )
        )

        // The line.
        val line = Path().apply {
            moveTo(x(0), y(prices[0]))
            for (i in 1 until prices.size) lineTo(x(i), y(prices[i]))
        }
        drawPath(
            path = line,
            color = color,
            style = Stroke(width = 5f, cap = StrokeCap.Round)
        )

        // Latest-point dot.
        drawCircle(color = color, radius = 6f, center = Offset(x(prices.size - 1), y(prices.last())))
    }
}

@Composable
private fun rememberPrices(quote: StockQuote): List<Float> {
    val history = quote.history.map { it.price }.filter { it > 0f }
    return when {
        history.size >= 2 -> history
        quote.open > 0f && quote.current > 0f -> listOf(quote.open, quote.current)
        else -> history
    }
}
