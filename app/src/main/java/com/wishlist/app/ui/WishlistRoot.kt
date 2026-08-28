package com.wishlist.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wishlist.app.data.WishlistItem
import com.wishlist.app.repository.TableRow
import com.wishlist.app.share.SharedDraft
import com.wishlist.app.ui.screens.AddEditItemDialog
import com.wishlist.app.ui.screens.SettingsScreen
import com.wishlist.app.ui.screens.SignInScreen
import com.wishlist.app.ui.screens.WishlistListScreen
import com.wishlist.app.ui.screens.WishlistShell
import com.wishlist.app.ui.screens.WishlistTableScreen
import com.wishlist.app.ui.theme.DeviceSettings
import com.wishlist.app.ui.theme.ViewMode
import com.wishlist.app.util.todayStartOfDayMillis

@Composable
fun WishlistRoot(
    viewModel: WishlistViewModel = viewModel(),
    /** Something another app shared, waiting to be turned into a 할 일. */
    sharedDraft: SharedDraft? = null,
    onSharedDraftHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val currentUser by viewModel.authManager.currentUser.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val deviceSettings = remember(context) { DeviceSettings.get(context) }
    val viewMode by deviceSettings.viewMode.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showAddEdit by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<WishlistItem?>(null) }

    // A share opens the editor on a new item filled in from it — never saved behind the user's
    // back, so what came across can be corrected or filed before it lands in the list.
    LaunchedEffect(sharedDraft) {
        val draft = sharedDraft ?: return@LaunchedEffect
        editingItem = WishlistItem(
            title = draft.title,
            memo = draft.memo,
            endDate = draft.endDate,
            startedAt = todayStartOfDayMillis(),
        )
        showAddEdit = true
        onSharedDraftHandled()
    }

    if (currentUser == null) {
        SignInScreen(authManager = viewModel.authManager)
        return
    }

    if (showSettings) {
        SettingsScreen(authManager = viewModel.authManager, onBack = { showSettings = false })
        return
    }

    val onToggleRowDone: (TableRow) -> Unit = { row ->
        // A row backed by a 세부항목 ticks that step; an item with none ticks the item itself.
        if (row.subIndex >= 0) {
            viewModel.toggleSubItem(row.item, row.subIndex)
        } else {
            viewModel.toggleItemDone(row.item)
        }
    }
    val onRowClick: (TableRow) -> Unit = { row ->
        editingItem = row.item
        showAddEdit = true
    }

    WishlistShell(
        uiState = uiState,
        viewMode = viewMode,
        onViewModeChange = deviceSettings::setViewMode,
        onHideCompletedChange = viewModel::onHideCompletedChange,
        onSortSelected = viewModel::onSortSelected,
        onAddClick = {
            editingItem = null
            showAddEdit = true
        },
        onSettingsClick = { showSettings = true },
    ) { dark ->
        // Same data, same sort, same filter — only the shape differs.
        when (viewMode) {
            ViewMode.TABLE -> WishlistTableScreen(
                uiState = uiState,
                dark = dark,
                onSortSelected = viewModel::onSortSelected,
                onToggleRowDone = onToggleRowDone,
                onRowClick = onRowClick,
            )

            ViewMode.LIST -> WishlistListScreen(
                uiState = uiState,
                dark = dark,
                onToggleRowDone = onToggleRowDone,
                onRowClick = onRowClick,
            )
        }
    }

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
