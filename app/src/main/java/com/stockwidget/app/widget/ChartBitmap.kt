package com.stockwidget.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.TypedValue
import android.content.res.Resources
import com.stockwidget.app.data.model.StockQuote

/**
 * Renders an intraday sparkline for a [StockQuote] into a [Bitmap] that RemoteViews can
 * display. Green when the price is at/above the day's open, red when below. Only the
 * price line (and its gradient fill) is drawn — no baseline.
 */
object ChartBitmap {

    val GREEN = 0xFF4CAF50.toInt()
    val GREEN_FILL = 0x334CAF50
    val RED = 0xFFEF5350.toInt()
    val RED_FILL = 0x33EF5350

    fun render(quote: StockQuote, widthDp: Int = 72, heightDp: Int = 40): Bitmap {
        val w = dp(widthDp)
        val h = dp(heightDp)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val color = if (quote.isUp) GREEN else RED
        val fill = if (quote.isUp) GREEN_FILL else RED_FILL

        // Use captured history; fall back to a flat open->current segment if sparse.
        val prices = buildPrices(quote)
        if (prices.size < 2) {
            drawFlat(canvas, w, h, color)
            return bitmap
        }

        val pad = dp(3).toFloat()
        val chartW = w - pad * 2
        val chartH = h - pad * 2

        var min = prices.min()
        var max = prices.max()
        if (max - min < 0.0001f) {
            max += 1f
            min -= 1f
        }
        val range = max - min

        fun x(i: Int) = pad + chartW * i / (prices.size - 1)
        fun y(v: Float) = pad + chartH * (1f - (v - min) / range)

        // Filled area under the line.
        val areaPath = Path().apply {
            moveTo(x(0), y(prices[0]))
            for (i in 1 until prices.size) lineTo(x(i), y(prices[i]))
            lineTo(x(prices.size - 1), h - pad)
            lineTo(x(0), h - pad)
            close()
        }
        val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, pad, 0f, h.toFloat(),
                fill, 0x00000000, Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(areaPath, areaPaint)

        // The line itself.
        val linePath = Path().apply {
            moveTo(x(0), y(prices[0]))
            for (i in 1 until prices.size) lineTo(x(i), y(prices[i]))
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(linePath, linePaint)

        // Dot on the latest point.
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawCircle(x(prices.size - 1), y(prices.last()), dp(2).toFloat(), dotPaint)

        return bitmap
    }

    private fun buildPrices(quote: StockQuote): List<Float> {
        val history = quote.history.map { it.price }.filter { it > 0f }
        return when {
            history.size >= 2 -> history
            quote.open > 0f && quote.current > 0f -> listOf(quote.open, quote.current)
            else -> history
        }
    }

    private fun drawFlat(canvas: Canvas, w: Int, h: Int, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = dp(2).toFloat()
            strokeCap = Paint.Cap.ROUND
        }
        val mid = h / 2f
        val pad = dp(3).toFloat()
        canvas.drawLine(pad, mid, w - pad, mid, paint)
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        Resources.getSystem().displayMetrics
    ).toInt().coerceAtLeast(1)
}
