package com.wishlist.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wishlist.app.data.SubItem
import com.wishlist.app.data.WishlistItem
import com.wishlist.app.util.formatDate
import com.wishlist.app.util.formatPonderedDuration
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun WishlistItemRow(
    item: WishlistItem,
    dragHandleModifier: Modifier,
    onToggleCompleted: () -> Unit,
    onToggleSubItem: (index: Int) -> Unit,
    onMoveSubItem: (from: Int, to: Int) -> Unit,
    onClick: () -> Unit,
) {
    // Keeps the "고민한 기간" of in-progress items ticking while the row is visible.
    val nowState = remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (!item.isCompleted) {
        LaunchedEffect(item.id) {
            while (true) {
                delay(60_000)
                nowState.value = System.currentTimeMillis()
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.DragIndicator,
                    contentDescription = "길게 눌러 순서 이동",
                    modifier = dragHandleModifier.alpha(0.4f),
                )
                Checkbox(checked = item.isCompleted, onCheckedChange = { onToggleCompleted() })
                Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
                    Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "시작일: ${formatDate(item.startedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val statusText = if (item.isCompleted) {
                        "완료일: ${formatDate(item.completedAt!!)}"
                    } else {
                        "진행 중"
                    }
                    Text(text = statusText, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "고민한 기간: ${formatPonderedDuration(item.ponderedDurationMillis(nowState.value))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    item.memo?.takeIf { it.isNotBlank() }?.let { memo ->
                        Text(
                            text = memo,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (item.subItems.isNotEmpty()) {
                Text(
                    text = "세부항목 ${item.doneSubItemCount}/${item.subItems.size}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp, start = 32.dp),
                )
                SubItemList(
                    subItems = item.subItems,
                    onToggle = onToggleSubItem,
                    onMove = onMoveSubItem,
                )
            }
        }
    }
}

/**
 * The item's 세부항목, each checkable in place and reorderable by long-pressing that row. These sit
 * inside a card in the outer LazyColumn, so the drag is handled per-row here rather than by the
 * list's own drag state — the child consumes the gesture, leaving the parent's item drag alone.
 */
@Composable
private fun SubItemList(
    subItems: List<SubItem>,
    onToggle: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth().padding(start = 32.dp)) {
        subItems.forEachIndexed { index, subItem ->
            val isDragging = draggingIndex == index
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                    .pointerInput(subItems.size, index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount.y
                                val current = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                val height = rowHeight
                                if (height <= 0) return@detectDragGesturesAfterLongPress
                                val shift = (dragOffset / height).roundToInt()
                                val target = (current + shift).coerceIn(0, subItems.lastIndex)
                                if (target != current) {
                                    onMove(current, target)
                                    // Keep the row under the finger: consume the distance the swap
                                    // already accounted for.
                                    dragOffset -= (target - current) * height
                                    draggingIndex = target
                                }
                            },
                            onDragEnd = {
                                draggingIndex = null
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggingIndex = null
                                dragOffset = 0f
                            },
                        )
                    }
                    .onSizeChanged { rowHeight = it.height },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = subItem.done, onCheckedChange = { onToggle(index) })
                Text(
                    text = subItem.title,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (subItem.done) TextDecoration.LineThrough else null,
                )
            }
        }
    }
}
