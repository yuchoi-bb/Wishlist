package com.wishlist.app.repository

import com.wishlist.app.data.CategorySortPref
import com.wishlist.app.data.CategorySortPrefDao
import com.wishlist.app.data.FirestoreWishlistRepository
import com.wishlist.app.data.SortField
import com.wishlist.app.data.SubItem
import com.wishlist.app.data.WishlistItem
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class WishlistRepository(
    // Null before google-services.json/Firebase project setup is complete.
    private val firestoreRepository: FirestoreWishlistRepository?,
    private val sortPrefDao: CategorySortPrefDao,
) {
    fun observeMinorCategories(uid: String, major: String): Flow<List<String>> =
        itemsFlow(uid).map { items ->
            items.filter { it.majorCategory == major }
                .mapNotNull { it.minorCategory?.takeIf { minor -> minor.isNotBlank() } }
                .distinct()
                .sorted()
        }

    suspend fun saveItem(uid: String, item: WishlistItem) {
        firestoreRepository?.saveItem(uid, item)
    }

    suspend fun deleteItem(uid: String, item: WishlistItem) {
        firestoreRepository?.deleteItem(uid, item)
    }

    suspend fun setSortForCategory(categoryKey: String, field: SortField, ascending: Boolean) {
        sortPrefDao.upsert(CategorySortPref(categoryKey, field, ascending))
    }

    /**
     * Persists a hand-arranged order and pins the group to it — leaving the group on a computed
     * sort would just re-sort the items and throw the drag away on the next emission.
     */
    suspend fun applyManualOrder(uid: String, categoryKey: String, orderedIds: List<String>) {
        firestoreRepository?.updatePositions(uid, orderedIds)
        val existing = sortPrefDao.get(categoryKey) ?: CategorySortPref(categoryKey)
        // copy(), not a fresh row: the group's own groupPosition must survive an item reorder.
        sortPrefDao.upsert(existing.copy(sortField = SortField.MANUAL, ascending = true))
    }

    suspend fun updateSubItems(uid: String, item: WishlistItem, subItems: List<SubItem>) {
        firestoreRepository?.saveItem(uid, item.copy(subItems = subItems))
    }

    /** Stores the dragged order of the category groups, keeping each group's own sort setting. */
    suspend fun applyGroupOrder(orderedCategoryKeys: List<String>) {
        orderedCategoryKeys.forEachIndexed { index, key ->
            val existing = sortPrefDao.get(key) ?: CategorySortPref(key)
            sortPrefDao.upsert(existing.copy(groupPosition = index.toLong()))
        }
    }

    private fun itemsFlow(uid: String): Flow<List<WishlistItem>> =
        firestoreRepository?.observeItems(uid) ?: flowOf(emptyList())

    /** Combines real-time Firestore items, per-category sort prefs and the show-completed toggle
     * into grouped, sorted UI state. */
    fun observeGroups(
        uid: String,
        showCompleted: Flow<Boolean>,
    ): Flow<List<CategoryGroup>> =
        combine(
            itemsFlow(uid),
            sortPrefDao.observeAll(),
            showCompleted,
        ) { items, prefs, includeCompleted ->
            val prefsByKey = prefs.associateBy { it.categoryKey }
            val now = System.currentTimeMillis()

            val filtered = items.filter { includeCompleted || !it.isCompleted }

            val keyComparator = categoryKeyComparator()
            filtered
                .groupBy { it.categoryKey }
                .map { (key, groupItems) ->
                    val pref = prefsByKey[key] ?: CategorySortPref(key)
                    CategoryGroup(
                        categoryKey = key,
                        majorCategory = groupItems.first().majorCategory,
                        minorCategory = groupItems.first().minorCategory,
                        sortField = pref.sortField,
                        ascending = pref.ascending,
                        groupPosition = pref.groupPosition,
                        items = sortGroupItems(groupItems, pref.sortField, pref.ascending, now),
                    )
                }
                // Hand-arranged groups first in the order they were dragged into; everything else
                // keeps the alphabetical fallback (with 미분류 last).
                .sortedWith(
                    compareBy<CategoryGroup> { it.groupPosition }
                        .thenComparator { a, b -> keyComparator.compare(a.categoryKey, b.categoryKey) },
                )
        }

    private fun categoryKeyComparator(): Comparator<String> {
        val collator = Collator.getInstance(Locale.KOREAN)
        return Comparator { a, b ->
            when {
                a == WishlistItem.UNCATEGORIZED_KEY && b == WishlistItem.UNCATEGORIZED_KEY -> 0
                a == WishlistItem.UNCATEGORIZED_KEY -> 1
                b == WishlistItem.UNCATEGORIZED_KEY -> -1
                else -> collator.compare(a, b)
            }
        }
    }

    /** In-progress (no 완료일) items always sink to the bottom, regardless of chosen direction. */
    private fun sortGroupItems(
        items: List<WishlistItem>,
        field: SortField,
        ascending: Boolean,
        now: Long,
    ): List<WishlistItem> {
        // Manual order is exactly what the user dragged: no direction flip, and no sinking of
        // in-progress items, since either would move rows away from where they were dropped.
        if (field == SortField.MANUAL) return items.sortedBy { it.position }

        val fieldComparator = fieldComparator(field, now)
        val directional = if (ascending) fieldComparator else fieldComparator.reversed()
        val (completed, inProgress) = items.partition { it.isCompleted }
        return completed.sortedWith(directional) + inProgress.sortedWith(directional)
    }

    private fun fieldComparator(field: SortField, now: Long): Comparator<WishlistItem> =
        when (field) {
            // Items with no 종료일 sort as if theirs were today, keeping them among current work.
            SortField.COMPLETED_AT -> Comparator.comparingLong { it.endDate ?: now }
            SortField.STARTED_AT -> Comparator.comparingLong { it.startedAt }
            SortField.DURATION -> Comparator.comparingLong { it.ponderedDurationMillis(now) }
            SortField.TITLE -> {
                val collator = Collator.getInstance(Locale.KOREAN)
                Comparator { a, b -> collator.compare(a.title, b.title) }
            }
            SortField.MANUAL -> Comparator.comparingLong { it.position }
        }
}
