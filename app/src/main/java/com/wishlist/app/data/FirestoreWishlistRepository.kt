package com.wishlist.app.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
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

    suspend fun getAllItemsOnce(uid: String): List<WishlistItem> =
        itemsCollection(uid).get().await().documents.map { it.toWishlistItem() }
}

private fun DocumentSnapshot.toWishlistItem(): WishlistItem = WishlistItem(
    id = id,
    title = getString("title") ?: "",
    majorCategory = getString("majorCategory"),
    minorCategory = getString("minorCategory"),
    startedAt = getLong("startedAt") ?: 0L,
    completedAt = getLong("completedAt"),
)

private fun WishlistItem.toFirestoreMap(): Map<String, Any?> = mapOf(
    "title" to title,
    "majorCategory" to majorCategory,
    "minorCategory" to minorCategory,
    "startedAt" to startedAt,
    "completedAt" to completedAt,
)
