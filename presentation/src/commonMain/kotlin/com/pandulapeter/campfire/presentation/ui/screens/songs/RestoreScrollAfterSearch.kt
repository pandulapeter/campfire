/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import com.pandulapeter.campfire.presentation.ui.components.list.ListAnchor
import kotlinx.coroutines.flow.filter

/**
 * Brings the song list back to where it was when the search opened, once it closes over the same songs with the list not
 * scrolled in between. The grid alone would keep the card that was first in sight where it is, and the headers coming
 * back above it would leave the list scrolled down by their height.
 *
 * Asking for the old position is a jump, which makes the grid forget where its items were, so the change is narrated by
 * [anchor] instead, as a tick in the selection is ([KeepCardsInPlaceUnderTick]): what was on screen slides from where
 * it was, and the headers fade in. Called after [KeepCardsInPlaceUnderTick], so that its request is the one the grid
 * measures with.
 */
@Composable
internal fun RestoreScrollAfterSearch(
    listState: LazyGridState,
    isSearchOpen: Boolean,
    songGroups: SongGroups,
    anchor: ListAnchor,
) {
    val opening = remember { arrayOfNulls<SearchOpening>(1) }
    val wasOpen = remember { booleanArrayOf(isSearchOpen) }
    val coroutineScope = rememberCoroutineScope()
    SideEffect {
        if (isSearchOpen == wasOpen[0]) return@SideEffect
        wasOpen[0] = isSearchOpen
        if (isSearchOpen) {
            opening[0] = SearchOpening(songGroups, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            return@SideEffect
        }
        val last = opening[0] ?: return@SideEffect
        opening[0] = null
        if (last.songGroups != songGroups || listState.isScrollInProgress) return@SideEffect
        // Asked for even when the numbers are the ones the grid is at, which is the usual case at the top of the list:
        // the request makes the grid hold that index rather than follow its first card's key down below the returning
        // header. Such a request is no jump, so the grid's own item animation is left to it.
        val isJump = last.index != listState.firstVisibleItemIndex || last.offset != listState.firstVisibleItemScrollOffset
        val visibleOffsets = listState.layoutInfo.visibleItemsInfo.associate { it.key to it.offset }
        listState.requestScrollToItem(index = last.index, scrollOffset = last.offset)
        if (isJump) anchor.animateFrom(visibleOffsets, coroutineScope)
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { opening[0] = null }
    }
}

/** The songs and the scroll position the search opened over. */
private class SearchOpening(val songGroups: SongGroups, val index: Int, val offset: Int)
