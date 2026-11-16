package com.wishlist.app.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import com.wishlist.app.data.ArcItem
import com.wishlist.app.repository.TableRow
import com.wishlist.app.share.SharedDraft
import com.wishlist.app.share.clipboardText
import com.wishlist.app.share.draftFromText
import com.wishlist.app.ui.screens.AddEditItemDialog
import com.wishlist.app.ui.screens.ArcListScreen
import com.wishlist.app.ui.screens.ArcShell
import com.wishlist.app.ui.screens.ArcTableScreen
import com.wishlist.app.ui.screens.SettingsScreen
import com.wishlist.app.ui.screens.SignInScreen
import com.wishlist.app.ui.theme.DeviceSettings
import com.wishlist.app.ui.theme.ViewMode
import com.wishlist.app.util.todayStartOfDayMillis

@Composable
fun ArcRoot(
    viewModel: ArcViewModel = viewModel(),
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
    var editingItem by remember { mutableStateOf<ArcItem?>(null) }

    // Text arriving from outside — shared in, or pasted — opens the editor on a new item filled
    // in from it. Never saved behind the user's back, so what came across can be corrected or
    // filed before it lands in the list, and a misread list is fixed in the same breath.
    val openDraft: (SharedDraft) -> Unit = { draft ->
        editingItem = ArcItem(
            title = draft.title,
            memo = draft.memo,
            endDate = draft.endDate,
            subItems = draft.subItems,
            startedAt = todayStartOfDayMillis(),
        )
        showAddEdit = true
    }

    LaunchedEffect(sharedDraft) {
        val draft = sharedDraft ?: return@LaunchedEffect
        openDraft(draft)
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
    // Linked 할 일 opened so far, outermost first. Empty means the whole table.
    var focusStack by remember { mutableStateOf<List<String>>(emptyList()) }
    val focusedItem = focusStack.lastOrNull()?.let { id -> uiState.items.firstOrNull { it.id == id } }

    // A stale id — the target was deleted on another device — would otherwise leave a screen with
    // no way out, since the title and the rows both come from an item that no longer exists.
    LaunchedEffect(uiState.items, focusStack) {
        if (focusStack.isNotEmpty() && uiState.items.isNotEmpty() && focusedItem == null) {
            focusStack = focusStack.dropLast(1)
        }
    }

    BackHandler(enabled = focusStack.isNotEmpty()) { focusStack = focusStack.dropLast(1) }

    val onRowClick: (TableRow) -> Unit = { row ->
        val linkedId = row.subItem?.linkedItemId
        if (linkedId != null && uiState.items.any { it.id == linkedId }) {
            // Opening the link is what a tap on it means; the 할 일 it names is edited from its own
            // screen, the same way any other is.
            focusStack = focusStack + linkedId
        } else {
            editingItem = row.item
            showAddEdit = true
        }
    }

    // Inside a linked 할 일 the table narrows to that one item: its steps, in the order the current
    // sort puts them, filtered the same way.
    val visibleState = focusedItem?.let { item ->
        uiState.copy(rows = uiState.rows.filter { it.item.id == item.id })
    } ?: uiState

    ArcShell(
        uiState = visibleState,
        focusedTitle = focusedItem?.title,
        onBack = { focusStack = focusStack.dropLast(1) },
        viewMode = viewMode,
        onViewModeChange = deviceSettings::setViewMode,
        onHideCompletedChange = viewModel::onHideCompletedChange,
        onSortSelected = viewModel::onSortSelected,
        onAddClick = {
            editingItem = null
            showAddEdit = true
        },
        onPasteClick = {
            val draft = context.clipboardText()?.let(::draftFromText)
            if (draft == null) {
                Toast.makeText(context, "붙여넣을 내용이 없습니다.", Toast.LENGTH_SHORT).show()
            } else {
                openDraft(draft)
            }
        },
        onSettingsClick = { showSettings = true },
    ) { dark ->
        // Same data, same sort, same filter — only the shape differs.
        when (viewMode) {
            ViewMode.TABLE -> ArcTableScreen(
                uiState = visibleState,
                dark = dark,
                onSortSelected = viewModel::onSortSelected,
                onToggleRowDone = onToggleRowDone,
                onRowClick = onRowClick,
            )

            ViewMode.LIST -> ArcListScreen(
                uiState = visibleState,
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
            linkCandidates = uiState.items,
            hideCompletedSubItems = uiState.hideCompleted,
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
