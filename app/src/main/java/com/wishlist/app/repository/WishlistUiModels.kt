package com.wishlist.app.repository

import com.wishlist.app.data.SortField
import com.wishlist.app.data.SubItem
import com.wishlist.app.data.WishlistItem

/**
 * One line of the main table. Each 세부항목 gets its own row, repeating its item's 대분류/중분류/
 * 할 일 alongside it; an item with no 세부항목 still gets a single row with [subItem] null so it
 * doesn't vanish from the table.
 */
data class TableRow(
    val item: WishlistItem,
    val subItem: SubItem?,
    /** Index into the item's subItems, or -1 for the item-only row. */
    val subIndex: Int,
) {
    val rowKey: String get() = "${item.id}#$subIndex"

    /** The deadline this row is judged by: the 세부항목's own, else the item's 최종 종료일. */
    val effectiveEndDate: Long? get() = subItem?.endDate ?: item.endDate

    /** A row counts as done when its own 세부항목 is ticked, or the whole item is complete. */
    val isDone: Boolean get() = item.isDone || (subItem?.done ?: false)
}

data class WishlistUiState(
    val rows: List<TableRow> = emptyList(),
    val sortField: SortField = SortField.END_DATE,
    val ascending: Boolean = true,
    /** Completed rows stay in the table but are hidden until the user asks to see them. */
    val showCompleted: Boolean = false,
    val majorCategories: List<String> = emptyList(),
    val isLoading: Boolean = true,
)
