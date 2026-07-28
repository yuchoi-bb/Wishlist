package com.wishlist.app.repository

import com.wishlist.app.data.CategoryColorPref
import com.wishlist.app.data.SortField
import com.wishlist.app.data.SubItem
import com.wishlist.app.data.WishlistItem

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
    val item: WishlistItem,
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

data class WishlistUiState(
    val rows: List<TableRow> = emptyList(),
    val sortField: SortField = SortField.END_DATE,
    val ascending: Boolean = true,
    /** Completed rows stay in the table but are hidden until the user asks to see them. */
    val showCompleted: Boolean = false,
    val majorCategories: List<String> = emptyList(),
    /** Colors the user picked per 대분류/중분류; anything not listed falls back to an automatic one. */
    val categoryColors: List<CategoryColorPref> = emptyList(),
    val isLoading: Boolean = true,
)
