package com.wishlist.app.backup

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.wishlist.app.data.CategorySortPref
import com.wishlist.app.data.SortField
import com.wishlist.app.data.WishlistDatabase
import com.wishlist.app.data.WishlistItem
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manual Google Drive backup/restore using the app-private appDataFolder (drive.appdata scope).
 * This is the second, user-triggered backup layer on top of the OS-level automatic backup
 * configured via android:allowBackup / dataExtractionRules in the manifest.
 */
class DriveBackupManager(
    private val context: Context,
    private val database: WishlistDatabase,
) {
    fun signInClient(): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun signedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    suspend fun backup(account: GoogleSignInAccount): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val drive = driveService(account)
            val content = ByteArrayContent("application/json", exportJson().toByteArray())
            val existing = findBackupFile(drive)
            if (existing != null) {
                drive.files().update(existing.id, null, content).execute()
            } else {
                val metadata = DriveFile().apply {
                    name = BACKUP_FILE_NAME
                    parents = listOf("appDataFolder")
                }
                drive.files().create(metadata, content).execute()
            }
            Unit
        }
    }

    suspend fun restore(account: GoogleSignInAccount): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val drive = driveService(account)
            val existing = findBackupFile(drive) ?: error("Drive에 저장된 백업이 없습니다.")
            val output = ByteArrayOutputStream()
            drive.files().get(existing.id).executeMediaAndDownloadTo(output)
            importJson(output.toString(Charsets.UTF_8.name()))
        }
    }

    private fun driveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))
        credential.selectedAccount = requireNotNull(account.account) { "Google 계정 정보를 가져올 수 없습니다." }
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("Wishlist")
            .build()
    }

    private fun findBackupFile(drive: Drive): DriveFile? {
        val result = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$BACKUP_FILE_NAME'")
            .setFields("files(id, name)")
            .execute()
        return result.files?.firstOrNull()
    }

    private suspend fun exportJson(): String {
        val items = database.wishlistDao().observeAll().first()
        val prefs = database.categorySortPrefDao().observeAll().first()
        return JSONObject().apply {
            put("items", JSONArray(items.map { it.toJson() }))
            put("prefs", JSONArray(prefs.map { it.toJson() }))
        }.toString()
    }

    private suspend fun importJson(text: String) {
        val root = JSONObject(text)
        val items = root.getJSONArray("items")
        for (i in 0 until items.length()) {
            database.wishlistDao().upsert(items.getJSONObject(i).toWishlistItem())
        }
        val prefs = root.optJSONArray("prefs")
        if (prefs != null) {
            for (i in 0 until prefs.length()) {
                database.categorySortPrefDao().upsert(prefs.getJSONObject(i).toCategorySortPref())
            }
        }
    }

    companion object {
        private const val BACKUP_FILE_NAME = "wishlist_backup.json"
    }
}

private fun WishlistItem.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("majorCategory", majorCategory)
    put("minorCategory", minorCategory)
    put("startedAt", startedAt)
    put("completedAt", completedAt)
}

private fun JSONObject.toWishlistItem(): WishlistItem = WishlistItem(
    id = optLong("id", 0),
    title = getString("title"),
    majorCategory = if (isNull("majorCategory")) null else optString("majorCategory"),
    minorCategory = if (isNull("minorCategory")) null else optString("minorCategory"),
    startedAt = getLong("startedAt"),
    completedAt = if (isNull("completedAt") || !has("completedAt")) null else optLong("completedAt"),
)

private fun CategorySortPref.toJson(): JSONObject = JSONObject().apply {
    put("categoryKey", categoryKey)
    put("sortField", sortField.name)
    put("ascending", ascending)
}

private fun JSONObject.toCategorySortPref(): CategorySortPref = CategorySortPref(
    categoryKey = getString("categoryKey"),
    sortField = runCatching { SortField.valueOf(getString("sortField")) }.getOrDefault(SortField.COMPLETED_AT),
    ascending = getBoolean("ascending"),
)
