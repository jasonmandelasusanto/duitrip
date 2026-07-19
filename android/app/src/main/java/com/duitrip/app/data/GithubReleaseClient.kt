package com.duitrip.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GithubRelease(val version: String, val apkDownloadUrl: String)

/** Checks the app's own public GitHub Releases for a newer signed APK. */
object GithubReleaseClient {
    private const val API_URL = "https://api.github.com/repos/jasonmandelasusanto/duitrip/releases/latest"

    /** Null on any failure (offline, rate-limited, no release yet) — fails soft. */
    suspend fun fetchLatest(): GithubRelease? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v").takeIf { it.isNotBlank() } ?: return@withContext null
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
            apkUrl?.let { GithubRelease(tag, it) }
        } catch (e: Exception) {
            null
        }
    }
}

/** Compares "1.2.3"-style versions numerically (so 2.10.0 > 2.9.0, unlike a string compare). */
object SemVer {
    fun isNewer(remote: String, current: String): Boolean {
        val r = parts(remote)
        val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    private fun parts(v: String): List<Int> =
        v.substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }
}
