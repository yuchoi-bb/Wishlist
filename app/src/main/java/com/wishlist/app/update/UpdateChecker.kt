package com.wishlist.app.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.wishlist.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UpdateInfo(val version: String, val apkUrl: String, val releaseNotes: String)

/**
 * Checks GitHub Releases for a newer version than the one currently installed, and if found,
 * downloads the APK asset via DownloadManager (which posts its own "download complete"
 * notification that opens the installer when tapped).
 */
class UpdateChecker(private val context: Context) {

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                "https://api.github.com/repos/${BuildConfig.UPDATE_REPO_OWNER}/${BuildConfig.UPDATE_REPO_NAME}/releases/latest",
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(body)
            val remoteVersion = json.getString("tag_name").removePrefix("v")
            val apkUrl = findApkAssetUrl(json)

            if (apkUrl != null && isNewerVersion(remoteVersion, BuildConfig.VERSION_NAME)) {
                UpdateInfo(remoteVersion, apkUrl, json.optString("body"))
            } else {
                null
            }
        }.getOrNull()
    }

    /**
     * True once [downloadUpdate] has been called for this version. The update check runs on every
     * launch and keeps reporting the same release until the user actually installs it, so without
     * this the same APK would be re-downloaded (and re-notified) every single time.
     */
    fun alreadyDownloaded(version: String): Boolean =
        prefs().getString(KEY_DOWNLOADED_VERSION, null) == version

    fun downloadUpdate(update: UpdateInfo) {
        prefs().edit().putString(KEY_DOWNLOADED_VERSION, update.version).apply()
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("Wishlist ${update.version} 업데이트")
            .setDescription("새 버전을 다운로드하고 있습니다")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "wishlist-${update.version}.apk",
            )
        downloadManager.enqueue(request)
    }

    private fun findApkAssetUrl(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name").endsWith(".apk")) {
                return asset.getString("browser_download_url")
            }
        }
        return null
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS_NAME = "update_checker"
        const val KEY_DOWNLOADED_VERSION = "downloaded_version"
    }
}
