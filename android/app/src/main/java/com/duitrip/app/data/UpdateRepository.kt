package com.duitrip.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.duitrip.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app update check against this app's own public GitHub Releases (no Play Store).
 * The user still approves Android's "install unknown apps" system prompt — that step
 * can't be skipped for a sideloaded APK — but this avoids them having to find and
 * download the release manually.
 */
class UpdateRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("update_checker", Context.MODE_PRIVATE)

    /** At most once every 24h unless [force]. */
    suspend fun checkForUpdate(force: Boolean = false): GithubRelease? {
        val now = System.currentTimeMillis()
        if (!force) {
            val lastCheck = prefs.getLong("last_check_ms", 0L)
            if (now - lastCheck < CHECK_INTERVAL_MS) return null
        }
        prefs.edit().putLong("last_check_ms", now).apply()

        val release = GithubReleaseClient.fetchLatest() ?: return null
        return release.takeIf { SemVer.isNewer(it.version, BuildConfig.VERSION_NAME) }
    }

    suspend fun downloadApk(release: GithubRelease): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "duitrip-${release.version}.apk")
        val conn = (URL(release.apkDownloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        conn.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
        file
    }

    /** Launches the system package installer for the downloaded APK. */
    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
