package com.wishlist.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wishlist.app.data.WishlistItem
import com.wishlist.app.ui.screens.AddEditItemDialog
import com.wishlist.app.ui.screens.SettingsScreen
import com.wishlist.app.ui.screens.SignInScreen
import com.wishlist.app.ui.screens.WishlistTableScreen

@Composable
fun WishlistRoot(viewModel: WishlistViewModel = viewModel()) {
    val currentUser by viewModel.authManager.currentUser.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showAddEdit by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<WishlistItem?>(null) }

    if (currentUser == null) {
        SignInScreen(authManager = viewModel.authManager)
        return
    }

    if (showSettings) {
        SettingsScreen(authManager = viewModel.authManager, onBack = { showSettings = false })
        return
    }

    WishlistTableScreen(
        uiState = uiState,
        onShowCompletedChange = viewModel::onShowCompletedChange,
        onSortSelected = viewModel::onSortSelected,
        onToggleRowDone = { row ->
            // A row backed by a 세부항목 ticks that step; an item with none ticks the item itself.
            if (row.subIndex >= 0) {
                viewModel.toggleSubItem(row.item, row.subIndex)
            } else {
                viewModel.toggleItemDone(row.item)
            }
        },
        onRowClick = { row ->
            editingItem = row.item
            showAddEdit = true
        },
        onAddClick = {
            editingItem = null
            showAddEdit = true
        },
        onSettingsClick = { showSettings = true },
    )

    if (showAddEdit) {
        AddEditItemDialog(
            viewModel = viewModel,
            majorCategorySuggestions = uiState.majorCategories,
            categoryColors = uiState.categoryColors,
            editingItem = editingItem,
            onDismiss = { showAddEdit = false },
            onSave = { item ->
                viewModel.saveItem(item)
                showAddEdit = false
            },
            onDelete = { item ->
                viewModel.deleteItem(item)
                showAddEdit = false
            },
        )
    }
}
