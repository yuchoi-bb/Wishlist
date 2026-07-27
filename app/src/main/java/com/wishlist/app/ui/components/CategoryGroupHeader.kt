package com.wishlist.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wishlist.app.data.SortField

fun sortFieldLabel(field: SortField): String = when (field) {
    SortField.COMPLETED_AT -> "완료일"
    SortField.STARTED_AT -> "시작일"
    SortField.DURATION -> "고민한 기간"
    SortField.TITLE -> "할 일 이름"
}

/**
 * Header for one category group. Each group keeps its own sort field + direction,
 * independent of every other group's setting.
 */
@Composable
fun CategoryGroupHeader(
    title: String,
    sortField: SortField,
    ascending: Boolean,
    onSortFieldSelected: (SortField) -> Unit,
    onToggleDirection: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )

        androidx.compose.foundation.layout.Box {
            TextButton(onClick = { menuExpanded = true }) {
                Text(sortFieldLabel(sortField))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                SortField.entries.forEach { field ->
                    DropdownMenuItem(
                        text = { Text(sortFieldLabel(field)) },
                        onClick = {
                            onSortFieldSelected(field)
                            menuExpanded = false
                        },
                    )
                }
            }
        }

        IconButton(onClick = onToggleDirection) {
            Icon(
                imageVector = if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = if (ascending) "오름차순" else "내림차순",
            )
        }
    }
}
