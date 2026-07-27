package com.wishlist.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-category sort setting. Each category group (major/minor pair, or the
 * "uncategorized" bucket) remembers its own sort field + direction across app restarts,
 * independently of every other group.
 */
@Entity(tableName = "category_sort_prefs")
data class CategorySortPref(
    @PrimaryKey val categoryKey: String,
    val sortField: SortField = SortField.COMPLETED_AT,
    val ascending: Boolean = true,
)
