package com.wishlist.app.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.wishlist.app.util.toStartOfDayMillis
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Real-time item storage backed by Firestore. A private 할 일 lives at
 * users/{uid}/wishlist_items/{id}, where only its owner can reach it; a shared one lives at
 * shared_items/{id} with a list of the uids allowed to see it. Firestore's own client cache gives
 * offline reads/writes for free; changes made on one device are pushed to every other listening
 * device — and, for a shared item, to every other member — as soon as they're online.
 */
class FirestoreArcRepository(private val firestore: FirebaseFirestore) {

    /**
     * What is currently wrong with sync, keyed by which listener or write said so.
     *
     * Keyed rather than a single value because there are now several sources at once: the shared
     * listener being rejected must not be cleared by the private one's next healthy snapshot, which
     * is exactly what a lone field did.
     */
    private val errors = MutableStateFlow<Map<String, String>>(emptyMap())

    /**
     * The first thing currently failing, or null while sync is healthy. A rejected listener used to
     * be rethrown into the collecting coroutine, which took the whole app down — a security rule
     * that hasn't been published yet is a reason to show a message, not to crash.
     */
    val syncError: Flow<String?> = errors.map { it.values.firstOrNull() }

    private fun report(source: String, message: String?) {
        errors.value = if (message == null) errors.value - source else errors.value + (source to message)
    }

    // Where every user's items already live, and invisible to them, so it keeps the old name: a
    // rename here would point the app at an empty collection and read as total data loss.
    private fun itemsCollection(uid: String) =
        firestore.collection("users").document(uid).collection("wishlist_items")

    /** Items shared between accounts, outside anybody's subtree because two people own them. */
    private fun sharedCollection() = firestore.collection("shared_items")

    /** Where this 할 일 is stored, which its member list decides. */
    private fun documentFor(uid: String, item: ArcItem) =
        if (item.isShared) sharedCollection().document(item.id)
        else itemsCollection(uid).document(item.id)

    /**
     * Both halves of the list, merged: the user's own items and the shared ones they are a member
     * of. Two listeners rather than one query because the two collections have nothing in common
     * but the document shape.
     *
     * Keyed by id with shared winning, so the brief moment during a share when the document exists
     * in both collections shows one row rather than two.
     */
    fun observeItems(uid: String): Flow<List<ArcItem>> =
        combine(
            observeQuery(itemsCollection(uid), "할 일"),
            observeQuery(sharedCollection().whereArrayContains("members", uid), "공유 할 일"),
        ) { own, shared ->
            val byId = LinkedHashMap<String, ArcItem>()
            own.forEach { byId[it.id] = it }
            shared.forEach { byId[it.id] = it }
            byId.values.toList()
        }

    private fun observeQuery(query: Query, what: String): Flow<List<ArcItem>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                report(what, error.describe(what))
                trySend(emptyList())
                return@addSnapshotListener
            }
            report(what, null)
            trySend(snapshot?.documents.orEmpty().map { it.toArcItem() })
        }
        awaitClose { registration.remove() }
    }

    suspend fun saveItem(uid: String, item: ArcItem) = guarded("할 일") {
        val data = item.toFirestoreMap()
        if (item.id.isBlank()) {
            // A 할 일 is always born private; sharing it is a separate, deliberate step.
            itemsCollection(uid).add(data).await()
        } else {
            documentFor(uid, item).set(data).await()
        }
    }

    suspend fun deleteItem(uid: String, item: ArcItem) = guarded("할 일") {
        if (item.id.isNotBlank()) documentFor(uid, item).delete().await()
    }

    /**
     * Shares a 할 일 with another Google account, named by its uid.
     *
     * The first share moves the document out of the owner's subtree into shared_items, under the
     * same id so a 세부항목 linked to it still resolves. One batch, so the item is never in both
     * places (two rows) or neither (lost) — which a write followed by a delete would allow.
     */
    suspend fun shareItem(uid: String, item: ArcItem, withUid: String) = guarded("공유") {
        if (item.id.isBlank()) return@guarded
        val shared = item.copy(
            ownerUid = if (item.isShared) item.ownerUid else uid,
            members = (listOf(uid) + item.members + withUid).distinct(),
        )
        val batch = firestore.batch()
        batch.set(sharedCollection().document(item.id), shared.toFirestoreMap())
        if (!item.isShared) batch.delete(itemsCollection(uid).document(item.id))
        batch.commit().await()
    }

    /**
     * Stops sharing. The owner takes the 할 일 back — it returns to their own subtree, keeping its
     * id — while anyone else simply drops out of the member list, leaving it with its owner.
     */
    suspend fun unshareItem(uid: String, item: ArcItem) = guarded("공유 중단") {
        if (!item.isShared || item.id.isBlank()) return@guarded
        val sharedRef = sharedCollection().document(item.id)
        if (item.ownerUid == uid) {
            val batch = firestore.batch()
            val reclaimed = item.copy(ownerUid = "", members = emptyList())
            batch.set(itemsCollection(uid).document(item.id), reclaimed.toFirestoreMap())
            batch.delete(sharedRef)
            batch.commit().await()
        } else {
            sharedRef.update("members", item.members - uid).await()
        }
    }

    /**
     * Runs a write and turns a rejection into [syncError] instead of an exception. Writes are
     * launched from click handlers, where an uncaught failure would take the app down for something
     * as recoverable as a rule that hasn't been published.
     */
    private suspend fun guarded(what: String, block: suspend () -> Unit) {
        runCatching { block() }
            .onSuccess { report(what, null) }
            .onFailure { failure ->
                report(
                    what,
                    (failure as? FirebaseFirestoreException)?.describe(what)
                        ?: "$what 저장 실패: ${failure.message}",
                )
            }
    }

    /**
     * For the Drive backup, which is this account's own data: shared items belong to whoever owns
     * them and are left out, so restoring never quietly makes a private copy of someone else's.
     */
    suspend fun getAllItemsOnce(uid: String): List<ArcItem> =
        itemsCollection(uid).get().await().documents.map { it.toArcItem() }

    // Category colors live in a single document rather than one per category: the whole set is
    // small, always read together, and rewritten as a unit whenever one color changes.
    private fun categoryColorsDoc(uid: String) =
        firestore.collection("users").document(uid).collection("settings").document("categoryColors")

    fun observeCategoryColors(uid: String): Flow<List<CategoryColorPref>> = callbackFlow {
        val registration = categoryColorsDoc(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Colors are decoration: without them the automatic ones still work, so the rest of
                // the app carries on and only the banner mentions it.
                report("분류 색", error.describe("분류 색"))
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

private fun DocumentSnapshot.toArcItem(): ArcItem {
    // Documents written before 완료일 was split into 종료일 + 완료 only have completedAt, which
    // meant "finished on this date" — read it as both the end date and the done flag.
    val legacyCompletedAt = getLong("completedAt")
    return ArcItem(
        id = id,
        title = getString("title") ?: "",
        memo = getString("memo"),
        subItems = readSubItems(),
        majorCategory = getString("majorCategory"),
        minorCategory = getString("minorCategory"),
        startedAt = getLong("startedAt") ?: 0L,
        endDate = getLong("endDate") ?: legacyCompletedAt,
        isDone = getBoolean("isDone") ?: (legacyCompletedAt != null),
        priority = (getLong("priority") ?: ArcItem.DEFAULT_PRIORITY.toLong()).toInt(),
        position = getLong("position") ?: 0L,
        ownerUid = getString("ownerUid") ?: "",
        members = readMembers(),
    )
}

/**
 * Who may see this 할 일. Absent on a private item, which is most of them, and read as empty —
 * which is also what says the document is private.
 */
@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.readMembers(): List<String> =
    (get("members") as? List<Any?>).orEmpty().mapNotNull { it as? String }.filter { it.isNotBlank() }

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.readSubItems(): List<SubItem> {
    val raw = get("subItems") as? List<Map<String, Any?>> ?: return emptyList()
    return raw.map { entry ->
        SubItem(
            title = entry["title"] as? String ?: "",
            done = entry["done"] as? Boolean ?: false,
            endDate = (entry["endDate"] as? Number)?.toLong(),
            linkedItemId = (entry["linkedItemId"] as? String)?.takeIf { it.isNotBlank() },
        )
    }
}

private fun ArcItem.toFirestoreMap(): Map<String, Any?> = mapOf(
    "title" to title,
    "memo" to memo,
    "subItems" to subItems.map {
        mapOf(
            "title" to it.title,
            "done" to it.done,
            "endDate" to it.endDate?.toStartOfDayMillis(),
            // Written on every save, including when null. saveItem replaces the whole document, so
            // a field this map leaves out is dropped from a 할 일 the moment it is edited — which
            // would quietly cut every link made on the web.
            "linkedItemId" to it.linkedItemId,
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
    // Written on every save so a shared 할 일 edited from either side keeps its member list: this
    // map replaces the whole document, and a field left out of it would end the share silently.
    "ownerUid" to ownerUid,
    "members" to members,
)
