package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** All geometry is in LazyColumn viewport pixels; the grabbed point never changes. */
internal class DragReorderState(private val list: LazyListState, private val prefix: String) {
    var draggedId by mutableStateOf<String?>(null)
        private set
    var fingerY by mutableFloatStateOf(0f)
        private set
    private var grabOffsetY = 0f
    private var draggedHeight = 0
    private var group = false
    private var originalOrder = emptyList<String>()
    private var transientOrder by mutableStateOf(emptyList<String>())
    private var pending by mutableStateOf(false)
    var groups: Map<Boolean, List<String>> = emptyMap()
    var busy = false
    var commit: (String, String, Boolean) -> Unit = { _, _, _ -> }

    fun orderFor(completed: Boolean, persisted: List<String>): List<String> =
        if ((draggedId != null || pending) && group == completed) transientOrder else persisted

    fun isDragging(id: String) = draggedId == id

    fun cancel() {
        draggedId = null
        pending = false
        transientOrder = emptyList()
    }

    fun reconcile(error: String?) {
        if (draggedId == null && !pending) return
        val persisted = groups[group].orEmpty()
        if (persisted.toSet() != originalOrder.toSet() ||
            (draggedId != null && persisted != originalOrder) ||
            (pending && (persisted == transientOrder || error != null))) cancel()
    }

    val gestureModifier: Modifier
        get() = Modifier.pointerInput(this) {
            detectDragGesturesAfterLongPress(
                onDragStart = { position -> startDrag(position.y) },
                onDrag = { change, _ ->
                    if (draggedId != null) {
                        change.consume()
                        dragTo(change.position.y)
                    }
                },
                onDragEnd = { finishDrag() },
                onDragCancel = { if (draggedId != null) cancel() },
            )
        }

    internal fun startDrag(y: Float) {
        if (!busy && !pending) {
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull {
                y >= it.offset && y < it.offset + it.size &&
                    groups.values.any { ids -> it.key.toString().removePrefix(prefix) in ids }
            }
            if (item != null) {
                val id = item.key.toString().removePrefix(prefix)
                group = groups.entries.first { id in it.value }.key
                originalOrder = groups.getValue(group).toList()
                transientOrder = originalOrder
                fingerY = y
                grabOffsetY = y - item.offset
                draggedHeight = item.size
                draggedId = id
            }
        }
    }

    internal fun dragTo(y: Float) {
        if (draggedId == null) return
        fingerY = y
        crossNeighbor()
    }

    internal fun finishDrag() {
        val id = draggedId
        if (id != null) {
            val target = finalTarget(originalOrder, transientOrder, id)
            draggedId = null // Stop the frame loop before submitting exactly once.
            if (target == null) cancel() else {
                pending = true
                commit(id, target, group)
            }
        }
    }

    fun cardModifier(id: String): Modifier = Modifier.graphicsLayer {
        if (draggedId == id) {
            val top = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "$prefix$id" }?.offset
            // Read layout in the layer block so scroll changes reconcile in the same frame.
            translationY = if (top != null) fingerY - grabOffsetY - top else 0f
            shadowElevation = 8.dp.toPx()
        } else {
            translationY = 0f
            shadowElevation = 0f
        }
    }

    fun crossNeighbor() {
        val id = draggedId ?: return
        val visible = list.layoutInfo.visibleItemsInfo.filter { it.key.toString().removePrefix(prefix) in transientOrder }
        // Wait for the last swap to be measured; old offsets must not trigger another swap.
        val measuredOrder = visible.sortedBy { it.index }.map { it.key.toString().removePrefix(prefix) }
        if (measuredOrder != transientOrder.filter { it in measuredOrder }) return
        val index = transientOrder.indexOf(id)
        val center = fingerY - grabOffsetY + draggedHeight / 2f
        for (direction in listOf(-1, 1)) {
            val neighbor = transientOrder.getOrNull(index + direction) ?: continue
            val item = visible.firstOrNull { it.key == "$prefix$neighbor" } ?: continue
            val crossed = if (direction < 0) center <= item.offset + item.size / 2f else center >= item.offset + item.size / 2f
            if (crossed) {
                // Keep viewport coordinates instead of LazyColumn anchoring the old first key.
                list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
                transientOrder = transientOrder.moveDraggedToTarget(id, neighbor)
                return // Adjacent swaps, then remeasure; repeated frames allow multiple crossings.
            }
        }
    }

    suspend fun runFrames(edge: Float, maxSpeed: Float) {
        var previous = withFrameNanos { it }
        while (draggedId != null) {
            val now = withFrameNanos { it }
            val dt = ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
            previous = now
            if (draggedId == null) break
            val velocity = computeAutoScrollVelocity(fingerY, list.layoutInfo.viewportSize.height.toFloat(), edge, maxSpeed)
            val index = transientOrder.indexOf(draggedId)
            // Do not scroll away from the active group into headers or another completion group.
            val hasNeighbor = if (velocity < 0f) index > 0 else index < transientOrder.lastIndex
            if (velocity != 0f && hasNeighbor) list.scrollBy(velocity * dt)
            crossNeighbor()
        }
    }
}

@Composable
internal fun rememberDragReorderState(
    list: LazyListState,
    prefix: String,
    groups: Map<Boolean, List<String>>,
    busy: Boolean,
    error: String?,
    layerKey: String? = null,
    commit: (String, String, Boolean) -> Unit,
): DragReorderState {
    val state = remember(list, prefix, layerKey) { DragReorderState(list, prefix) }
    SideEffect {
        state.groups = groups
        state.busy = busy
        state.commit = commit
        state.reconcile(error)
    }
    val density = LocalDensity.current
    val edge = with(density) { 96.dp.toPx() }
    val speed = with(density) { 700.dp.toPx() }
    LaunchedEffect(state, state.draggedId) { if (state.draggedId != null) state.runFrames(edge, speed) }
    DisposableEffect(state) { onDispose { state.cancel() } }
    return state
}

internal fun List<String>.moveDraggedToTarget(id: String, target: String): List<String> {
    val from = indexOf(id)
    val to = indexOf(target)
    if (from < 0 || to < 0 || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** Repository inserts at the target's ORIGINAL index, including when moving upward. */
internal fun finalTarget(original: List<String>, final: List<String>, id: String): String? {
    val index = final.indexOf(id)
    return if (index < 0 || index == original.indexOf(id)) null else original.getOrNull(index)
}

internal fun computeAutoScrollVelocity(fingerY: Float, viewportHeight: Float, edgeZonePx: Float, maxVelocity: Float): Float {
    val edge = edgeZonePx.coerceAtMost(viewportHeight / 2f)
    if (edge <= 0f) return 0f
    val proximity = when {
        fingerY < edge -> -((edge - fingerY) / edge).coerceIn(0f, 1f)
        fingerY > viewportHeight - edge -> ((fingerY - viewportHeight + edge) / edge).coerceIn(0f, 1f)
        else -> 0f
    }
    return maxVelocity * proximity * kotlin.math.abs(proximity)
}
