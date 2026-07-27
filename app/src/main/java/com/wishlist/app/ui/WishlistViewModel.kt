package com.wishlist.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wishlist.app.data.SortField
import com.wishlist.app.data.StatusFilter
import com.wishlist.app.data.WishlistDatabase
import com.wishlist.app.data.WishlistItem
import com.wishlist.app.repository.WishlistRepository
import com.wishlist.app.repository.WishlistUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WishlistViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WishlistDatabase.getInstance(application)
    private val repository = WishlistRepository(db.wishlistDao(), db.categorySortPrefDao())

    private val searchQuery = MutableStateFlow("")
    private val statusFilter = MutableStateFlow(StatusFilter.ALL)

    val uiState: StateFlow<WishlistUiState> = combine(
        repository.observeGroups(searchQuery, statusFilter),
        searchQuery,
        statusFilter,
    ) { groups, query, filter ->
        WishlistUiState(
            groups = groups,
            searchQuery = query,
            statusFilter = filter,
            majorCategories = groups.mapNotNull { it.majorCategory }.distinct().sorted(),
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WishlistUiState())

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onStatusFilterChange(filter: StatusFilter) {
        statusFilter.value = filter
    }

    /** Tapping the same field again flips direction; tapping a different field switches to it (ascending). */
    fun onSortChange(categoryKey: String, field: SortField, currentField: SortField, currentAscending: Boolean) {
        val newAscending = if (field == currentField) !currentAscending else true
        viewModelScope.launch {
            repository.setSortForCategory(categoryKey, field, newAscending)
        }
    }

    fun observeMinorCategories(major: String): Flow<List<String>> =
        repository.observeMinorCategories(major)

    fun saveItem(item: WishlistItem) {
        viewModelScope.launch { repository.saveItem(item) }
    }

    fun deleteItem(item: WishlistItem) {
        viewModelScope.launch { repository.deleteItem(item) }
    }

    fun toggleCompleted(item: WishlistItem) {
        viewModelScope.launch {
            repository.setCompleted(item, if (item.isCompleted) null else System.currentTimeMillis())
        }
    }

    fun setCompletedAt(item: WishlistItem, completedAt: Long?) {
        viewModelScope.launch { repository.setCompleted(item, completedAt) }
    }
}
