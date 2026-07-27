package com.wishlist.app.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Long-press drag reordering for a [androidx.compose.foundation.lazy.LazyColumn].
 *
 * [canDrag] gates which rows participate — the list interleaves category headers with items, and
 * only items may move. [onMove] is called on every swap so the list animates while the finger is
 * down; [onDragFinished] is where the result should be persisted, so one drag is one write.
 */
@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    canDrag: (index: Int) -> Boolean,
    onMove: (from: Int, to: Int) -> Unit,
    onDragFinished: (draggedIndex: Int) -> Unit,
): DragDropState {
    val state = remember(lazyListState) {
        DragDropState(lazyListState, canDrag, onMove, onDragFinished)
    }
    LaunchedEffect(state) {
        state.scrollChannel.receiveAsFlow().collect { diff -> lazyListState.scrollBy(diff) }
    }
    return state
}

class DragDropState internal constructor(
    private val state: LazyListState,
    private val canDrag: (Int) -> Boolean,
    private val onMove: (Int, Int) -> Unit,
    private val onDragFinished: (Int) -> Unit,
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    internal val scrollChannel = Channel<Float>()

    private var draggingItemInitialOffset by mutableIntStateOf(0)
    private var draggingItemDraggedDelta by mutableFloatStateOf(0f)

    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    /** Vertical shift to apply to the row currently under the finger. */
    val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { item ->
            draggingItemInitialOffset + draggingItemDraggedDelta - item.offset
        } ?: 0f

    /** Starts dragging a known row, used by the per-row drag handle. */
    fun onDragStartAt(index: Int) {
        if (!canDrag(index)) return
        val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        draggingItemIndex = index
        draggingItemInitialOffset = info.offset
    }

    fun onDragInterrupted() {
        draggingItemIndex?.let(onDragFinished)
        draggingItemIndex = null
        draggingItemDraggedDelta = 0f
        draggingItemInitialOffset = 0
    }

    fun onDrag(offset: Offset) {
        draggingItemDraggedDelta += offset.y

        val dragging = draggingItemLayoutInfo ?: return
        val startOffset = dragging.offset + draggingItemOffset
        val endOffset = startOffset + dragging.size
        val middleOffset = startOffset + (endOffset - startOffset) / 2f

        val target = state.layoutInfo.visibleItemsInfo.find { item ->
            middleOffset.toInt() in item.offset..(item.offset + item.size) &&
                item.index != dragging.index &&
                canDrag(item.index)
        }

        if (target != null) {
            onMove(dragging.index, target.index)
            draggingItemIndex = target.index
        } else {
            // Nothing to swap with: near an edge, scroll the list instead so long lists stay reachable.
            val overscroll = when {
                draggingItemDraggedDelta > 0 ->
                    (endOffset - state.layoutInfo.viewportEndOffset).coerceAtLeast(0f)
                draggingItemDraggedDelta < 0 ->
                    (startOffset - state.layoutInfo.viewportStartOffset).coerceAtMost(0f)
                else -> 0f
            }
            if (overscroll != 0f) scrollChannel.trySend(overscroll)
        }
    }
}

/**
 * Long-press-and-drag handle for one row. Attached to the row's grip icon rather than the whole
 * list: the card itself is clickable (tap opens the editor), so starting drags from a dedicated
 * handle keeps the two gestures from competing and makes the affordance visible.
 */
fun Modifier.dragHandle(dragDropState: DragDropState, index: Int): Modifier =
    pointerInput(dragDropState, index) {
        detectDragGesturesAfterLongPress(
            onDragStart = { dragDropState.onDragStartAt(index) },
            onDrag = { change, dragAmount ->
                change.consume()
                dragDropState.onDrag(dragAmount)
            },
            onDragEnd = { dragDropState.onDragInterrupted() },
            onDragCancel = { dragDropState.onDragInterrupted() },
        )
    }
