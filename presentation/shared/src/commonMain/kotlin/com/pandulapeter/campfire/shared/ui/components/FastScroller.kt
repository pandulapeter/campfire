package com.pandulapeter.campfire.shared.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.shared.ui.platform.isDesktopPlatform
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draggable scrollbar of a long lazy list, in the style of the fast scroller of a contacts app: the thumb sits on the
 * end edge with a wide touch target, and while it is dragged a bubble next to it shows the label of the section that
 * is currently at the top of the list. On touch platforms the scroller appears while the list is scrolling or dragged
 * and hides shortly after; on desktop it is always shown. Touches outside the thumb go through to the list.
 *
 * @param labelForItem Returns the label of the section the item at the given index belongs to, or null if none.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BoxScope.FastScroller(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    labelForItem: (index: Int) -> String?
) {
    val coroutineScope = rememberCoroutineScope()
    val minThumbHeight = with(LocalDensity.current) { MIN_THUMB_HEIGHT.toPx() }
    val state = remember(listState) { FastScrollerState(listState, minThumbHeight) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isScrolling = listState.isScrollInProgress
    var isRecentlyActive by remember { mutableStateOf(false) }
    LaunchedEffect(isScrolling, state.isDragging) {
        if (isScrolling || state.isDragging) {
            isRecentlyActive = true
        } else {
            delay(HIDE_DELAY)
            isRecentlyActive = false
        }
    }
    val isVisible = state.isScrollable && (isDesktopPlatform || isRecentlyActive)
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
    )
    val thumbColor by animateColorAsState(
        when {
            state.isDragging -> MaterialTheme.colorScheme.primary
            isHovered -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = IDLE_THUMB_ALPHA)
        }
    )
    val label = labelForItem(listState.firstVisibleItemIndex)

    Box(
        modifier = modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(TOUCH_TARGET_WIDTH + BUBBLE_SPACING + BUBBLE_SIZE)
            .onSizeChanged { state.trackHeight = it.height }
            .graphicsLayer { this.alpha = alpha }
    ) {
        // The thumb is only drawn, so that it never gets in the way of the list underneath.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(TOUCH_TARGET_WIDTH)
                .drawBehind {
                    val thumbWidth = THUMB_WIDTH.toPx()
                    drawRoundRect(
                        color = thumbColor,
                        topLeft = Offset(x = size.width - thumbWidth - THUMB_END_PADDING.toPx(), y = state.thumbTop),
                        size = Size(width = thumbWidth, height = state.thumbHeight),
                        cornerRadius = CornerRadius(thumbWidth / 2)
                    )
                }
        )
        // The bubble pops out of the thumb while it is being dragged and keeps its last label while it disappears.
        AnimatedVisibility(
            visible = state.isDragging && label != null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(x = -TOUCH_TARGET_WIDTH.roundToPx(), y = (state.thumbCenter - BUBBLE_SIZE.toPx() / 2).roundToInt()) },
            enter = scaleIn(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(), transformOrigin = BUBBLE_TRANSFORM_ORIGIN) +
                    fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = scaleOut(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(), transformOrigin = BUBBLE_TRANSFORM_ORIGIN) +
                    fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec())
        ) {
            var lastLabel by remember { mutableStateOf(label.orEmpty()) }
            label?.let { lastLabel = it }
            Box(
                modifier = Modifier
                    .size(BUBBLE_SIZE)
                    .shadow(elevation = BUBBLE_ELEVATION, shape = CircleShape)
                    .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lastLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        // The touch target only covers the thumb (plus some slack), so that touches elsewhere reach the list.
        if (isVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(TOUCH_TARGET_WIDTH)
                    .layout { measurable, constraints ->
                        val slack = TOUCH_SLACK.roundToPx()
                        val height = (state.thumbHeight + 2 * slack).roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
                        layout(placeable.width, height) { placeable.placeRelative(0, (state.thumbTop - slack).roundToInt()) }
                    }
                    .hoverable(interactionSource)
                    .pointerInput(state) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            state.startDrag()
                            drag(down.id) { change ->
                                // The delta has to be read before consuming the change, as consumed changes report none.
                                val fraction = state.dragBy(change.positionChange().y)
                                change.consume()
                                coroutineScope.launch { state.scrollToFraction(fraction) }
                            }
                            state.endDrag()
                        }
                    }
            )
        }
    }
}

private class FastScrollerState(
    private val listState: LazyListState,
    private val minThumbHeight: Float
) {
    var trackHeight by mutableIntStateOf(0)
    var isDragging by mutableStateOf(false)
        private set
    private var draggedThumbTop by mutableFloatStateOf(0f)

    private val metrics: ScrollMetrics? get() = listState.scrollMetrics()

    val isScrollable: Boolean get() = trackHeight > 0 && metrics != null

    val thumbHeight: Float
        get() = metrics?.let { max(it.viewportFraction * trackHeight, minThumbHeight).coerceAtMost(trackHeight.toFloat()) } ?: 0f

    val thumbTop: Float
        get() = if (isDragging) draggedThumbTop else scrollFraction * thumbRange

    val thumbCenter: Float get() = thumbTop + thumbHeight / 2

    private val thumbRange: Float get() = trackHeight - thumbHeight

    private val scrollFraction: Float
        get() = when {
            !listState.canScrollBackward -> 0f
            !listState.canScrollForward -> 1f
            else -> metrics?.scrollFraction ?: 0f
        }

    fun startDrag() {
        draggedThumbTop = thumbTop
        isDragging = true
    }

    /** Moves the thumb by [delta] pixels and returns the new scroll fraction. */
    fun dragBy(delta: Float): Float {
        val range = thumbRange
        if (range <= 0f) return 0f
        draggedThumbTop = (draggedThumbTop + delta).coerceIn(0f, range)
        return draggedThumbTop / range
    }

    fun endDrag() {
        isDragging = false
    }

    suspend fun scrollToFraction(fraction: Float) {
        val metrics = metrics ?: return
        val scrollOffset = fraction * metrics.maxScrollOffset
        val index = (scrollOffset / metrics.averageItemSize).toInt().coerceIn(0, metrics.totalItemsCount - 1)
        listState.scrollToItem(index, (scrollOffset - index * metrics.averageItemSize).roundToInt())
    }
}

/**
 * Estimates the scroll position from the sizes of the visible items, as the total content size of a lazy list is
 * unknown. Null if the whole list fits into the viewport.
 */
private fun LazyListState.scrollMetrics(): ScrollMetrics? {
    if (!canScrollForward && !canScrollBackward) return null
    val info = layoutInfo
    val firstIndex = firstVisibleItemIndex
    // A pinned sticky header is listed among the visible items but sits out of order, so it must not skew the estimate.
    val visibleItems = info.visibleItemsInfo.filter { it.index >= firstIndex }.distinctBy { it.index }
    if (visibleItems.isEmpty() || info.totalItemsCount == 0) return null
    val first = visibleItems.first()
    val last = visibleItems.last()
    val averageItemSize = (last.offset + last.size - first.offset).toFloat() / visibleItems.size
    if (averageItemSize <= 0f) return null
    val contentHeight = info.beforeContentPadding + averageItemSize * info.totalItemsCount + info.afterContentPadding
    val maxScrollOffset = max(contentHeight - info.viewportSize.height, 1f)
    val scrollOffset = (firstIndex * averageItemSize + firstVisibleItemScrollOffset).coerceIn(0f, maxScrollOffset)
    return ScrollMetrics(
        scrollFraction = scrollOffset / maxScrollOffset,
        viewportFraction = (info.viewportSize.height / contentHeight).coerceIn(0f, 1f),
        averageItemSize = averageItemSize,
        totalItemsCount = info.totalItemsCount,
        maxScrollOffset = maxScrollOffset
    )
}

private class ScrollMetrics(
    val scrollFraction: Float,
    val viewportFraction: Float,
    val averageItemSize: Float,
    val totalItemsCount: Int,
    val maxScrollOffset: Float
)

private val TOUCH_TARGET_WIDTH = 48.dp
private val TOUCH_SLACK = 8.dp
private val THUMB_WIDTH = 6.dp
private val THUMB_END_PADDING = 4.dp
private val MIN_THUMB_HEIGHT = 48.dp
private val BUBBLE_SIZE = 48.dp
private val BUBBLE_SPACING = 4.dp
private val BUBBLE_ELEVATION = 2.dp
private val BUBBLE_TRANSFORM_ORIGIN = TransformOrigin(pivotFractionX = 1f, pivotFractionY = 0.5f)
private const val IDLE_THUMB_ALPHA = 0.5f
private const val HIDE_DELAY = 1500L
