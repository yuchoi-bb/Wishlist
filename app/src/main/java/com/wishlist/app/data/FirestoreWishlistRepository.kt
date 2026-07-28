package com.wishlist.app.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.wishlist.app.util.toStartOfDayMillis
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Real-time item storage backed by Firestore, scoped to users/{uid}/wishlist_items so every
 * signed-in user only ever sees their own data. Firestore's own client cache gives offline
 * reads/writes for free; changes made on one device are pushed to every other listening device
 * as soon as they're online.
 */
class FirestoreWishlistRepository(private val firestore: FirebaseFirestore) {

    /**
     * The last listener failure, or null while sync is healthy. A rejected listener used to be
     * rethrown into the collecting coroutine, which took the whole app down — a security rule that
     * hasn't been published yet is a reason to show a message, not to crash.
     */
    val syncError = MutableStateFlow<String?>(null)

    private fun itemsCollection(uid: String) =
        firestore.collection("users").document(uid).collection("wishlist_items")

    fun observeItems(uid: String): Flow<List<WishlistItem>> = callbackFlow {
        val registration = itemsCollection(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                syncError.value = error.describe("할 일")
                trySend(emptyList())
                return@addSnapshotListener
            }
            syncError.value = null
            trySend(snapshot?.documents.orEmpty().map { it.toWishlistItem() })
        }
        awaitClose { registration.remove() }
    }

    suspend fun saveItem(uid: String, item: WishlistItem) = guarded("할 일") {
        val data = item.toFirestoreMap()
        if (item.id.isBlank()) {
            itemsCollection(uid).add(data).await()
        } else {
            itemsCollection(uid).document(item.id).set(data).await()
        }
    }

    suspend fun deleteItem(uid: String, item: WishlistItem) = guarded("할 일") {
        if (item.id.isNotBlank()) itemsCollection(uid).document(item.id).delete().await()
    }

    /**
     * Runs a write and turns a rejection into [syncError] instead of an exception. Writes are
     * launched from click handlers, where an uncaught failure would take the app down for something
     * as recoverable as a rule that hasn't been published.
     */
    private suspend fun guarded(what: String, block: suspend () -> Unit) {
        runCatching { block() }
            .onSuccess { syncError.value = null }
            .onFailure { failure ->
                syncError.value = (failure as? FirebaseFirestoreException)?.describe(what)
                    ?: "$what 저장 실패: ${failure.message}"
            }
    }

    suspend fun getAllItemsOnce(uid: String): List<WishlistItem> =
        itemsCollection(uid).get().await().documents.map { it.toWishlistItem() }

    // Category colors live in a single document rather than one per category: the whole set is
    // small, always read together, and rewritten as a unit whenever one color changes.
    private fun categoryColorsDoc(uid: String) =
        firestore.collection("users").document(uid).collection("settings").document("categoryColors")

    fun observeCategoryColors(uid: String): Flow<List<CategoryColorPref>> = callbackFlow {
        val registration = categoryColorsDoc(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Colors are decoration: without them the automatic ones still work, so the rest of
                // the app carries on and only the banner mentions it.
                syncError.value = error.describe("분류 색")
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snapshot.readCategoryColors())
        }
        awaitClose { registration.remove() }
    }

    suspend fun saveCategoryColors(uid: String, prefs: List<CategoryColorPref>) = guarded("분류 색") {
        val entries = prefs.map {
            mapOf("major" to it.major, "minor" to it.minor, "paletteIndex" to it.paletteIndex)
        }
        categoryColorsDoc(uid).set(mapOf("entries" to entries)).await()
    }

    suspend fun getCategoryColorsOnce(uid: String): List<CategoryColorPref> =
        categoryColorsDoc(uid).get().await().readCategoryColors()
}

/**
 * A short Korean explanation of a listener failure. PERMISSION_DENIED is the one worth naming: it
 * means the Firestore security rules don't cover this path yet, which no amount of retrying fixes.
 */
private fun FirebaseFirestoreException.describe(what: String): String = when (code) {
    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        "$what 동기화 권한이 없습니다. Firebase 콘솔에서 Firestore 규칙을 게시했는지 확인해주세요."
    FirebaseFirestoreException.Code.UNAVAILABLE ->
        "$what 동기화 연결이 끊겼습니다. 네트워크가 돌아오면 자동으로 다시 붙습니다."
    else -> "$what 동기화 오류: ${code.name}"
}

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot?.readCategoryColors(): List<CategoryColorPref> {
    val raw = this?.get("entries") as? List<Map<String, Any?>> ?: return emptyList()
    return raw.mapNotNull { entry ->
        val major = entry["major"] as? String ?: return@mapNotNull null
        CategoryColorPref(
            major = major,
            minor = entry["minor"] as? String,
            paletteIndex = (entry["paletteIndex"] as? Number)?.toInt() ?: 0,
        )
    }
}

private fun DocumentSnapshot.toWishlistItem(): WishlistItem {
    // Documents written before 완료일 was split into 종료일 + 완료 only have completedAt, which
    // meant "finished on this date" — read it as both the end date and the done flag.
    val legacyCompletedAt = getLong("completedAt")
    return WishlistItem(
        id = id,
        title = getString("title") ?: "",
        memo = getString("memo"),
        subItems = readSubItems(),
        majorCategory = getString("majorCategory"),
        minorCategory = getString("minorCategory"),
        startedAt = getLong("startedAt") ?: 0L,
        endDate = getLong("endDate") ?: legacyCompletedAt,
        isDone = getBoolean("isDone") ?: (legacyCompletedAt != null),
        priority = (getLong("priority") ?: WishlistItem.DEFAULT_PRIORITY.toLong()).toInt(),
        position = getLong("position") ?: 0L,
    )
}

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.readSubItems(): List<SubItem> {
    val raw = get("subItems") as? List<Map<String, Any?>> ?: return emptyList()
    return raw.map { entry ->
        SubItem(
            title = entry["title"] as? String ?: "",
            done = entry["done"] as? Boolean ?: false,
            endDate = (entry["endDate"] as? Number)?.toLong(),
        )
    }
}

private fun WishlistItem.toFirestoreMap(): Map<String, Any?> = mapOf(
    "title" to title,
    "memo" to memo,
    "subItems" to subItems.map {
        mapOf(
            "title" to it.title,
            "done" to it.done,
            "endDate" to it.endDate?.toStartOfDayMillis(),
        )
    },
    "majorCategory" to majorCategory,
    "minorCategory" to minorCategory,
    // Normalized on every write, so items created before 시작일/완료일 became date-only (and any
    // restored from an older backup) get cleaned up the next time they're saved.
    "startedAt" to startedAt.toStartOfDayMillis(),
    "endDate" to endDate?.toStartOfDayMillis(),
    "isDone" to isDone,
    "priority" to priority,
    "position" to position,
)
