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
import androidx.compose.runtime.withFrameNanos
import com.pandulapeter.campfire.presentation.ui.components.list.ScrollToTopWhenChanged

/**
 * Shows a sort picked from the menu at work where the random picks that lead the list would hide it: they are drawn
 * whatever the order, so on a short screen they are all the sort would leave in sight. A list read from its very top
 * glides down to its first section once the new order arrives. A list still resting where that glide left it, nobody
 * having scrolled it since, is put straight onto the new order's first section ([topIndex]) rather than sent back
 * up over the picks to glide down again. A list scrolled anywhere else goes to the top as the sort sends it
 * ([ScrollToTopWhenChanged]), and a sort that arrives any other way than from the menu moves nothing.
 */
internal class SortChangeReveal {

    /** A sort picked from the menu whose order has not arrived yet: the [orderKey] the list had when it was picked. */
    private class Pending(val orderKey: String, val isGlide: Boolean)

    private var pending: Pending? = null

    /** Where the last reveal left the list, as its first visible item and that item's offset. */
    private var revealedAt: Pair<Int, Int>? = null

    /**
     * For a sort picked from the menu, before it is written. [isRevealed] is false for the sort already applied, which
     * changes nothing, and under an open search, whose list has no picks.
     */
    fun onSortPicked(listState: LazyGridState, orderKey: String, isRevealed: Boolean) {
        val position = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        pending = when {
            !isRevealed -> null
            position == (0 to 0) -> Pending(orderKey = orderKey, isGlide = true)
            position == revealedAt -> Pending(orderKey = orderKey, isGlide = false)
            else -> null
        }
    }

    /**
     * Where the list built for [orderKey] out of [groups] starts, for [ScrollToTopWhenChanged] to put it there as the
     * order arrives: the first section for a sort picked over a list resting where the last reveal left it, the top
     * otherwise.
     */
    fun topIndex(orderKey: String, groups: List<SongGroup>): Int {
        val pending = pending?.takeIf { !it.isGlide && it.orderKey != orderKey } ?: return 0
        this.pending = null
        val index = groups.firstSectionAfterRandomPicksIndex() ?: return 0
        revealedAt = index to 0
        return index
    }

    /** Whether the list built for [orderKey] is the one a sort picked over the very top led to; asked once. */
    fun takeGlide(orderKey: String): Boolean {
        val pending = pending?.takeIf { it.isGlide && it.orderKey != orderKey } ?: return false
        this.pending = null
        return true
    }

    /** Where the glide left [listState], so that the next sort picked over it, untouched, is revealed too. */
    fun onGlided(listState: LazyGridState) {
        revealedAt = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
    }
}

/** Glides the list down to its first section when [reveal] says the new order of [songGroups] calls for it. */
@Composable
internal fun RevealSortChange(
    reveal: SortChangeReveal,
    listState: LazyGridState,
    songGroups: SongGroups,
) {
    LaunchedEffect(songGroups.orderKey) {
        if (!reveal.takeGlide(songGroups.orderKey)) return@LaunchedEffect
        val index = songGroups.groups.firstSectionAfterRandomPicksIndex() ?: return@LaunchedEffect
        // A frame first, so that the scroll to the top the new order asked for is laid out before this one starts.
        withFrameNanos {}
        listState.animateScrollToItem(index)
        reveal.onGlided(listState)
    }
}
