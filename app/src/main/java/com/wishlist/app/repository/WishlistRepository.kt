package com.wishlist.app.repository

import com.wishlist.app.data.CategorySortPref
import com.wishlist.app.data.CategorySortPrefDao
import com.wishlist.app.data.SortField
import com.wishlist.app.data.StatusFilter
import com.wishlist.app.data.WishlistDao
import com.wishlist.app.data.WishlistItem
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class WishlistRepository(
    private val wishlistDao: WishlistDao,
    private val sortPrefDao: CategorySortPrefDao,
) {
    fun observeMinorCategories(major: String): Flow<List<String>> =
        wishlistDao.observeMinorCategories(major)

    suspend fun saveItem(item: WishlistItem) {
        wishlistDao.upsert(item)
    }

    suspend fun deleteItem(item: WishlistItem) {
        wishlistDao.delete(item)
    }

    suspend fun setCompleted(item: WishlistItem, completedAt: Long?) {
        wishlistDao.update(item.copy(completedAt = completedAt))
    }

    suspend fun setSortForCategory(categoryKey: String, field: SortField, ascending: Boolean) {
        sortPrefDao.upsert(CategorySortPref(categoryKey, field, ascending))
    }

    /** Combines raw items, per-category sort prefs, search query and status filter into grouped, sorted UI state. */
    fun observeGroups(
        searchQuery: Flow<String>,
        statusFilter: Flow<StatusFilter>,
    ): Flow<List<CategoryGroup>> =
        combine(
            wishlistDao.observeAll(),
            sortPrefDao.observeAll(),
            searchQuery,
            statusFilter,
        ) { items, prefs, query, filter ->
            val prefsByKey = prefs.associateBy { it.categoryKey }
            val now = System.currentTimeMillis()

            val filtered = items.filter { item ->
                val matchesStatus = when (filter) {
                    StatusFilter.ALL -> true
                    StatusFilter.IN_PROGRESS -> !item.isCompleted
                    StatusFilter.COMPLETED -> item.isCompleted
                }
                val matchesQuery = query.isBlank() || item.title.contains(query, ignoreCase = true)
                matchesStatus && matchesQuery
            }

            filtered
                .groupBy { it.categoryKey }
                .toSortedMap(categoryKeyComparator())
                .map { (key, groupItems) ->
                    val pref = prefsByKey[key] ?: CategorySortPref(key)
                    CategoryGroup(
                        categoryKey = key,
                        majorCategory = groupItems.first().majorCategory,
                        minorCategory = groupItems.first().minorCategory,
                        sortField = pref.sortField,
                        ascending = pref.ascending,
                        items = sortGroupItems(groupItems, pref.sortField, pref.ascending, now),
                    )
                }
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

    /** In-progress (no 완료일자) items always sink to the bottom, regardless of chosen direction. */
    private fun sortGroupItems(
        items: List<WishlistItem>,
        field: SortField,
        ascending: Boolean,
        now: Long,
    ): List<WishlistItem> {
        val fieldComparator = fieldComparator(field, now)
        val directional = if (ascending) fieldComparator else fieldComparator.reversed()
        val (completed, inProgress) = items.partition { it.isCompleted }
        return completed.sortedWith(directional) + inProgress.sortedWith(directional)
    }

    private fun fieldComparator(field: SortField, now: Long): Comparator<WishlistItem> =
        when (field) {
            SortField.COMPLETED_AT -> Comparator.comparingLong { it.completedAt ?: now }
            SortField.STARTED_AT -> Comparator.comparingLong { it.startedAt }
            SortField.DURATION -> Comparator.comparingLong { it.ponderedDurationMillis(now) }
            SortField.TITLE -> {
                val collator = Collator.getInstance(Locale.KOREAN)
                Comparator { a, b -> collator.compare(a.title, b.title) }
            }
        }
}
