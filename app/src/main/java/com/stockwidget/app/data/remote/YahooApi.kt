package com.stockwidget.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Keyless Yahoo Finance endpoints. No API key or sign-up required.
 *
 * - The chart endpoint returns the live price, the day's open, the previous close and the
 *   full intraday series used to draw the sparkline — all in one call.
 * - The search endpoint powers the "add a stock" lookup.
 *
 * These are unofficial endpoints; a browser-like User-Agent is sent (see YahooClient).
 */
interface YahooApi {

    @GET("v8/finance/chart/{symbol}")
    suspend fun getChart(
        @Path("symbol") symbol: String,
        @Query("range") range: String,
        @Query("interval") interval: String
    ): ChartResponse

    @GET("v1/finance/search")
    suspend fun search(
        @Query("q") query: String
    ): SearchResponse
}

// ---- Chart -----------------------------------------------------------------

data class ChartResponse(
    @SerializedName("chart") val chart: Chart = Chart()
)

data class Chart(
    @SerializedName("result") val result: List<ChartResult>? = null,
    @SerializedName("error") val error: ChartError? = null
)

data class ChartError(
    @SerializedName("code") val code: String? = null,
    @SerializedName("description") val description: String? = null
)

data class ChartResult(
    @SerializedName("meta") val meta: ChartMeta = ChartMeta(),
    @SerializedName("timestamp") val timestamp: List<Long>? = null,
    @SerializedName("indicators") val indicators: Indicators = Indicators()
)

data class ChartMeta(
    @SerializedName("symbol") val symbol: String? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("exchangeName") val exchangeName: String? = null,
    @SerializedName("fullExchangeName") val fullExchangeName: String? = null,
    @SerializedName("regularMarketPrice") val regularMarketPrice: Float? = null,
    @SerializedName("chartPreviousClose") val chartPreviousClose: Float? = null,
    @SerializedName("previousClose") val previousClose: Float? = null,
    @SerializedName("regularMarketDayHigh") val regularMarketDayHigh: Float? = null,
    @SerializedName("regularMarketDayLow") val regularMarketDayLow: Float? = null,
    @SerializedName("regularMarketTime") val regularMarketTime: Long? = null,
    @SerializedName("shortName") val shortName: String? = null
)

data class Indicators(
    @SerializedName("quote") val quote: List<QuoteSeries>? = null
)

data class QuoteSeries(
    @SerializedName("open") val open: List<Float?>? = null,
    @SerializedName("close") val close: List<Float?>? = null,
    @SerializedName("high") val high: List<Float?>? = null,
    @SerializedName("low") val low: List<Float?>? = null
)

// ---- Search ----------------------------------------------------------------

data class SearchResponse(
    @SerializedName("quotes") val quotes: List<SearchQuote>? = null
)

data class SearchQuote(
    @SerializedName("symbol") val symbol: String? = null,
    @SerializedName("shortname") val shortname: String? = null,
    @SerializedName("longname") val longname: String? = null,
    @SerializedName("quoteType") val quoteType: String? = null,
    @SerializedName("exchDisp") val exchDisp: String? = null
)
