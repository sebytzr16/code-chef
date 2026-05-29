package com.stockwidget.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Minimal Finnhub REST client. Only the free /quote endpoint is used.
 * Docs: https://finnhub.io/docs/api/quote
 */
interface FinnhubApi {

    @GET("quote")
    suspend fun getQuote(
        @Query("symbol") symbol: String,
        @Query("token") token: String
    ): QuoteResponse

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("token") token: String
    ): SymbolSearchResponse
}

/** Response shape of GET /quote. */
data class QuoteResponse(
    @SerializedName("c") val current: Float = 0f,
    @SerializedName("d") val change: Float = 0f,
    @SerializedName("dp") val changePercent: Float = 0f,
    @SerializedName("h") val high: Float = 0f,
    @SerializedName("l") val low: Float = 0f,
    @SerializedName("o") val open: Float = 0f,
    @SerializedName("pc") val previousClose: Float = 0f,
    @SerializedName("t") val timestamp: Long = 0L
)

data class SymbolSearchResponse(
    @SerializedName("count") val count: Int = 0,
    @SerializedName("result") val result: List<SymbolMatch> = emptyList()
)

data class SymbolMatch(
    @SerializedName("symbol") val symbol: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("type") val type: String = ""
)
