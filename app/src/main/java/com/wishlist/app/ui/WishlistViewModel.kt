package com.wishlist.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.wishlist.app.auth.AuthManager
import com.wishlist.app.data.CategoryColorPref
import com.wishlist.app.data.FirestoreWishlistRepository
import com.wishlist.app.data.SortField
import com.wishlist.app.data.WishlistDatabase
import com.wishlist.app.data.WishlistItem
import com.wishlist.app.repository.WishlistRepository
import com.wishlist.app.repository.WishlistUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val repository = WishlistRepository(firestoreRepository, db.sortPreferenceDao())

    // On by default: what's left to do is what the table is for. Unchecking brings the finished
    // lines back, struck through, without them ever having been deleted.
    private val hideCompleted = MutableStateFlow(true)

    val uiState: StateFlow<WishlistUiState> = authManager.currentUser
        .flatMapLatest { user ->
            val uid = user?.uid
            if (uid == null) {
                flowOf(WishlistUiState(isLoading = false))
            } else {
                combine(
                    repository.observeRows(uid, hideCompleted),
                    repository.observeSortPreference(),
                    hideCompleted,
                    repository.observeCategoryColors(uid),
                    repository.observeSyncError(),
                ) { rows, preference, hideDone, colors, syncError ->
                    WishlistUiState(
                        rows = rows,
                        sortField = preference.sortField,
                        ascending = preference.ascending,
                        hideCompleted = hideDone,
                        majorCategories = rows.mapNotNull { it.item.majorCategory }.distinct().sorted(),
                        categoryColors = colors,
                        syncError = syncError,
                        isLoading = false,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WishlistUiState())

    fun onHideCompletedChange(hide: Boolean) {
        hideCompleted.value = hide
    }

    /** Choosing the field already in use flips direction; a different field starts ascending. */
    fun onSortSelected(field: SortField) {
        viewModelScope.launch {
            val current = repository.observeSortPreference().first()
            val ascending = if (current.sortField == field) !current.ascending else true
            repository.setSort(field, ascending)
        }
    }

    /**
     * Sets the color for one 대분류 (minor null) or one 중분류 inside it. A null [paletteIndex] drops
     * the pref, putting that category back on its automatic color.
     */
    fun setCategoryColor(major: String, minor: String?, paletteIndex: Int?) {
        val uid = authManager.currentUser.value?.uid ?: return
        if (major.isBlank()) return
        val others = uiState.value.categoryColors.filterNot { it.major == major && it.minor == minor }
        val updated = if (paletteIndex == null) {
            others
        } else {
            others + CategoryColorPref(major = major, minor = minor, paletteIndex = paletteIndex)
        }
        viewModelScope.launch { repository.saveCategoryColors(uid, updated) }
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

    fun toggleSubItem(item: WishlistItem, index: Int) {
        val uid = authManager.currentUser.value?.uid ?: return
        val subItem = item.subItems.getOrNull(index) ?: return
        val updated = item.subItems.toMutableList().apply { this[index] = subItem.copy(done = !subItem.done) }
        viewModelScope.launch { repository.updateSubItems(uid, item, updated) }
    }

    /** For the item-only row, where there is no 세부항목 to tick. */
    fun toggleItemDone(item: WishlistItem) {
        val uid = authManager.currentUser.value?.uid ?: return
        viewModelScope.launch { repository.saveItem(uid, item.copy(isDone = !item.isDone)) }
    }
}
