package com.duitrip.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Exchange-rate fetcher ported from backend/app/services/exchange_rates.py.
 * Base = destination currency; the returned map is dest-per-unit with rates[base] = 1.0.
 * A simple in-memory TTL cache mirrors the old server cache; on failure it falls back
 * to the last good value, or 1.0 for every symbol.
 */
object FrankfurterClient {

    private const val CACHE_TTL_MS = 3_600_000L // 1 hour

    private data class Entry(val rates: Map<String, Double>, val ts: Long, var stale: Boolean)

    private val cache = HashMap<String, Entry>()

    suspend fun fetchRates(base: String, symbols: List<String>): Map<String, Double> {
        val symbolsKey = symbols.filter { it != base }.distinct().sorted().joinToString(",")
        if (symbolsKey.isEmpty()) return mapOf(base to 1.0)

        val cacheKey = "$base:$symbolsKey"
        val now = System.currentTimeMillis()
        cache[cacheKey]?.let { if (now - it.ts < CACHE_TTL_MS) return it.rates }

        return withContext(Dispatchers.IO) {
            try {
                val url = URL(
                    "https://api.frankfurter.app/latest?base=" +
                        URLEncoder.encode(base, "UTF-8") +
                        "&symbols=" + URLEncoder.encode(symbolsKey, "UTF-8"),
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    requestMethod = "GET"
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val ratesJson = JSONObject(body).getJSONObject("rates")
                val rates = HashMap<String, Double>()
                ratesJson.keys().forEach { k -> rates[k] = ratesJson.getDouble(k) }
                rates[base] = 1.0
                cache[cacheKey] = Entry(rates, now, stale = false)
                rates
            } catch (e: Exception) {
                cache[cacheKey]?.let { it.stale = true; return@withContext it.rates }
                (symbols + base).associateWith { 1.0 }
            }
        }
    }

    fun isStale(base: String, symbols: List<String>): Boolean {
        val symbolsKey = symbols.filter { it != base }.distinct().sorted().joinToString(",")
        return cache["$base:$symbolsKey"]?.stale ?: false
    }
}
