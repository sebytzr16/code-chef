package com.stockwidget.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.stockwidget.app.data.model.PricePoint
import com.stockwidget.app.data.model.StockQuote
import com.stockwidget.app.ui.theme.PriceDown
import com.stockwidget.app.ui.theme.PriceUp
import kotlin.math.roundToInt

/**
 * An intraday chart you can scrub: drag horizontally to inspect the price at each point
 * in the day. The selected point is reported via [onScrub] (null when not scrubbing).
 */
@Composable
fun InteractiveChart(
    quote: StockQuote,
    modifier: Modifier = Modifier,
    onScrub: (PricePoint?) -> Unit
) {
    val color = if (quote.isUp) PriceUp else PriceDown
    val points = remember(quote.history) { quote.history.filter { it.price > 0f } }
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val surface = MaterialTheme.colorScheme.surface

    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }

    LaunchedEffect(selectedIndex, points) {
        onScrub(selectedIndex?.let { points.getOrNull(it) })
    }

    Canvas(
        modifier = modifier.pointerInput(points) {
            if (points.size < 2) return@pointerInput
            val padPx = 8.dp.toPx()
            fun indexFor(x: Float): Int {
                val w = size.width.toFloat()
                val span = (w - padPx * 2).coerceAtLeast(1f)
                val frac = ((x - padPx) / span).coerceIn(0f, 1f)
                return (frac * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
            }
            detectHorizontalDragGestures(
                onDragStart = { offset -> selectedIndex = indexFor(offset.x) },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    selectedIndex = indexFor(change.position.x)
                },
                onDragEnd = { selectedIndex = null },
                onDragCancel = { selectedIndex = null }
            )
        }
    ) {
        if (points.size < 2) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
            return@Canvas
        }

        val pad = 8.dp.toPx()
        val chartW = size.width - pad * 2
        val chartH = size.height - pad * 2

        var min = points.minOf { it.price }
        var max = points.maxOf { it.price }
        if (max - min < 0.0001f) { max += 1f; min -= 1f }
        val range = max - min

        fun x(i: Int) = pad + chartW * i / (points.size - 1)
        fun y(v: Float) = pad + chartH * (1f - (v - min) / range)

        // Filled gradient area.
        val area = Path().apply {
            moveTo(x(0), y(points[0].price))
            for (i in 1 until points.size) lineTo(x(i), y(points[i].price))
            lineTo(x(points.size - 1), size.height - pad)
            lineTo(x(0), size.height - pad)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent)))

        // Line.
        val line = Path().apply {
            moveTo(x(0), y(points[0].price))
            for (i in 1 until points.size) lineTo(x(i), y(points[i].price))
        }
        drawPath(line, color = color, style = Stroke(width = 5f, cap = StrokeCap.Round))

        // Scrub indicator or latest dot.
        val sel = selectedIndex
        if (sel != null) {
            val sx = x(sel)
            val sy = y(points[sel].price)
            drawLine(
                color = gridColor,
                start = Offset(sx, pad),
                end = Offset(sx, size.height - pad),
                strokeWidth = 2f
            )
            drawCircle(color = surface, radius = 11f, center = Offset(sx, sy))
            drawCircle(color = color, radius = 7f, center = Offset(sx, sy))
        } else {
            drawCircle(color = color, radius = 6f, center = Offset(x(points.size - 1), y(points.last().price)))
        }
    }
}
