/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draggable scrollbar of a long lazy grid, in the style of the fast scroller of a contacts app: the thumb runs down
 * the end edge, and while it is dragged a bubble next to it shows the label of the section that is currently at the
 * top of the list. The scroller stays visible for as long as the list is scrollable.
 *
 * It is a column of its own, [FAST_SCROLLER_WIDTH] wide, laid out next to the grid rather than over it, and the
 * whole column is its touch target: pressing it anywhere moves the thumb under the finger, and a track fades in
 * behind the thumb while it is pointed at or dragged to show how far that reaches. Sharing no space with the grid is
 * what lets every row keep its controls where a row without a scroller would have them, the ones in the inner columns
 * of a wide grid included, with no touch target of the scroller's reaching over any of them. Only the bubble is drawn
 * out over the list, and nothing about it can be pressed.
 *
 * @param labelForItem Returns the label of the section the item at the given index belongs to, or null if none. A
 *   list whose sections have no single character to go by (the setlists, named by whatever somebody called them)
 *   leaves it out, and the thumb is then dragged with no bubble at all.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FastScroller(
    modifier: Modifier = Modifier,
    gridState: LazyGridState,
    labelForItem: (index: Int) -> String? = { null },
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val state = remember(gridState, density) {
        FastScrollerState(
            gridState = gridState,
            minThumbHeight = with(density) { MIN_THUMB_HEIGHT.toPx() },
            touchSlack = with(density) { TOUCH_SLACK.toPx() },
        )
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    // Both derived, since what they are worked out from changes on every scrolled pixel and what they come to only
    // once in a while: read directly, they would recompose the scroller for the whole length of every scroll.
    val isVisible by remember(state) { derivedStateOf { state.isScrollable } }
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    // Progress values instead of animated colors, so that the thumb follows the color scheme immediately while it is
    // animating between the light and the dark theme (a color animation would chase it and trail behind).
    val hoverProgress by animateFloatAsState(
        targetValue = if (isHovered || state.isDragging) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val dragProgress by animateFloatAsState(
        targetValue = if (state.isDragging) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val thumbColor = lerp(
        start = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = lerp(IDLE_THUMB_ALPHA, 1f, hoverProgress)),
        stop = MaterialTheme.colorScheme.primary,
        fraction = dragProgress,
    )
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TRACK_ALPHA * hoverProgress)
    val label by remember(gridState, labelForItem) { derivedStateOf { labelForItem(gridState.firstVisibleItemIndex) } }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(FAST_SCROLLER_WIDTH)
            .padding(vertical = TRACK_VERTICAL_PADDING)
            .onSizeChanged { state.trackHeight = it.height }
            // Alpha applied to every draw call rather than through an offscreen buffer, which would be the size of the
            // column and cut off the bubble that reaches out of it over the list.
            .graphicsLayer {
                this.alpha = alpha
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val thumbWidth = THUMB_WIDTH.toPx()
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(x = size.width - thumbWidth - THUMB_END_PADDING.toPx(), y = 0f),
                        size = Size(width = thumbWidth, height = size.height),
                        cornerRadius = CornerRadius(thumbWidth / 2),
                    )
                    drawRoundRect(
                        color = thumbColor,
                        topLeft = Offset(x = size.width - thumbWidth - THUMB_END_PADDING.toPx(), y = state.thumbTop),
                        size = Size(width = thumbWidth, height = state.thumbHeight),
                        cornerRadius = CornerRadius(thumbWidth / 2),
                    )
                }
        )
        // The bubble pops out of the thumb while it is being dragged and keeps its last label while it disappears. It is
        // wider than the column, so it is measured without the column's width and hangs out of its start edge.
        AnimatedVisibility(
            visible = state.isDragging && label != null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(x = -BUBBLE_END_MARGIN.roundToPx(), y = (state.thumbCenter - BUBBLE_SIZE.toPx() / 2).roundToInt()) }
                .wrapContentWidth(align = Alignment.End, unbounded = true),
            enter = scaleIn(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(), transformOrigin = BUBBLE_TRANSFORM_ORIGIN) +
                    fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = scaleOut(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(), transformOrigin = BUBBLE_TRANSFORM_ORIGIN) +
                    fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            var lastLabel by remember { mutableStateOf(label.orEmpty()) }
            label?.let { lastLabel = it }
            Box(
                modifier = Modifier
                    .size(BUBBLE_SIZE)
                    .shadow(elevation = BUBBLE_ELEVATION, shape = CircleShape)
                    .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = lastLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        if (isVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hoverable(interactionSource)
                    .thumbDragGestures(state = state, coroutineScope = coroutineScope)
            )
        }
    }
}

/**
 * Drags the thumb for as long as the pointer that pressed this element stays down, wherever it wanders off to. The
 * element is laid out over the whole track, so a press on it is already in the thumb's coordinates.
 */
private fun Modifier.thumbDragGestures(
    state: FastScrollerState,
    coroutineScope: CoroutineScope,
) = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        // Ended in a finally: the element this runs on is taken away the moment the list stops being scrollable,
        // which cancels the gesture in the middle of a drag - the keyboard going down under a search results list is
        // enough - and a drag that never ends leaves the bubble up and the thumb frozen the next time it comes back.
        try {
            state.startDrag(pressY = down.position.y)?.let { fraction ->
                coroutineScope.launch { state.scrollToFraction(fraction) }
            }
            drag(down.id) { change ->
                // The delta has to be read before consuming the change, as consumed changes report none.
                val fraction = state.dragBy(change.positionChange().y)
                change.consume()
                coroutineScope.launch { state.scrollToFraction(fraction) }
            }
        } finally {
            state.endDrag()
        }
    }
}

private class FastScrollerState(
    private val gridState: LazyGridState,
    private val minThumbHeight: Float,
    private val touchSlack: Float,
) {
    var trackHeight by mutableIntStateOf(0)
    var isDragging by mutableStateOf(false)
        private set
    private var draggedThumbTop by mutableFloatStateOf(0f)

    /**
     * Worked out once per change of the list's layout rather than once per reader: a single frame of a scroll asks for
     * it from the thumb's top, from its height and from its range, and from whether there is anything to scroll at all,
     * and every answer costs the two collections the visible items are filtered through.
     */
    private val metrics: ScrollMetrics? by derivedStateOf { gridState.scrollMetrics() }

    val isScrollable: Boolean get() = trackHeight > 0 && metrics != null

    val thumbHeight: Float
        get() = metrics?.let { max(it.viewportFraction * trackHeight, minThumbHeight).coerceAtMost(trackHeight.toFloat()) } ?: 0f

    val thumbTop: Float
        get() = if (isDragging) draggedThumbTop else scrollFraction * thumbRange

    val thumbCenter: Float get() = thumbTop + thumbHeight / 2

    private val thumbRange: Float get() = trackHeight - thumbHeight

    private val scrollFraction: Float
        get() = when {
            !gridState.canScrollBackward -> 0f
            !gridState.canScrollForward -> 1f
            else -> metrics?.scrollFraction ?: 0f
        }

    /**
     * Starts a drag. A press at [pressY] that misses the thumb first centers the thumb under it, which is what the
     * track is there for; a press on the thumb itself leaves it where it is, so that grabbing the thumb never scrolls
     * the list by itself. The thumb counts as a little taller than it is drawn, since a finger that is not looking
     * lands just above or below a mark this narrow about as often as on it. Returns the scroll fraction to follow the
     * jump with, or null if there was none.
     */
    fun startDrag(pressY: Float): Float? {
        draggedThumbTop = thumbTop
        isDragging = true
        return if (pressY in thumbTop - touchSlack..thumbTop + thumbHeight + touchSlack) null else dragBy(pressY - thumbCenter)
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
        gridState.scrollToItem(index, (scrollOffset - index * metrics.averageItemSize).roundToInt())
    }
}

/**
 * Estimates the scroll position from the sizes of the visible items, as the total content size of a lazy grid is
 * unknown. Null if the whole list fits into the viewport.
 */
private fun LazyGridState.scrollMetrics(): ScrollMetrics? {
    if (!canScrollForward && !canScrollBackward) return null
    val info = layoutInfo
    val firstIndex = firstVisibleItemIndex
    // A pinned sticky header is listed among the visible items but sits out of order, so it must not skew the estimate.
    val visibleItems = info.visibleItemsInfo.filter { it.index >= firstIndex }.distinctBy { it.index }
    if (visibleItems.isEmpty() || info.totalItemsCount == 0) return null
    val first = visibleItems.first()
    val last = visibleItems.last()
    // Items of the same row share their offset, so this averages the height of a row over the items in it: the
    // per item slice of the content the index based math below works with.
    val averageItemSize = (last.offset.y + last.size.height - first.offset.y).toFloat() / visibleItems.size
    if (averageItemSize <= 0f) return null
    val contentHeight = info.beforeContentPadding + averageItemSize * info.totalItemsCount + info.afterContentPadding
    val maxScrollOffset = max(contentHeight - info.viewportSize.height, 1f)
    val scrollOffset = (firstIndex * averageItemSize + firstVisibleItemScrollOffset).coerceIn(0f, maxScrollOffset)
    return ScrollMetrics(
        scrollFraction = scrollOffset / maxScrollOffset,
        viewportFraction = (info.viewportSize.height / contentHeight).coerceIn(0f, 1f),
        averageItemSize = averageItemSize,
        totalItemsCount = info.totalItemsCount,
        maxScrollOffset = maxScrollOffset,
    )
}

private class ScrollMetrics(
    val scrollFraction: Float,
    val viewportFraction: Float,
    val averageItemSize: Float,
    val totalItemsCount: Int,
    val maxScrollOffset: Float,
)

/**
 * The width of the column a [FastScroller] takes up next to its list, all of which is its touch target. It is the
 * smallest width a touch target is still reliably hit at rather than the 48dp of a button, because the column is taken
 * out of the width of the rows for as long as the list is on the screen, scrollable or not - a column that came and
 * went with the scroller would reflow every row whenever the list grew past the height of the screen or shrank below
 * it. The thumb is tall enough that the target is never short of the height it lacks in width.
 */
internal val FAST_SCROLLER_WIDTH = 24.dp

private val TRACK_VERTICAL_PADDING = 8.dp
private val TOUCH_SLACK = 8.dp
private val THUMB_WIDTH = 6.dp
private val THUMB_END_PADDING = 4.dp
private val MIN_THUMB_HEIGHT = 48.dp
private val BUBBLE_SIZE = 48.dp

/** How far the bubble's end edge stays from the screen's, which is far enough for the finger on the thumb to leave it in sight. */
private val BUBBLE_END_MARGIN = 48.dp
private val BUBBLE_ELEVATION = 2.dp
private val BUBBLE_TRANSFORM_ORIGIN = TransformOrigin(pivotFractionX = 1f, pivotFractionY = 0.5f)
private const val IDLE_THUMB_ALPHA = 0.5f
private const val TRACK_ALPHA = 0.12f
