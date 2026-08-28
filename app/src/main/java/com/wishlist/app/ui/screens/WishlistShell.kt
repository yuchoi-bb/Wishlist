package com.wishlist.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wishlist.app.data.SortField
import com.wishlist.app.repository.WishlistUiState
import com.wishlist.app.ui.theme.ViewMode

/**
 * Everything both main views share: the app bar, the sync banner, the 완료항목 숨기기 switch and the
 * sort control, plus the add button. Only the body differs between 표 and 목록, and the sort control
 * lives here rather than in the table's header row because the list has no header row to tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistShell(
    uiState: WishlistUiState,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    onHideCompletedChange: (Boolean) -> Unit,
    onSortSelected: (SortField) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    body: @Composable (dark: Boolean) -> Unit,
) {
    // Read from the theme in effect rather than the system setting, since it can be overridden per
    // device in 설정. Tints need a different color and a heavier alpha on a dark surface.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wishlist") },
                actions = {
                    // One tap to compare the two shapes; 설정 has the same choice spelled out.
                    IconButton(
                        onClick = {
                            onViewModeChange(
                                if (viewMode == ViewMode.TABLE) ViewMode.LIST else ViewMode.TABLE,
                            )
                        },
                    ) {
                        Icon(
                            imageVector = if (viewMode == ViewMode.TABLE) {
                                Icons.Filled.ViewAgenda
                            } else {
                                Icons.Filled.TableChart
                            },
                            contentDescription = if (viewMode == ViewMode.TABLE) "목록으로 보기" else "표로 보기",
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "설정")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("할 일 추가") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Sync trouble is shown and worked through, not crashed on: both views still work from
            // Firestore's local cache while this is up.
            uiState.syncError?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    SelectionContainer {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = uiState.hideCompleted, onCheckedChange = onHideCompletedChange)
                Text("완료항목 숨기기", style = MaterialTheme.typography.bodySmall)
                Box(modifier = Modifier.weight(1f))
                SortControl(
                    sortField = uiState.sortField,
                    ascending = uiState.ascending,
                    onSortSelected = onSortSelected,
                )
            }
            HorizontalDivider()

            body(dark)
        }
    }
}

/** The current sort, and a menu to change it. Choosing the active field again flips direction. */
@Composable
private fun SortControl(
    sortField: SortField,
    ascending: Boolean,
    onSortSelected: (SortField) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "정렬",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = sortField.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = if (ascending) "오름차순" else "내림차순",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortField.entries.forEach { field ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = field.label,
                            fontWeight = if (field == sortField) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    trailingIcon = {
                        if (field == sortField) {
                            Icon(
                                imageVector = if (ascending) {
                                    Icons.Filled.ArrowUpward
                                } else {
                                    Icons.Filled.ArrowDownward
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    onClick = {
                        onSortSelected(field)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** A section band above a run of rows that share a month or a (대/중분류). */
@Composable
fun SectionHeader(
    label: String,
    count: Int,
    tint: androidx.compose.ui.graphics.Color?,
    modifier: Modifier = Modifier,
) {
    Row(
        // Width comes from the caller: the table is inside a horizontal scroll, where filling the
        // width would mean filling an infinite constraint.
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 12.dp)
                .background(tint ?: MaterialTheme.colorScheme.outlineVariant),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
