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

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

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
 * The placement animation of a lazy list's item.
 *
 * An item sliding into its new slot says that the list the user is looking at has changed: a song was renamed,
 * deleted, filtered out, or a different sorting order moved it. The library arriving is not that kind of change
 * ([isEnabled], which the song and setlist lists answer with [rememberHasLoadedLibrary]), and neither is a scroll —
 * hence [listState], which turns the animations off for as long as the list is moving.
 *
 * A lazy list keeps the animation state of the items it has on screen and moves each of them by the distance the
 * list itself was scrolled, so that scrolling is not mistaken for the list rearranging itself. The two numbers come
 * apart at the ends of the list, where a fast scroll asks for more than there is left to give: an item that was on
 * screen for both of the last two frames is moved by the distance that was *asked* for rather than the shorter one
 * that was actually scrolled, lands far outside the list, and is then animated back to where it belongs. What that
 * looks like is a single row sliding in from off screen while every other row is already still, most visibly the
 * first row of a list that was flung back to the top.
 *
 * Nothing is lost by leaving the animations off while the list moves, since they are there to narrate a change of
 * its *contents* and the contents do not change under a finger. Whatever does change mid-scroll simply takes its
 * place, and the next change with the list at rest animates as it always did.
 */
@Composable
internal fun LazyItemScope.listItemAnimation(listState: ScrollableState, isEnabled: Boolean = true) =
    if (isEnabled && !listState.isScrollInProgress) Modifier.animateItem() else Modifier

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
) = if (isEnabled && (isRearranging || !listState.isScrollInProgress)) Modifier.animateItem() else Modifier
