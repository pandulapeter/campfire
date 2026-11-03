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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.pandulapeter.campfire.presentation.ui.components.list.ScrollToTopWhenChanged
import com.pandulapeter.campfire.presentation.ui.state.songs.SongGroup
import com.pandulapeter.campfire.presentation.ui.state.songs.SongGroups

/**
 * Shows a sort picked from the menu at work where the random picks that lead the list would hide it: they are drawn
 * whatever the order, so on a short screen they are all the sort would leave in sight. Wherever it is picked, the list
 * ends on the new order's first section: one read from its very top glides down to it once the new order arrives,
 * one scrolled anywhere else is put straight onto it ([topIndex]) rather than sent up over the picks, as
 * [ScrollToTopWhenChanged] would send it. The sort already applied, picked again while the picks are in sight, glides
 * there too; picked once the first section's header has reached the top, it moves nothing, the reader being in the
 * sections already. A sort that arrives any other way than from the menu moves nothing.
 */
internal class SortChangeReveal {

    /** A sort picked from the menu whose order has not arrived yet: the [orderKey] the list had when it was picked. */
    private class Pending(val orderKey: String, val isGlide: Boolean)

    private var pending: Pending? = null

    /** A glide to the first section that no new order will start, the sort picked being the one already applied. */
    class Reselection(val index: Int)

    /** The latest [Reselection], for [RevealSortChange] to glide by; a new instance for every pick, so each one glides. */
    var reselection by mutableStateOf<Reselection?>(null)
        private set

    /**
     * For a sort picked from the menu out of [songGroups], before it is written. [isRevealed] is false under an open
     * search, whose list has no picks; [isReselected] is true for the sort already applied, which changes no order.
     */
    fun onSortPicked(listState: LazyGridState, songGroups: SongGroups, isRevealed: Boolean, isReselected: Boolean) {
        pending = null
        if (!isRevealed) return
        if (isReselected) {
            // Only while the first section's header has not reached the top, where it would stick: before then the picks
            // are in sight, after it the reader is in the sections already.
            songGroups.groups.firstSectionAfterRandomPicksIndex()?.takeIf { listState.firstVisibleItemIndex < it }?.let { reselection = Reselection(it) }
        } else {
            pending = Pending(orderKey = songGroups.orderKey, isGlide = listState.isAtTop)
        }
    }

    /**
     * Where the list built for [orderKey] out of [groups] starts, for [ScrollToTopWhenChanged] to put it there as the
     * order arrives: the first section for a sort picked below the very top, the top otherwise.
     */
    fun topIndex(orderKey: String, groups: List<SongGroup>): Int {
        val pending = pending?.takeIf { !it.isGlide && it.orderKey != orderKey } ?: return 0
        this.pending = null
        return groups.firstSectionAfterRandomPicksIndex() ?: 0
    }

    /** Whether the list built for [orderKey] is the one a sort picked over the very top led to; asked once. */
    fun takeGlide(orderKey: String): Boolean {
        val pending = pending?.takeIf { it.isGlide && it.orderKey != orderKey } ?: return false
        this.pending = null
        return true
    }
}

private val LazyGridState.isAtTop get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

/** Glides the list down to its first section when [reveal] says the new order of [songGroups] calls for it. */
@Composable
internal fun RevealSortChange(
    reveal: SortChangeReveal,
    listState: LazyGridState,
    songGroups: SongGroups,
) {
    LaunchedEffect(reveal.reselection) {
        reveal.reselection?.let { listState.animateScrollToItem(it.index) }
    }
    LaunchedEffect(songGroups.orderKey) {
        if (!reveal.takeGlide(songGroups.orderKey)) return@LaunchedEffect
        val index = songGroups.groups.firstSectionAfterRandomPicksIndex() ?: return@LaunchedEffect
        // A frame first, so that the scroll to the top the new order asked for is laid out before this one starts.
        withFrameNanos {}
        listState.animateScrollToItem(index)
    }
}
