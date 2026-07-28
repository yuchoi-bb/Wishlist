package com.wishlist.app.backup

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.wishlist.app.data.FirestoreWishlistRepository
import com.wishlist.app.data.SortField
import com.wishlist.app.data.SortPreference
import com.wishlist.app.data.SubItem
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
 * Items now live in Firestore (real-time multi-device sync); this is a user-triggered export/
 * import of that data plus the local per-category sort prefs, on top of the OS-level automatic
 * backup configured via android:allowBackup / dataExtractionRules in the manifest.
 */
class DriveBackupManager(
    private val context: Context,
    private val database: WishlistDatabase,
    private val firestoreRepository: FirestoreWishlistRepository,
) {
    fun signedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    suspend fun backup(account: GoogleSignInAccount, uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val drive = driveService(account)
            val content = ByteArrayContent("application/json", exportJson(uid).toByteArray())
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

    suspend fun restore(account: GoogleSignInAccount, uid: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val drive = driveService(account)
            val existing = findBackupFile(drive) ?: error("Drive에 저장된 백업이 없습니다.")
            val output = ByteArrayOutputStream()
            drive.files().get(existing.id).executeMediaAndDownloadTo(output)
            importJson(uid, output.toString(Charsets.UTF_8.name()))
        }
    }

    private fun driveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(DriveScopes.DRIVE_APPDATA))
        credential.selectedAccount = requireNotNull(account.account) { "Google 계정 정보를 가져올 수 없습니다." }
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("Wishlist")
            .build()
    }

    /**
     * When the backup on Drive was last written, or null if there isn't one yet. Read from the
     * file itself rather than a local note, so it stays truthful even when the backup was made
     * from a different device.
     */
    suspend fun lastBackupAt(account: GoogleSignInAccount): Result<Long?> = withContext(Dispatchers.IO) {
        runCatching {
            findBackupFile(driveService(account))?.modifiedTime?.value
        }
    }

    private fun findBackupFile(drive: Drive): DriveFile? {
        val result = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$BACKUP_FILE_NAME'")
            .setFields("files(id, name, modifiedTime)")
            .execute()
        return result.files?.firstOrNull()
    }

    private suspend fun exportJson(uid: String): String {
        val items = firestoreRepository.getAllItemsOnce(uid)
        val sort = database.sortPreferenceDao().observe().first() ?: SortPreference()
        return JSONObject().apply {
            put("items", JSONArray(items.map { it.toJson() }))
            put("sort", sort.toJson())
        }.toString()
    }

    private suspend fun importJson(uid: String, text: String) {
        val root = JSONObject(text)
        val items = root.getJSONArray("items")
        for (i in 0 until items.length()) {
            firestoreRepository.saveItem(uid, items.getJSONObject(i).toWishlistItem())
        }
        // Older backups carried a "prefs" array of per-category sorts, which no longer exists now
        // that one sort applies to the whole table; those are simply skipped.
        root.optJSONObject("sort")?.let { database.sortPreferenceDao().upsert(it.toSortPreference()) }
    }

    companion object {
        private const val BACKUP_FILE_NAME = "wishlist_backup.json"
    }
}

private fun WishlistItem.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("memo", memo)
    put(
        "subItems",
        JSONArray(
            subItems.map { sub ->
                JSONObject().apply {
                    put("title", sub.title)
                    put("done", sub.done)
                    put("endDate", sub.endDate)
                }
            },
        ),
    )
    put("majorCategory", majorCategory)
    put("minorCategory", minorCategory)
    put("startedAt", startedAt)
    put("endDate", endDate)
    put("isDone", isDone)
    put("priority", priority)
    put("position", position)
}

private fun JSONObject.toWishlistItem(): WishlistItem = WishlistItem(
    id = optString("id", ""),
    title = getString("title"),
    memo = if (isNull("memo")) null else optString("memo"),
    subItems = optJSONArray("subItems")?.let { array ->
        (0 until array.length()).map { i ->
            val entry = array.getJSONObject(i)
            SubItem(
                title = entry.optString("title"),
                done = entry.optBoolean("done"),
                endDate = if (entry.isNull("endDate")) null else entry.optLong("endDate"),
            )
        }
    }.orEmpty(),
    majorCategory = if (isNull("majorCategory")) null else optString("majorCategory"),
    minorCategory = if (isNull("minorCategory")) null else optString("minorCategory"),
    startedAt = getLong("startedAt"),
    // Older backups only carry completedAt, which meant "finished on this date".
    endDate = when {
        has("endDate") && !isNull("endDate") -> optLong("endDate")
        has("completedAt") && !isNull("completedAt") -> optLong("completedAt")
        else -> null
    },
    isDone = if (has("isDone")) optBoolean("isDone") else has("completedAt") && !isNull("completedAt"),
    priority = optInt("priority", WishlistItem.DEFAULT_PRIORITY),
    position = optLong("position", 0L),
)

private fun SortPreference.toJson(): JSONObject = JSONObject().apply {
    put("sortField", sortField.name)
    put("ascending", ascending)
}

private fun JSONObject.toSortPreference(): SortPreference = SortPreference(
    sortField = runCatching { SortField.valueOf(getString("sortField")) }.getOrDefault(SortField.END_DATE),
    ascending = optBoolean("ascending", true),
)
