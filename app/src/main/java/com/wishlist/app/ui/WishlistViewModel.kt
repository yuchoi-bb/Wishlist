package com.wishlist.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.wishlist.app.auth.AuthManager
import com.wishlist.app.data.FirestoreWishlistRepository
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WishlistViewModel(application: Application) : AndroidViewModel(application) {

    val authManager = AuthManager(application)

    private val db = WishlistDatabase.getInstance(application)

    // Null before google-services.json is added: there's no default FirebaseApp yet, and
    // FirebaseFirestore.getInstance() throws IllegalStateException in that case.
    private val firestoreRepository = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
        ?.let { FirestoreWishlistRepository(it) }
    private val repository = WishlistRepository(firestoreRepository, db.categorySortPrefDao())

    private val searchQuery = MutableStateFlow("")
    private val statusFilter = MutableStateFlow(StatusFilter.ALL)

    val uiState: StateFlow<WishlistUiState> = authManager.currentUser
        .flatMapLatest { user ->
            val uid = user?.uid
            if (uid == null) {
                flowOf(WishlistUiState(isLoading = false))
            } else {
                combine(
                    repository.observeGroups(uid, searchQuery, statusFilter),
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
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WishlistUiState())

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

    fun observeMinorCategories(major: String): Flow<List<String>> {
        val uid = authManager.currentUser.value?.uid ?: return flowOf(emptyList())
        return repository.observeMinorCategories(uid, major)
    }

    fun saveItem(item: WishlistItem) {
        val uid = authManager.currentUser.value?.uid ?: return
        viewModelScope.launch { repository.saveItem(uid, item) }
    }

    fun deleteItem(item: WishlistItem) {
        val uid = authManager.currentUser.value?.uid ?: return
        viewModelScope.launch { repository.deleteItem(uid, item) }
    }

    fun toggleCompleted(item: WishlistItem) {
        val uid = authManager.currentUser.value?.uid ?: return
        viewModelScope.launch {
            repository.setCompleted(uid, item, if (item.isCompleted) null else System.currentTimeMillis())
        }
    }

    fun setCompletedAt(item: WishlistItem, completedAt: Long?) {
        val uid = authManager.currentUser.value?.uid ?: return
        viewModelScope.launch { repository.setCompleted(uid, item, completedAt) }
    }
}
