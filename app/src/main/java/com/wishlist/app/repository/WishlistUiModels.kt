package com.wishlist.app.repository

import com.wishlist.app.data.SortField
import com.wishlist.app.data.StatusFilter
import com.wishlist.app.data.WishlistItem

/** One category group (대분류/중분류 pair, or the uncategorized bucket) with its own sort setting. */
data class CategoryGroup(
    val categoryKey: String,
    val majorCategory: String?,
    val minorCategory: String?,
    val sortField: SortField,
    val ascending: Boolean,
    val items: List<WishlistItem>,
)

data class WishlistUiState(
    val groups: List<CategoryGroup> = emptyList(),
    val searchQuery: String = "",
    val statusFilter: StatusFilter = StatusFilter.ALL,
    val majorCategories: List<String> = emptyList(),
    val isLoading: Boolean = true,
)
