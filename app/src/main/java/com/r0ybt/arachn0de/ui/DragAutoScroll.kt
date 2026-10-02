package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal object DragAutoScroll {
    private const val EDGE_THRESHOLD = 96f
    private const val SCROLL_INTERVAL_MS = 16L

    fun edgeDirection(listState: LazyListState, itemKey: String, dragOffset: Float): Int {
        val visible = listState.layoutInfo.visibleItemsInfo
        val source = visible.firstOrNull { it.key.toString() == itemKey } ?: return 0
        val center = source.offset + source.size / 2f + dragOffset
        val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()

        return when {
            center < EDGE_THRESHOLD -> -1
            center > viewportHeight - EDGE_THRESHOLD -> 1
            else -> 0
        }
    }

    fun CoroutineScope.startJob(
        listState: LazyListState,
        itemKey: String,
        dragOffsetProvider: () -> Float,
    ): Job = launch {
        while (isActive) {
            val direction = edgeDirection(listState, itemKey, dragOffsetProvider())
            if (direction == 0) break

            val canScroll = when {
                direction < 0 -> listState.firstVisibleItemIndex > 0
                direction > 0 -> listState.firstVisibleItemIndex < listState.layoutInfo.totalItemsCount - 1
                else -> false
            }
            if (!canScroll) break

            val nextIndex = when {
                direction < 0 -> (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                direction > 0 -> (listState.firstVisibleItemIndex + 1).coerceAtMost(listState.layoutInfo.totalItemsCount - 1)
                else -> listState.firstVisibleItemIndex
            }
            if (nextIndex == listState.firstVisibleItemIndex) break
            listState.scrollToItem(nextIndex)
            delay(SCROLL_INTERVAL_MS)
        }
    }
}
