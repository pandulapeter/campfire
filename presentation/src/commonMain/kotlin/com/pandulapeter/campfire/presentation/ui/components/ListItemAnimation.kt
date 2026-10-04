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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Whether the lists have the whole library and can start animating their items, which they must not do while they
 * are still being filled.
 *
 * The first read of the library publishes what it has put together as it goes (see
 * `BaseLocalDataRepository.publishPartialData`), so a list arrives in several batches, each of which drops items in
 * among the ones already on screen and pushes the rest into other slots. Animating that turns the first moments of
 * the app into the library shuffling itself into shape, as if the layout kept changing its mind about where the
 * songs go.
 *
 * Hence the effect rather than `!isLoading` on its own: the last batch lands in the very composition [isLoading]
 * goes false in, so reading it directly would still animate the largest rearrangement of them all. Written from an
 * effect, the answer only changes in the composition after that, by which time nothing is moving any more.
 */
@Composable
internal fun rememberHasLoadedLibrary(isLoading: Boolean): Boolean {
    var hasLoadedLibrary by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) { if (!isLoading) hasLoadedLibrary = true }
    return hasLoadedLibrary
}

/**
 * Scrolls a list back to the top whenever [key] changes - which is where its search, its sorting and its filters
 * go - and keeps the change of [contents] that follows it animated.
 *
 * A lazy grid holds on to the key of its first visible item across a change of its contents, and a list that grows
 * has that item further down than it was: a filter taken off puts the songs it hid above the row at the top, and the
 * grid follows that row to its new index. Going back to the top from there is a jump to a different position, which
 * the grid answers by forgetting where every item was, so nothing slides out of the way and nothing fades in - the
 * rows are simply there. A list that is narrowed never runs into it, since the rows it keeps only ever move up.
 *
 * The position is therefore asked for by index, in the very composition the new contents arrive in, before the grid
 * measures them. That can be several frames after [key] changed, since the list is worked out outside the
 * composition, so every change of [contents] is held that way until the list is next scrolled - which is also where
 * holding it by index stops being right, and the grid goes back to following its first visible row through an edit.
 * The key last scrolled to the top is saved, so that coming back from another screen keeps the restored position
 * rather than jumping to the top.
 *
 * A change of [key] that was asked for from a row of the list itself - a tag on a song, which narrows the list to the
 * songs carrying it - leaves that row where it was instead, since the row is what the user was looking at and it is
 * still in the list the change leads to. The row is put in [anchor] before the change is made, and the position is
 * asked for once the contents it changes arrive: by the row's new index, with the offset it had on screen, which the
 * grid honors as far as there is list above the row to fill it with and stops at the top where there is not. That
 * is a jump to a different position, so the grid animates none of it, and [anchor] narrates the change instead (see
 * [anchoredTransition]).
 *
 * @param contents What the grid is built from, compared by identity: a new instance is a change of the list. Where an
 * [anchor] is passed, a value that changes with [key] and carries it, so that the two arrive in one composition: the
 * anchor is only consumed by a change of [contents], and a key that changed without one - a filter that leaves the
 * list as it was - would leave the row pending until the list next changes for some unrelated reason.
 * @param itemIndex The index of the item with the given key in [contents], or null where it holds no such item.
 */
@Composable
internal fun ScrollToTopWhenChanged(
    listState: LazyGridState,
    key: String,
    contents: Any?,
    anchor: ListAnchor? = null,
    itemIndex: (Any) -> Int? = { null },
    scrollToTopOnKeyChange: Boolean = true,
) {
    var lastScrollToTopKey by rememberSaveable { mutableStateOf(key) }
    val heldTop = remember { HeldTop(contents) }
    val coroutineScope = rememberCoroutineScope()
    // A side effect rather than a launched one, because it runs before the grid measures what this composition gave
    // it: a request made a frame later would come after the grid had already followed its first row down.
    SideEffect {
        val hasKeyChanged = key != lastScrollToTopKey
        if (hasKeyChanged) {
            lastScrollToTopKey = key
            // Opting out lets the grid retain its visible item by key when a filter is removed.
            heldTop.isHolding = scrollToTopOnKeyChange
            heldTop.anchoredItem = anchor?.take()
        }
        val anchoredItem = heldTop.anchoredItem
        if (anchoredItem != null) {
            if (contents !== heldTop.contents) {
                heldTop.anchoredItem = null
                val index = itemIndex(anchoredItem.key)
                if (index == null) {
                    listState.requestScrollToItem(0)
                } else {
                    listState.requestScrollToItem(index = index, scrollOffset = -anchoredItem.offset)
                    anchor?.animateFrom(anchoredItem.visibleOffsets, coroutineScope)
                }
            }
        } else if (hasKeyChanged && scrollToTopOnKeyChange) {
            listState.requestScrollToItem(0)
        } else if (heldTop.isHolding && contents !== heldTop.contents && !listState.isScrollInProgress) {
            listState.requestScrollToItem(
                index = listState.firstVisibleItemIndex,
                scrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }
        heldTop.contents = contents
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect {
            heldTop.isHolding = false
            // A list scrolled before the change arrived has moved on from the row, and bringing it back would undo
            // the scroll.
            heldTop.anchoredItem = null
            anchor?.stopTransition()
        }
    }
}

/**
 * Scrolls a list of a sheet or a dialog back to its start whenever [key], the order it is sorted in, changes: what was
 * in front before is somewhere else now, and the start is where the new order is read from.
 *
 * The reordered [contents] can arrive several frames after [key], where the view model sorts them away from the main
 * thread, and a lazy list follows its first visible item to wherever that went. So, as in [ScrollToTopWhenChanged],
 * every change of [contents] is held at the position the list is at until the list is next scrolled.
 */
@Composable
internal fun ScrollToStartWhenChanged(
    listState: LazyListState,
    key: Any?,
    contents: Any?,
) {
    val heldStart = remember { HeldStart(key, contents) }
    // A side effect for the reason given in ScrollToTopWhenChanged: it runs before the list measures the new contents.
    SideEffect {
        if (key != heldStart.key) {
            heldStart.key = key
            heldStart.isHolding = true
            listState.requestScrollToItem(0)
        } else if (heldStart.isHolding && contents !== heldStart.contents && !listState.isScrollInProgress) {
            listState.requestScrollToItem(
                index = listState.firstVisibleItemIndex,
                scrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }
        heldStart.contents = contents
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { heldStart.isHolding = false }
    }
}

/** What [ScrollToStartWhenChanged] last saw, which is not state: it is only read and written by its side effect. */
private class HeldStart(
    var key: Any?,
    var contents: Any?,
) {
    var isHolding = false
}

/**
 * The row the next change of [ScrollToTopWhenChanged]'s key keeps in place, and the transition that narrates the
 * change around it. The row is not state: it is set by the tap that makes the change, in the same event, and read by
 * the effect of the composition that change causes. The transition is, since every item's layer reads it.
 */
internal class ListAnchor {
    private var item: AnchoredItem? = null
    private var transition by mutableStateOf<AnchorTransition?>(null)
    private var transitionJob: Job? = null

    /** Remembers where the item with [key] is on screen, if it is on screen at all, and where every item around it is. */
    fun set(listState: LazyGridState, key: Any) {
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        item = visibleItems.firstOrNull { it.key == key }?.let { anchoredItem ->
            AnchoredItem(
                key = key,
                offset = anchoredItem.offset.y,
                visibleOffsets = visibleItems.associate { it.key to it.offset },
            )
        }
    }

    fun take() = item.also { item = null }

    fun animateFrom(visibleOffsets: Map<Any, IntOffset>, coroutineScope: CoroutineScope) {
        transitionJob?.cancel()
        val newTransition = AnchorTransition(visibleOffsets)
        transition = newTransition
        transitionJob = coroutineScope.launch {
            try {
                newTransition.progress.animateTo(1f, ITEM_FADE_SPEC)
            } finally {
                if (transition === newTransition) transition = null
            }
        }
    }

    fun stopTransition() {
        transitionJob?.cancel()
    }

    /**
     * Where the item with [key] is drawn from, relative to where the grid placed it, and how opaque it is: an item
     * that was on screen before the change slides in from where it was, and one that was not fades in where it is.
     */
    fun GraphicsLayerScope.applyTransition(listState: LazyGridState, key: Any) {
        val transition = transition ?: return
        val progress = transition.progress.value
        val previousOffset = transition.previousOffsets[key]
        if (previousOffset == null) {
            alpha = progress
        } else {
            val offset = transition.offsetOf(listState, key) ?: return
            translationX = (previousOffset.x - offset.x) * (1f - progress)
            translationY = (previousOffset.y - offset.y) * (1f - progress)
        }
    }
}

/**
 * The movement of an item across an anchored change of [ScrollToTopWhenChanged]'s key, which the grid does not animate
 * itself: it asks for the new position by index, and a lazy grid answers a jump by forgetting where every item was,
 * since a jump is ordinarily a scroll and a scroll is not a change of the list. What the change looks like is then
 * drawn on top of the grid's own layout, which is final from the first frame, so nothing is measured twice and the
 * rows only move in their layers. The items the change takes away are gone at once, since nothing draws them any more.
 */
internal fun Modifier.anchoredTransition(anchor: ListAnchor, listState: LazyGridState, key: Any) = graphicsLayer {
    with(anchor) { applyTransition(listState, key) }
}

/** An item of the grid, how far below the start of the viewport its top was, in pixels, and where the rest were. */
internal class AnchoredItem(val key: Any, val offset: Int, val visibleOffsets: Map<Any, IntOffset>)

private class AnchorTransition(val previousOffsets: Map<Any, IntOffset>) {
    val progress = Animatable(0f)
    private var layoutInfo: LazyGridLayoutInfo? = null
    private var offsets = emptyMap<Any, IntOffset>()

    /** Worked out once per layout rather than once per item, since every visible item asks while the change runs. */
    fun offsetOf(listState: LazyGridState, key: Any): IntOffset? {
        val currentLayoutInfo = listState.layoutInfo
        if (currentLayoutInfo !== layoutInfo) {
            layoutInfo = currentLayoutInfo
            offsets = currentLayoutInfo.visibleItemsInfo.associate { it.key to it.offset }
        }
        return offsets[key]
    }
}

/** What [ScrollToTopWhenChanged] remembers between compositions, none of which is ever drawn. */
private class HeldTop(var contents: Any?) {
    var isHolding = false
    var anchoredItem: AnchoredItem? = null
}

/**
 * The placement animation of an item of the song or setlist grid.
 *
 * An item sliding into its new slot says that the list the user is looking at has changed: a song was renamed,
 * deleted, filtered out, or a different sorting order moved it. The library arriving is not that kind of change
 * ([isEnabled], which the song and setlist lists answer with [rememberHasLoadedLibrary]), and neither is a scroll —
 * hence [listState], which turns the placement animation off for as long as the list is moving.
 *
 * A lazy list keeps the animation state of the items it has on screen and moves each of them by the distance the
 * list itself was scrolled, so that scrolling is not mistaken for the list rearranging itself. The two numbers come
 * apart at the ends of the list, where a fast scroll asks for more than there is left to give: an item that was on
 * screen for both of the last two frames is moved by the distance that was *asked* for rather than the shorter one
 * that was actually scrolled, lands far outside the list, and is then animated back to where it belongs. What that
 * looks like is a single row sliding in from off screen while every other row is already still, most visibly the
 * first row of a list that was flung back to the top.
 *
 * Nothing is lost by leaving the placement animation off while the list moves, since it is there to narrate a change
 * of its *contents* and the contents do not change under a finger. Whatever does change mid-scroll simply takes its
 * place, and the next change with the list at rest animates as it always did. Only the placement is turned off, and
 * by its spec rather than by taking the modifier away: a modifier taken away at the start of a scroll takes the
 * animation node with it, cutting short a placement animation that is still running, and puts a new one in at the
 * end. The fades, which move nothing, are what keep the modifier in place, since one with no spec at all is none.
 * The scroll state is read here, while composing, on purpose: the spec is a parameter of the modifier, which the
 * grid reads when it measures, so there is no later phase to decide it in - every visible row is recomposed once as
 * a scroll starts and once as it ends, and a spec that snapped instead of being null would still displace the row for
 * a frame.
 *
 * @param isRearranging True while a drag is rearranging the list, which is the one case the reasoning above does not
 *   cover: a list being dragged in scrolls itself once the dragged row reaches an edge, and the rows it travels past
 *   have to go on sliding out of its way while it does. That is a change of the contents and a scroll at the same
 *   time, so here the scroll is not taken as a reason to stop narrating the change - without it the rows either side
 *   of the finger snap into their new places for exactly as long as the list keeps scrolling.
 */
@Composable
internal fun LazyGridItemScope.listItemAnimation(
    listState: ScrollableState,
    isEnabled: Boolean = true,
    isRearranging: Boolean = false,
    placementSpec: FiniteAnimationSpec<IntOffset>? = ITEM_PLACEMENT_SPEC,
) = if (isEnabled) {
    Modifier.animateItem(
        fadeInSpec = ITEM_FADE_SPEC,
        placementSpec = if (isRearranging || !listState.isScrollInProgress) placementSpec else null,
        fadeOutSpec = ITEM_FADE_SPEC,
    )
} else {
    Modifier
}

/**
 * [listItemAnimation] for a row of a lazy list rather than a grid: the lists of the sheets and the dialogs, which are
 * never rearranged by a drag and never filled in batches, and so need neither of the grid's exceptions.
 */
@Composable
internal fun LazyItemScope.listItemAnimation(listState: ScrollableState) = Modifier.animateItem(
    fadeInSpec = ITEM_FADE_SPEC,
    placementSpec = if (listState.isScrollInProgress) null else ITEM_PLACEMENT_SPEC,
    fadeOutSpec = ITEM_FADE_SPEC,
)

/** The fade [Modifier.animateItem] uses by default. */
private val ITEM_FADE_SPEC = spring<Float>(stiffness = Spring.StiffnessMediumLow)

/** The placement animation [Modifier.animateItem] uses by default. */
private val ITEM_PLACEMENT_SPEC = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)
