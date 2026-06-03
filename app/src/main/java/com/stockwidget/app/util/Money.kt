package com.stockwidget.app.util

import java.util.Locale

/**
 * Formats prices using the symbol of the stock's trading currency, so the displayed
 * currency reflects the exchange the stock is traded on. The currency code (e.g. "USD")
 * is shown separately in the UI for disambiguation.
 */
object Money {

    /** Currency symbol/prefix for a currency code; empty when unknown. */
    fun symbol(currency: String): String = when (currency.uppercase(Locale.US)) {
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY", "CNY", "CNH" -> "¥"
        "HKD" -> "HK$"
        "AUD" -> "A$"
        "CAD" -> "C$"
        "NZD" -> "NZ$"
        "SGD" -> "S$"
        "CHF" -> "CHF "
        "INR" -> "₹"
        "KRW" -> "₩"
        "BRL" -> "R$"
        "RUB" -> "₽"
        "ZAR" -> "R"
        "TRY" -> "₺"
        "TWD" -> "NT$"
        "THB" -> "฿"
        "MXN" -> "$"
        "SEK", "NOK", "DKK" -> "kr "
        "PLN" -> "zł "
        else -> ""
    }

    private val ZERO_DECIMAL = setOf("JPY", "KRW", "CLP", "ISK", "HUF", "VND", "TWD")

    /** Format an amount with the currency's symbol, e.g. "$123.45" or "€1,234.50". */
    fun format(amount: Float, currency: String): String {
        val decimals = if (currency.uppercase(Locale.US) in ZERO_DECIMAL) 0 else 2
        val number = String.format(Locale.US, "%,.${decimals}f", amount)
        return symbol(currency) + number
    }

    /** Absolute percent label, e.g. "1.23%" (the arrow/colour convey direction). */
    fun percentChange(value: Float): String =
        String.format(Locale.US, "%.2f%%", kotlin.math.abs(value))

    /** Price with its currency code appended, e.g. "C$44.75 CAD" — for the widgets. */
    fun priceLabel(amount: Float, currency: String): String {
        val price = format(amount, currency)
        return if (currency.isBlank()) price else "$price ${currency.uppercase(Locale.US)}"
    }
}
