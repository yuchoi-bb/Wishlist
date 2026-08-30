package com.wishlist.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wishlist.app.data.SortField
import com.wishlist.app.repository.ItemGroup
import com.wishlist.app.repository.TableRow
import com.wishlist.app.repository.WishlistUiState
import com.wishlist.app.repository.toItemGroups
import com.wishlist.app.ui.theme.Serif
import com.wishlist.app.util.formatDate

/**
 * The 목록 view: the same lines the 표 shows, folded back into 항목 blocks with their 세부항목
 * indented underneath. Nothing scrolls sideways, so a phone shows a whole item at once — at the cost
 * of fitting fewer lines on screen than the table does.
 *
 * The sort still decides the order: an 항목-level sort keeps items in its order, and a 세부항목-level
 * one (예정일, 세부항목) ranks each item by its most urgent line, which is where its 세부항목 first
 * appear in the table.
 */
@Composable
fun WishlistListScreen(
    uiState: WishlistUiState,
    dark: Boolean,
    onToggleRowDone: (TableRow) -> Unit,
    onRowClick: (TableRow) -> Unit,
) {
    if (uiState.rows.isEmpty() && !uiState.isLoading) {
        Text(text = "아직 등록된 항목이 없습니다.", modifier = Modifier.padding(24.dp))
        return
    }

    val sections = uiState.rows.sectionsFor(uiState.sortField, uiState.categoryColors, dark)

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        sections.forEach { section ->
            val groups = section.rows.toItemGroups()
            if (section.label != null) {
                item(key = "section:${section.key}") {
                    SectionHeader(
                        label = section.label,
                        count = groups.size,
                        tint = section.tint,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider()
                }
            }
            items(count = groups.size, key = { index -> "${section.key}:${groups[index].key}" }) { index ->
                val group = groups[index]
                ItemBlock(
                    group = group,
                    tint = section.tint
                        ?: tintFor(uiState.sortField, group.itemRow, uiState.categoryColors, dark),
                    dark = dark,
                    // A 예정일 sort already bands by month, so repeating the category there would be
                    // noise; every other sort shows it, since it isn't visible anywhere else.
                    showCategory = uiState.sortField != SortField.CATEGORY,
                    onToggleRowDone = onToggleRowDone,
                    onRowClick = onRowClick,
                )
            }
        }
    }
}

@Composable
private fun ItemBlock(
    group: ItemGroup,
    tint: Color?,
    dark: Boolean,
    showCategory: Boolean,
    onToggleRowDone: (TableRow) -> Unit,
    onRowClick: (TableRow) -> Unit,
) {
    val item = group.item
    val strike = if (item.isDone) TextDecoration.LineThrough else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(tint ?: Color.Transparent),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRowClick(group.itemRow) }
                .padding(start = 14.dp, end = 16.dp, top = 13.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = strike,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                group.headlineEndDate?.let { date ->
                    Text(
                        text = formatDate(date),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = Serif,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Box(modifier = Modifier.padding(start = 6.dp)) {
                    PriorityChip(
                        priority = item.priority,
                        dark = dark,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            val category = listOfNotNull(
                item.majorCategory?.takeIf { it.isNotBlank() },
                item.minorCategory?.takeIf { it.isNotBlank() },
            ).joinToString(" › ")
            if (showCategory && category.isNotBlank()) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (item.subItems.isNotEmpty()) {
                ProgressLine(
                    done = item.doneSubItemCount,
                    total = item.subItems.size,
                    tint = tint ?: MaterialTheme.colorScheme.primary,
                )
            }

            group.subRows.forEach { row ->
                SubRow(row = row, dark = dark, onToggle = { onToggleRowDone(row) }, onClick = { onRowClick(row) })
            }

            // An item with no 세부항목 is completed on its own line, since there's no step to tick.
            if (group.subRows.isEmpty()) {
                SubRow(
                    row = group.itemRow,
                    dark = dark,
                    label = "세부항목 없음",
                    onToggle = { onToggleRowDone(group.itemRow) },
                    onClick = { onRowClick(group.itemRow) },
                )
            }
        }
    }
}

/** How much of an 항목 is ticked off — the count the table shows as "2/5 완료", drawn. */
@Composable
private fun ProgressLine(done: Int, total: Int, tint: Color) {
    Row(
        modifier = Modifier.padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "$done/$total",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(3.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp)),
        ) {
            if (done > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(56.dp * (done.toFloat() / total))
                        .background(tint, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun SubRow(
    row: TableRow,
    dark: Boolean,
    label: String? = null,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val strike = if (row.isDone) TextDecoration.LineThrough else null
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Checkbox(checked = row.isDone, onCheckedChange = { onToggle() })
        }
        Text(
            text = label ?: row.subItem?.title.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (label != null || row.isDone) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = strike,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clickable(onClick = onClick),
        )
        row.effectiveEndDate?.let { date ->
            Text(
                text = formatDate(date),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = Serif,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = strike,
            )
        }
        DueChip(
            endDate = row.effectiveEndDate,
            isDone = row.isDone,
            dark = dark,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
