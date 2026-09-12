package com.wishlist.app.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.wishlist.app.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    /** Asset size from the release, used to tell a finished download from a truncated one. */
    val sizeBytes: Long,
    val releaseNotes: String,
)

/**
 * Finds newer releases on GitHub and brings them all the way to the system installer: check on
 * launch, download the APK, then hand it straight to the package installer. Android has no way for
 * a normal app to install silently, so the one unavoidable tap is the installer's own 설치 button —
 * everything before it happens without the user asking.
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
            val asset = findApkAsset(json)

            if (asset != null && isNewerVersion(remoteVersion, BuildConfig.VERSION_NAME)) {
                UpdateInfo(
                    version = remoteVersion,
                    apkUrl = asset.getString("browser_download_url"),
                    sizeBytes = asset.optLong("size"),
                    releaseNotes = json.optString("body"),
                )
            } else {
                null
            }
        }.getOrNull()
    }

    /**
     * The already-downloaded APK for this version, or null if it isn't there. Only a file whose
     * length matches the release asset counts, so a download interrupted half-way is re-fetched
     * instead of being handed to the installer as a broken package.
     */
    fun downloadedApk(update: UpdateInfo): File? =
        apkFile(update.version).takeIf { it.isFile && (update.sizeBytes <= 0 || it.length() == update.sizeBytes) }

    /** Enqueues the download and returns its id; the notification doubles as a progress indicator. */
    fun startDownload(update: UpdateInfo): Long {
        // DownloadManager appends "-1" to a name already on disk, so clear out a stale partial file
        // rather than downloading next to it.
        apkFile(update.version).delete()
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("Arc ${update.version} 업데이트")
            .setDescription("새 버전을 다운로드하고 있습니다")
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                apkFileName(update.version),
            )
        return downloadManager.enqueue(request)
    }

    /**
     * Waits for [downloadId] to finish, reporting percentage as it goes, and returns the finished
     * file — or null if the download failed. Polled rather than broadcast-based so it stops as soon
     * as the caller's coroutine is cancelled.
     */
    suspend fun awaitDownload(
        downloadId: Long,
        version: String,
        onProgress: (Int) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var finished: File? = null
        var polling = true
        while (polling) {
            val status = downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (!cursor.moveToFirst()) {
                    // The row is gone: the download was cancelled or cleared out from under us.
                    return@use DownloadManager.STATUS_FAILED
                }
                val state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val soFar = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                )
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                if (total > 0) onProgress(((soFar * 100) / total).toInt().coerceIn(0, 100))
                state
            }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    finished = apkFile(version).takeIf { it.isFile }
                    polling = false
                }
                DownloadManager.STATUS_FAILED -> polling = false
                else -> delay(POLL_INTERVAL_MS)
            }
        }
        finished
    }

    /**
     * Deletes downloaded APKs that are no longer needed — anything at or below the version now
     * running. The file cannot be removed at install time (the installer is still reading it, and
     * this process is replaced), so the cleanup happens on the next launch: once the update is
     * installed, the app comes back as that version and its APK is obsolete by this rule. An APK
     * newer than what's running is kept, since it's an update the user hasn't accepted yet.
     */
    suspend fun deleteInstalledApks(): Unit = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return@withContext
        dir.listFiles()?.forEach { file ->
            val isOurApk = file.name.startsWith(APK_PREFIX) && file.name.endsWith(APK_SUFFIX)
            val version = file.name.removeSurrounding(APK_PREFIX, APK_SUFFIX)
            if (isOurApk && !isNewerVersion(version, BuildConfig.VERSION_NAME)) {
                file.delete()
            }
        }
    }

    /** False until the user has allowed this app to install packages — a once-per-device switch. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the system installer for [file]; the user only has to confirm. */
    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** The one-time "이 출처의 앱 설치 허용" switch for this app. */
    fun installPermissionIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )

    private fun apkFileName(version: String) = "$APK_PREFIX$version$APK_SUFFIX"

    private fun apkFile(version: String) =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), apkFileName(version))

    private fun findApkAsset(release: JSONObject): JSONObject? {
        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name").endsWith(".apk")) return asset
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

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val POLL_INTERVAL_MS = 400L
        // Names the downloaded file on disk, so it stays as it is: deleteInstalledApks finds
        // old downloads by this prefix, and renaming it would strand every APK already there.
        const val APK_PREFIX = "wishlist-"
        const val APK_SUFFIX = ".apk"
    }
}
