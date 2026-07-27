package com.wishlist.app.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.wishlist.app.util.toStartOfDayMillis
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Real-time item storage backed by Firestore, scoped to users/{uid}/wishlist_items so every
 * signed-in user only ever sees their own data. Firestore's own client cache gives offline
 * reads/writes for free; changes made on one device are pushed to every other listening device
 * as soon as they're online.
 */
class FirestoreWishlistRepository(private val firestore: FirebaseFirestore) {

    private fun itemsCollection(uid: String) =
        firestore.collection("users").document(uid).collection("wishlist_items")

    fun observeItems(uid: String): Flow<List<WishlistItem>> = callbackFlow {
        val registration = itemsCollection(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents.orEmpty().map { it.toWishlistItem() })
        }
        awaitClose { registration.remove() }
    }

    suspend fun saveItem(uid: String, item: WishlistItem) {
        val data = item.toFirestoreMap()
        if (item.id.isBlank()) {
            itemsCollection(uid).add(data).await()
        } else {
            itemsCollection(uid).document(item.id).set(data).await()
        }
    }

    suspend fun deleteItem(uid: String, item: WishlistItem) {
        if (item.id.isBlank()) return
        itemsCollection(uid).document(item.id).delete().await()
    }

    /**
     * Writes the new manual ranks for one reordered group as a single batch — a drag produces many
     * intermediate swaps, so committing once at the end keeps it to one round trip.
     */
    suspend fun updatePositions(uid: String, orderedIds: List<String>) {
        val collection = itemsCollection(uid)
        val batch = firestore.batch()
        orderedIds.forEachIndexed { index, id ->
            if (id.isNotBlank()) {
                batch.update(collection.document(id), "position", index.toLong())
            }
        }
        batch.commit().await()
    }

    suspend fun getAllItemsOnce(uid: String): List<WishlistItem> =
        itemsCollection(uid).get().await().documents.map { it.toWishlistItem() }
}

private fun DocumentSnapshot.toWishlistItem(): WishlistItem = WishlistItem(
    id = id,
    title = getString("title") ?: "",
    memo = getString("memo"),
    subItems = readSubItems(),
    majorCategory = getString("majorCategory"),
    minorCategory = getString("minorCategory"),
    startedAt = getLong("startedAt") ?: 0L,
    completedAt = getLong("completedAt"),
    position = getLong("position") ?: 0L,
)

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.readSubItems(): List<SubItem> {
    val raw = get("subItems") as? List<Map<String, Any?>> ?: return emptyList()
    return raw.map { entry ->
        SubItem(
            title = entry["title"] as? String ?: "",
            done = entry["done"] as? Boolean ?: false,
        )
    }
}

private fun WishlistItem.toFirestoreMap(): Map<String, Any?> = mapOf(
    "title" to title,
    "memo" to memo,
    "subItems" to subItems.map { mapOf("title" to it.title, "done" to it.done) },
    "majorCategory" to majorCategory,
    "minorCategory" to minorCategory,
    // Normalized on every write, so items created before 시작일/완료일 became date-only (and any
    // restored from an older backup) get cleaned up the next time they're saved.
    "startedAt" to startedAt.toStartOfDayMillis(),
    "completedAt" to completedAt?.toStartOfDayMillis(),
    "position" to position,
)
