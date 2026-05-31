package com.voiceai.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WebSearchEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        runCatching {
            if (isWeatherQuery(query)) fetchWeather(query) ?: ddgInstantAnswer(query)
            else ddgInstantAnswer(query)
        }.getOrDefault("I couldn't retrieve that information right now.")
    }

    private fun isWeatherQuery(q: String): Boolean {
        val lq = q.lowercase()
        return listOf("weather", "temperature", "rain", "snow", "sunny", "cloudy", "forecast")
            .any { lq.contains(it) }
    }

    /** wttr.in provides free weather in a single compact line — ideal for voice. */
    private fun fetchWeather(query: String): String? {
        val city = query
            .replace(Regex("(?i)weather|in|for|today|tomorrow|forecast|current|what|is|the"), "")
            .trim()
            .replace(" ", "+")
            .ifEmpty { return null }

        val request = Request.Builder()
            .url("https://wttr.in/$city?format=3")
            .header("User-Agent", "VoiceAI/1.0")
            .build()
        val response = client.newCall(request).execute()
        return if (response.isSuccessful) response.body?.string()?.trim() else null
    }

    /** DuckDuckGo Instant Answer API — no API key, no rate limit for low-volume use. */
    private fun ddgInstantAnswer(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
            .header("User-Agent", "VoiceAI/1.0")
            .build()

        val body = client.newCall(request).execute().body?.string() ?: return fallbackScrape(query)

        val answer = extractField(body, "Answer")
        val abstract = extractField(body, "AbstractText")

        return when {
            answer.isNotBlank() -> answer
            abstract.isNotBlank() -> abstract.take(400)
            else -> fallbackScrape(query)
        }
    }

    /** Scrape the first snippet from DuckDuckGo HTML when the Instant Answer API returns nothing. */
    private fun fallbackScrape(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val doc = Jsoup.connect("https://html.duckduckgo.com/html/?q=$encoded")
            .userAgent("Mozilla/5.0 (compatible; VoiceAI/1.0)")
            .timeout(6000)
            .get()
        return doc.select(".result__snippet")
            .firstOrNull()?.text()
            ?.take(400)
            ?: "No relevant information found."
    }

    // Minimal JSON field extraction without a full JSON library
    private fun extractField(json: String, field: String): String =
        Regex(""""$field"\s*:\s*"(.*?)"""")
            .find(json)
            ?.groupValues?.get(1)
            ?.replace("\\u0026", "&")
            ?.replace("\\\"", "\"")
            ?.replace("\\n", " ")
            ?: ""
}
