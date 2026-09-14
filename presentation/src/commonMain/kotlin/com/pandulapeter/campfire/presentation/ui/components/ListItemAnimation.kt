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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.flow.filter

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
 * @param contents What the grid is built from, compared by identity: a new instance is a change of the list.
 */
@Composable
internal fun ScrollToTopWhenChanged(
    listState: LazyGridState,
    key: String,
    contents: Any?,
) {
    var lastScrollToTopKey by rememberSaveable { mutableStateOf(key) }
    val heldTop = remember { HeldTop(contents) }
    // A side effect rather than a launched one, because it runs before the grid measures what this composition gave
    // it: a request made a frame later would come after the grid had already followed its first row down.
    SideEffect {
        if (key != lastScrollToTopKey) {
            lastScrollToTopKey = key
            heldTop.isHolding = true
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
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { heldTop.isHolding = false }
    }
}

/** What [ScrollToTopWhenChanged] remembers between compositions, none of which is ever drawn. */
private class HeldTop(var contents: Any?) {
    var isHolding = false
}

/**
 * The placement animation of a lazy list's item.
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
 */
@Composable
internal fun LazyItemScope.listItemAnimation(listState: ScrollableState, isEnabled: Boolean = true) = if (isEnabled) {
    Modifier.animateItem(
        fadeInSpec = ITEM_FADE_SPEC,
        placementSpec = if (listState.isScrollInProgress) null else ITEM_PLACEMENT_SPEC,
        fadeOutSpec = ITEM_FADE_SPEC,
    )
} else {
    Modifier
}

/**
 * The [listItemAnimation] of an item of the song or setlist grid.
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
) = if (isEnabled) {
    Modifier.animateItem(
        fadeInSpec = ITEM_FADE_SPEC,
        placementSpec = if (isRearranging || !listState.isScrollInProgress) ITEM_PLACEMENT_SPEC else null,
        fadeOutSpec = ITEM_FADE_SPEC,
    )
} else {
    Modifier
}

/** The fade [Modifier.animateItem] uses by default. */
private val ITEM_FADE_SPEC = spring<Float>(stiffness = Spring.StiffnessMediumLow)

/** The placement animation [Modifier.animateItem] uses by default. */
private val ITEM_PLACEMENT_SPEC = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)
