/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.list

import com.pandulapeter.campfire.presentation.ui.components.only
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
 * The row the next change of [ScrollToTopWhenChanged]'s key keeps in place, and the transition that narrates the
 * change around it. The row is not state: it is set by the tap that makes the change, in the same event, and read by
 * the effect of the composition that change causes. The transition is, since every item's layer reads it.
 */
internal class ListAnchor {
    private var item: AnchoredItem? = null
    private var transition by mutableStateOf<AnchorTransition?>(null)
    private var transitionJob: Job? = null

    /** What the items last drew, for the ones a change pushes off screen ([animateFrom]'s `listedAfter`). */
    val snapshots = AnchorSnapshots()

    /**
     * Counts the transitions [animateFrom] has started, so that whoever started one can tell whether the one running is
     * still theirs. Not state: it is read by effects, never drawn.
     */
    var transitionNumber = 0
        private set

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

    /**
     * Narrates a jump from [visibleOffsets], the items' offsets before it, on [animationSpec]: a change that the list
     * also moves under, as the app bar fills in, is narrated on that movement's spring, so that what stands still on
     * screen does. Called before [listState] measures the change, whose layout until then is the one before it.
     *
     * @param listedAfter Set for a change that reads as the list moving around what stays, which the search opening and
     *   closing and a filter tapped on a row do, and not for a tick, which moves nothing on screen: the items that come
     *   on screen travel in with the ones that were ([arrivalShift]) rather than fade in where they are, and the ones
     *   still listed after the change that leave by an edge travel off with them ([departureShift]) rather than vanish.
     */
    fun animateFrom(
        listState: LazyGridState,
        visibleOffsets: Map<Any, IntOffset>,
        coroutineScope: CoroutineScope,
        animationSpec: AnimationSpec<Float> = ITEM_FADE_SPEC,
        listedAfter: ((key: Any) -> Boolean)? = null,
    ) {
        transitionJob?.cancel()
        transitionNumber++
        val newTransition = AnchorTransition(
            previousLayoutInfo = listState.layoutInfo,
            previousOffsets = visibleOffsets,
            listedAfter = listedAfter,
            departing = if (listedAfter == null) emptyMap() else snapshots.hold(visibleOffsets.keys),
        )
        transition = newTransition
        transitionJob = coroutineScope.launch {
            try {
                newTransition.progress.animateTo(1f, animationSpec)
            } finally {
                if (transition === newTransition) transition = null
                snapshots.release(newTransition.departing.keys)
            }
        }
    }

    /** How far the running transition is, null while none runs: read on every frame of one by whoever reads it. */
    val transitionProgress: Float?
        get() = transition?.progress?.value

    fun stopTransition() {
        transitionJob?.cancel()
    }

    /**
     * Where the item with [key] is drawn from, relative to where the grid placed it, and how opaque it is: an item
     * that was on screen before the change slides in from where it was, and one that was not fades in where it is,
     * unless it travels in from past an edge ([animateFrom]'s `listedAfter`).
     */
    fun GraphicsLayerScope.applyTransition(listState: LazyGridState, key: Any) {
        val transition = transition ?: return
        val progress = transition.progress.value
        val previousOffset = transition.previousOffsets[key]
        if (previousOffset == null) {
            val arrival = transition.arrivalOf(listState, key)
            if (arrival == null || arrival.fadesIn) alpha = progress
            if (arrival != null) translationY = arrival.shift * (1f - progress)
        } else {
            val offset = transition.offsetOf(listState, key) ?: return
            translationX = (previousOffset.x - offset.x) * (1f - progress)
            translationY = (previousOffset.y - offset.y) * (1f - progress)
        }
    }

    /**
     * Draws the items the running change pushes off screen where they travel, from what they last drew: the grid no
     * longer has them. Clipped to the grid, past whose edges they go.
     */
    fun DrawScope.drawDepartures(listState: LazyGridState) {
        val transition = transition ?: return
        val progress = transition.progress.value
        val departures = transition.departuresOf(listState).takeIf { it.isNotEmpty() } ?: return
        clipRect {
            departures.forEach { (snapshot, shift) ->
                translate(left = snapshot.position.x, top = snapshot.position.y - shift * progress) { drawLayer(snapshot.layer) }
            }
        }
    }
}

/**
 * The movement of an item across an anchored change of [ScrollToTopWhenChanged]'s key, which the grid does not animate
 * itself: it asks for the new position by index, and a lazy grid answers a jump by forgetting where every item was,
 * since a jump is ordinarily a scroll and a scroll is not a change of the list. What the change looks like is then
 * drawn on top of the grid's own layout, which is final from the first frame, so nothing is measured twice and the
 * rows only move in their layers. The items the change takes away are gone at once, since nothing draws them any more.
 *
 * @param canBePushedOff Whether the item can still be listed after a change that pushes it off screen
 *   ([ListAnchor.animateFrom]'s `listedAfter`), and so keeps what it last drew for that ([AnchorSnapshots]): the items
 *   that never are skip the cost.
 */
internal fun Modifier.anchoredTransition(anchor: ListAnchor, listState: LazyGridState, key: Any, canBePushedOff: Boolean = false) =
    graphicsLayer { with(anchor) { applyTransition(listState, key) } }
        .then(if (canBePushedOff) Modifier.anchorSnapshot(anchor.snapshots, key) else Modifier)

/** An item of the grid, how far below the start of the viewport its top was, in pixels, and where the rest were. */
internal class AnchoredItem(val key: Any, val offset: Int, val visibleOffsets: Map<Any, IntOffset>)

private class AnchorTransition(
    private val previousLayoutInfo: LazyGridLayoutInfo,
    val previousOffsets: Map<Any, IntOffset>,
    private val listedAfter: ((Any) -> Boolean)?,
    val departing: Map<Any, AnchorSnapshot>,
) {
    val progress = Animatable(0f)
    private var layoutInfo: LazyGridLayoutInfo? = null
    private val offsets = mutableMapOf<Any, IntOffset>()
    private val arrivals = mutableMapOf<Any, ItemArrival>()
    private val departureShifts = mutableMapOf<Any, Int?>()

    /**
     * Where the grid first placed the item with [key] after the change, kept for the rest of it rather than read off every
     * layout: cards above or between others grow or shrink while the change runs (an artist line folding out), and the
     * grid moves the items under them as they do. Measured from the layout of the moment, the slide would take only its
     * own progress' share of that movement, which leaves an item that the two move in opposite directions overshooting
     * and coming back. Measured from the first, the item moves by the slide and by all of the growth, which run on one
     * spring, so it travels once, straight to where it ends.
     *
     * Null until the grid has measured the change: the state that starts it has the items' layers read it at once, still
     * over the layout from before, where every item is where it was and has nothing to slide from.
     *
     * Worked out once per layout rather than once per item, since every visible item asks while the change runs.
     */
    fun offsetOf(listState: LazyGridState, key: Any): IntOffset? = measuredLayoutInfo(listState)?.let { offsets[key] }

    /** The layout of the moment, recorded, or null while it is still the one from before the change. */
    private fun measuredLayoutInfo(listState: LazyGridState): LazyGridLayoutInfo? {
        val currentLayoutInfo = listState.layoutInfo
        if (currentLayoutInfo === previousLayoutInfo) return null
        if (currentLayoutInfo !== layoutInfo) {
            layoutInfo = currentLayoutInfo
            val newItems = currentLayoutInfo.visibleItemsInfo.filter { it.key !in offsets && it.key !in previousOffsets }
            currentLayoutInfo.visibleItemsInfo.forEach { offsets.getOrPut(it.key) { it.offset } }
            if (listedAfter != null && newItems.isNotEmpty()) recordArrivals(currentLayoutInfo, newItems)
            if (listedAfter != null) recordDepartures(currentLayoutInfo, listedAfter)
        }
        return currentLayoutInfo
    }

    /** The items pushed off screen that the layout of the moment does not show, and how far up each travels. */
    fun departuresOf(listState: LazyGridState): List<Pair<AnchorSnapshot, Int>> {
        if (departing.isEmpty()) return emptyList()
        val visibleKeys = measuredLayoutInfo(listState)?.visibleItemsInfo?.mapTo(HashSet()) { it.key } ?: return emptyList()
        return departureShifts.mapNotNull { (key, shift) ->
            if (shift == null || key in visibleKeys) null else departing[key]?.let { it to shift }
        }
    }

    /**
     * Decided once for each item, in the first layout that does not show it, against the offsets the items still shown
     * were first placed at, as the arrivals are. One no longer listed (a header an open search takes away, a random pick)
     * did not leave by an edge and is gone at once.
     */
    private fun recordDepartures(layoutInfo: LazyGridLayoutInfo, listedAfter: (Any) -> Boolean) {
        val visibleKeys = layoutInfo.visibleItemsInfo.mapTo(HashSet()) { it.key }
        val left = previousLayoutInfo.visibleItemsInfo.filter { it.key !in visibleKeys && it.key !in departureShifts && it.key in departing }
        if (left.isEmpty()) return
        val shifts = previousLayoutInfo.visibleItemsInfo.mapNotNull { item ->
            offsets[item.key]?.takeIf { item.key in visibleKeys }?.let { offset -> ItemShift(index = item.index, shift = item.offset.y - offset.y) }
        }
        left.forEach { item ->
            departureShifts[item.key] = if (!listedAfter(item.key)) {
                null
            } else {
                departureShift(
                    index = item.index,
                    top = item.offset.y,
                    height = item.size.height,
                    shifts = shifts,
                    viewportStart = layoutInfo.viewportStartOffset,
                    viewportEnd = layoutInfo.viewportEndOffset,
                )
            }
        }
    }

    /** How the item with [key], which was not on screen before the change, travels to its place, if it does. */
    fun arrivalOf(listState: LazyGridState, key: Any): ItemArrival? = offsetOf(listState, key)?.let { arrivals[key] }

    /** Like the offsets, kept from the layout an arrival is first placed in, measured against the items' first ones. */
    private fun recordArrivals(layoutInfo: LazyGridLayoutInfo, newItems: List<LazyGridItemInfo>) {
        val shifts = layoutInfo.visibleItemsInfo.mapNotNull { item ->
            previousOffsets[item.key]?.let { previous -> ItemShift(index = item.index, shift = previous.y - offsets.getValue(item.key).y) }
        }
        newItems.forEach { item ->
            arrivalShift(
                index = item.index,
                top = item.offset.y,
                height = item.size.height,
                shifts = shifts,
                previousViewportStart = previousLayoutInfo.viewportStartOffset,
                previousViewportEnd = previousLayoutInfo.viewportEndOffset,
            )?.let { arrivals[item.key] = it }
        }
    }
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
 *
 * @param placementSpec Null for a list whose rows only ever move because a row above them folds out or away: they
 *   already follow that fold frame by frame, and a placement animation on top of it would spring after every one of
 *   those frames and settle again once the fold had finished.
 */
@Composable
internal fun LazyItemScope.listItemAnimation(
    listState: ScrollableState,
    placementSpec: FiniteAnimationSpec<IntOffset>? = ITEM_PLACEMENT_SPEC,
) = Modifier.animateItem(
    fadeInSpec = ITEM_FADE_SPEC,
    placementSpec = if (listState.isScrollInProgress) null else placementSpec,
    fadeOutSpec = ITEM_FADE_SPEC,
)

/** The fade [Modifier.animateItem] uses by default. */
private val ITEM_FADE_SPEC = spring<Float>(stiffness = Spring.StiffnessMediumLow)

/** The placement animation [Modifier.animateItem] uses by default. */
private val ITEM_PLACEMENT_SPEC = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)
