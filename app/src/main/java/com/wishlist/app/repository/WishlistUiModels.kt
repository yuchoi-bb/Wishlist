package com.wishlist.app.repository

import com.wishlist.app.data.SortField
import com.wishlist.app.data.WishlistItem

/** One category group (대분류/중분류 pair, or the uncategorized bucket) with its own sort setting. */
data class CategoryGroup(
    val categoryKey: String,
    val majorCategory: String?,
    val minorCategory: String?,
    val sortField: SortField,
    val ascending: Boolean,
    /** Rank among the other groups once dragged; unset groups fall back to alphabetical. */
    val groupPosition: Long,
    val items: List<WishlistItem>,
)

data class WishlistUiState(
    val groups: List<CategoryGroup> = emptyList(),
    /** Completed items stay in the list but are hidden until the user asks to see them. */
    val showCompleted: Boolean = false,
    val majorCategories: List<String> = emptyList(),
    val isLoading: Boolean = true,
)
