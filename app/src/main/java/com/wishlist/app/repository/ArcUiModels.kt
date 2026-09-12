package com.wishlist.app.repository

import com.wishlist.app.data.ArcItem
import com.wishlist.app.data.CategoryColorPref
import com.wishlist.app.data.SortField
import com.wishlist.app.data.SubItem

/**
 * One line of the main table, and the reason the table can show two shapes at once.
 *
 * Management hierarchy is (대/중분류) → 항목 → 세부항목, and [grouped] says whether the current sort
 * preserved it. An 항목-level sort ((대/중분류), 할 일, 우선순위, 시작일) emits an 항목 line followed by
 * its own 세부항목 lines, so an item and its children always travel together — the shape the
 * previous card layout had. A 세부항목-level sort (완료예정일, 세부항목 이름) emits one line per
 * 세부항목 across the whole list, each repeating its 항목 for context.
 *
 * [subItem] is null on an 항목 line; an item with no 세부항목 at all is always just that one line.
 */
data class TableRow(
    val item: ArcItem,
    val subItem: SubItem?,
    /** Index into the item's subItems, or -1 for the 항목 line. */
    val subIndex: Int,
    /** True when this line is part of an 항목 block rather than a standalone 세부항목 line. */
    val grouped: Boolean = false,
) {
    val isItemRow: Boolean get() = subIndex < 0

    val rowKey: String get() = if (isItemRow) "${item.id}#item" else "${item.id}#$subIndex"

    /** The 완료예정일 this line is judged by: the 세부항목's own, else the item's 최종 종료일. */
    val effectiveEndDate: Long? get() = subItem?.endDate ?: item.endDate

    /** A line counts as done when its own 세부항목 is ticked, or the whole item is complete. */
    val isDone: Boolean get() = item.isDone || (subItem?.done ?: false)
}

/**
 * One 항목 with the lines belonging to it, for the list view. [rows] is what survived the current
 * filter and keeps the order the sort put it in, so hiding completed lines or sorting by 완료예정일
 * changes what's under an item without a second query.
 */
data class ItemGroup(
    val itemRow: TableRow,
    val subRows: List<TableRow>,
) {
    val item get() = itemRow.item
    val key: String get() = item.id.ifBlank { itemRow.rowKey }

    /** The date the item as a whole is judged by: its own 최종 종료일, else its earliest step. */
    val headlineEndDate: Long?
        get() = item.endDate ?: subRows.mapNotNull { it.effectiveEndDate }.minOrNull()
}

/**
 * Collapses table lines back into 항목 blocks, in the order the items first appear. Under a
 * 세부항목-level sort that means an item ranks by its most urgent step, which is the same order the
 * table shows, read one level up.
 */
fun List<TableRow>.toItemGroups(): List<ItemGroup> {
    val order = LinkedHashMap<String, MutableList<TableRow>>()
    forEach { row -> order.getOrPut(row.item.id.ifBlank { row.rowKey }) { mutableListOf() }.add(row) }
    return order.values.mapNotNull { rows ->
        // An item line is only emitted by the grouped sorts; otherwise stand one in from a 세부항목
        // line so the block still has a head to show the 항목's own title and 최종 종료일.
        val head = rows.firstOrNull { it.isItemRow } ?: rows.firstOrNull() ?: return@mapNotNull null
        ItemGroup(itemRow = head, subRows = rows.filterNot { it.isItemRow })
    }
}

data class ArcUiState(
    val rows: List<TableRow> = emptyList(),
    val sortField: SortField = SortField.END_DATE,
    val ascending: Boolean = true,
    /** Completed lines are hidden by default; unchecking shows them struck through. */
    val hideCompleted: Boolean = true,
    val majorCategories: List<String> = emptyList(),
    /** Colors the user picked per 대분류/중분류; anything not listed falls back to an automatic one. */
    val categoryColors: List<CategoryColorPref> = emptyList(),
    /** Set while Firestore sync is failing — shown as a banner rather than taken as fatal. */
    val syncError: String? = null,
    val isLoading: Boolean = true,
)
