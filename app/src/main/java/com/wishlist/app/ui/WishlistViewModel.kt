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
import com.wishlist.app.util.todayStartOfDayMillis
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

    private val statusFilter = MutableStateFlow(StatusFilter.ALL)

    val uiState: StateFlow<WishlistUiState> = authManager.currentUser
        .flatMapLatest { user ->
            val uid = user?.uid
            if (uid == null) {
                flowOf(WishlistUiState(isLoading = false))
            } else {
                combine(
                    repository.observeGroups(uid, statusFilter),
                    statusFilter,
                ) { groups, filter ->
                    WishlistUiState(
                        groups = groups,
                        statusFilter = filter,
                        majorCategories = groups.mapNotNull { it.majorCategory }.distinct().sorted(),
                        isLoading = false,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WishlistUiState())

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
            repository.setCompleted(uid, item, if (item.isCompleted) null else todayStartOfDayMillis())
        }
    }

    fun setCompletedAt(item: WishlistItem, completedAt: Long?) {
        val uid = authManager.currentUser.value?.uid ?: return
        viewModelScope.launch { repository.setCompleted(uid, item, completedAt) }
    }

    fun toggleSubItem(item: WishlistItem, index: Int) {
        val uid = authManager.currentUser.value?.uid ?: return
        val subItem = item.subItems.getOrNull(index) ?: return
        val updated = item.subItems.toMutableList().apply { this[index] = subItem.copy(done = !subItem.done) }
        viewModelScope.launch { repository.updateSubItems(uid, item, updated) }
    }

    fun moveSubItem(item: WishlistItem, from: Int, to: Int) {
        val uid = authManager.currentUser.value?.uid ?: return
        if (from !in item.subItems.indices || to !in item.subItems.indices) return
        val updated = item.subItems.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch { repository.updateSubItems(uid, item, updated) }
    }

    /** Commits a finished drag: stores the new ranks and pins that group to 직접 지정 order. */
    fun applyManualOrder(categoryKey: String, orderedIds: List<String>) {
        val uid = authManager.currentUser.value?.uid ?: return
        viewModelScope.launch { repository.applyManualOrder(uid, categoryKey, orderedIds) }
    }
}
