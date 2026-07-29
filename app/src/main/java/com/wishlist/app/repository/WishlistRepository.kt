package com.wishlist.app.repository

import com.wishlist.app.data.CategoryColorPref
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

    /** Whatever is currently wrong with Firestore sync, or null while it's healthy. */
    fun observeSyncError(): Flow<String?> =
        firestoreRepository?.syncError ?: flowOf(null)

    fun observeCategoryColors(uid: String): Flow<List<CategoryColorPref>> =
        firestoreRepository?.observeCategoryColors(uid) ?: flowOf(emptyList())

    suspend fun saveCategoryColors(uid: String, prefs: List<CategoryColorPref>) {
        firestoreRepository?.saveCategoryColors(uid, prefs)
    }

    fun observeSortPreference(): Flow<SortPreference> =
        sortPreferenceDao.observe().map { it ?: SortPreference() }

    suspend fun setSort(sortField: SortField, ascending: Boolean) {
        sortPreferenceDao.upsert(SortPreference(sortField = sortField, ascending = ascending))
    }

    private fun itemsFlow(uid: String): Flow<List<WishlistItem>> =
        firestoreRepository?.observeItems(uid) ?: flowOf(emptyList())

    /**
     * Flattens items into one row per 세부항목 for the main table.
     *
     * The sort field decides which level of the (대/중분류) → 항목 → 세부항목 hierarchy is being
     * ordered. An 항목-level field ((대/중분류), 할 일, 우선순위, 시작일) moves the item as a single
     * unit and its 세부항목 ride along beneath it in their own order; a 세부항목-level field
     * (완료예정일, 세부항목 이름) orders the rows individually, which is what makes it possible to see
     * every step across the whole list by its due date.
     */
    fun observeRows(uid: String, hideCompleted: Flow<Boolean>): Flow<List<TableRow>> =
        combine(
            itemsFlow(uid),
            observeSortPreference(),
            hideCompleted,
        ) { items, preference, hideDone ->
            // Completed lines stay in the table struck through by default; hiding them is opt-in.
            val includeCompleted = !hideDone
            if (preference.sortField.keepsItemTogether) {
                groupedRows(items, preference, includeCompleted)
            } else {
                flatRows(items, preference, includeCompleted)
            }
        }

    /** 항목 line + its own 세부항목 lines, with the items themselves ordered by the sort field. */
    private fun groupedRows(
        items: List<WishlistItem>,
        preference: SortPreference,
        includeCompleted: Boolean,
    ): List<TableRow> {
        val comparator = itemComparator(preference.sortField)
        return items.sortedWith(if (preference.ascending) comparator else comparator.reversed())
            .flatMap { item ->
                val block = listOf(TableRow(item, subItem = null, subIndex = -1, grouped = true)) +
                    item.subItems.mapIndexed { index, sub -> TableRow(item, sub, index, grouped = true) }
                block.filter { includeCompleted || !it.isDone }
            }
    }

    /** One line per 세부항목 across every item, ordered on its own. */
    private fun flatRows(
        items: List<WishlistItem>,
        preference: SortPreference,
        includeCompleted: Boolean,
    ): List<TableRow> {
        val comparator = rowComparator(preference.sortField)
        return items.flatMap { item ->
            if (item.subItems.isEmpty()) {
                // Nothing to break out, so the 항목 stands in for itself instead of disappearing.
                listOf(TableRow(item, subItem = null, subIndex = -1))
            } else {
                item.subItems.mapIndexed { index, sub -> TableRow(item, sub, index) }
            }
        }
            .filter { includeCompleted || !it.isDone }
            .sortedWith(if (preference.ascending) comparator else comparator.reversed())
    }

    /** Orders whole 항목 for the 항목-level sort fields. */
    private fun itemComparator(field: SortField): Comparator<WishlistItem> {
        val collator = Collator.getInstance(Locale.KOREAN)
        val byField: Comparator<WishlistItem> = when (field) {
            // 대분류 and 중분류 are one key: 중분류 only breaks ties inside the same 대분류, so a
            // category is never split apart by the sort.
            SortField.CATEGORY -> Comparator { a, b ->
                val major = collator.compare(a.majorCategory.sortKey(), b.majorCategory.sortKey())
                if (major != 0) major
                else collator.compare(a.minorCategory.sortKey(), b.minorCategory.sortKey())
            }
            SortField.TITLE -> Comparator { a, b -> collator.compare(a.title, b.title) }
            SortField.PRIORITY -> compareBy { it.priority }
            SortField.START_DATE -> compareBy { it.startedAt }
            // Not 항목-level; handled by rowComparator.
            SortField.END_DATE, SortField.SUB_ITEM -> compareBy { it.position }
        }
        // Stable tail so items sharing a key keep a predictable order instead of shuffling on
        // every Firestore snapshot.
        return byField
            .thenComparator { a, b -> collator.compare(a.title, b.title) }
            .thenBy { it.position }
    }

    /** Orders individual 세부항목 rows for the 세부항목-level sort fields. */
    private fun rowComparator(field: SortField): Comparator<TableRow> {
        val collator = Collator.getInstance(Locale.KOREAN)
        // Rows with no date go last in ascending order rather than clumping at the top.
        val farFuture = Long.MAX_VALUE
        val byField: Comparator<TableRow> = when (field) {
            SortField.END_DATE -> compareBy { it.effectiveEndDate ?: farFuture }
            SortField.SUB_ITEM -> Comparator { a, b ->
                collator.compare(a.subItem?.title.orEmpty(), b.subItem?.title.orEmpty())
            }
            SortField.CATEGORY, SortField.TITLE, SortField.PRIORITY, SortField.START_DATE ->
                compareBy { it.item.position }
        }
        return byField
            .thenComparator { a, b -> collator.compare(a.item.title, b.item.title) }
            .thenBy { it.subIndex }
    }
}

/** Blank 대분류/중분류 sort after everything named, instead of leading the list. */
private fun String?.sortKey(): String = this?.takeIf { it.isNotBlank() } ?: "￿"
