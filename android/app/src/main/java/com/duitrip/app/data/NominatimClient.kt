package com.duitrip.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A destination suggestion from OpenStreetMap Nominatim (parity with the PWA). */
data class PlaceSuggestion(val displayName: String, val countryCode: String?)

object NominatimClient {

    /** Search destinations; returns up to 5 suggestions. Fails soft to an empty list. */
    suspend fun search(query: String): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://nominatim.openstreetmap.org/search?format=json&addressdetails=1&limit=5&q=" +
                    URLEncoder.encode(query, "UTF-8"),
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", "Duitrip/2.0 (Android)")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PlaceSuggestion(
                    displayName = o.optString("display_name"),
                    countryCode = o.optJSONObject("address")?.optString("country_code")?.takeIf { it.isNotBlank() },
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
