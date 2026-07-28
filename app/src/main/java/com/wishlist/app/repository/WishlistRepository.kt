package com.wishlist.app.repository

import com.wishlist.app.data.FirestoreWishlistRepository
import com.wishlist.app.data.SortField
import com.wishlist.app.data.SortPreference
import com.wishlist.app.data.SortPreferenceDao
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
    private val sortPreferenceDao: SortPreferenceDao,
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

    suspend fun updateSubItems(uid: String, item: WishlistItem, subItems: List<SubItem>) {
        firestoreRepository?.saveItem(uid, item.copy(subItems = subItems))
    }

    fun observeSortPreference(): Flow<SortPreference> =
        sortPreferenceDao.observe().map { it ?: SortPreference() }

    suspend fun setSort(sortField: SortField, ascending: Boolean) {
        sortPreferenceDao.upsert(SortPreference(sortField = sortField, ascending = ascending))
    }

    private fun itemsFlow(uid: String): Flow<List<WishlistItem>> =
        firestoreRepository?.observeItems(uid) ?: flowOf(emptyList())

    /** Flattens items into one row per 세부항목 and sorts the whole table by the chosen field. */
    fun observeRows(uid: String, showCompleted: Flow<Boolean>): Flow<List<TableRow>> =
        combine(
            itemsFlow(uid),
            observeSortPreference(),
            showCompleted,
        ) { items, preference, includeCompleted ->
            val rows = items.flatMap { item ->
                if (item.subItems.isEmpty()) {
                    listOf(TableRow(item, subItem = null, subIndex = -1))
                } else {
                    item.subItems.mapIndexed { index, sub -> TableRow(item, sub, index) }
                }
            }

            val visible = rows.filter { includeCompleted || !it.isDone }
            val comparator = rowComparator(preference.sortField)
            visible.sortedWith(if (preference.ascending) comparator else comparator.reversed())
        }

    private fun rowComparator(field: SortField): Comparator<TableRow> {
        val collator = Collator.getInstance(Locale.KOREAN)
        // Rows with nothing in the sorted field go last either way rather than clumping at the top.
        val farFuture = Long.MAX_VALUE
        return when (field) {
            SortField.END_DATE -> compareBy { it.effectiveEndDate ?: farFuture }
            SortField.PRIORITY -> compareBy { it.item.priority }
            SortField.START_DATE -> compareBy { it.item.startedAt }
            SortField.MAJOR_CATEGORY -> Comparator { a, b ->
                collator.compare(a.item.majorCategory.orEmpty(), b.item.majorCategory.orEmpty())
            }
            SortField.MINOR_CATEGORY -> Comparator { a, b ->
                collator.compare(a.item.minorCategory.orEmpty(), b.item.minorCategory.orEmpty())
            }
            SortField.SUB_ITEM -> Comparator { a, b ->
                collator.compare(a.subItem?.title.orEmpty(), b.subItem?.title.orEmpty())
            }
            SortField.TITLE -> Comparator { a, b -> collator.compare(a.item.title, b.item.title) }
        }
    }
}
