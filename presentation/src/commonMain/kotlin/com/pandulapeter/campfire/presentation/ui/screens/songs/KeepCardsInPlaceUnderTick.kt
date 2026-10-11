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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/**
 * Holds the cards of the song list where they are on screen while the Selection section above them changes: a tick
 * adds a copy to its end, and the first tick of a session, like the end of the mode, puts a header and a divider above
 * everything or takes them away. A lazy grid keeps its first visible item in place by key, which only helps while that
 * item is below the section; with the section or its header at the top of the viewport, everything after it would be
 * pushed down a row. So, in the composition the new order arrives in, the grid is asked to keep the first card whose
 * top is in sight exactly where it is, by its index in the new groups, the content above it moving out of the way, as
 * the Choose songs sheet keeps its rows ([com.pandulapeter.campfire.presentation.ui.components.KeepChecklistRowsInPlace]).
 *
 * Called after [com.pandulapeter.campfire.presentation.ui.components.ScrollToTopWhenChanged], so that its request is
 * the one the grid measures with. A change of the search, the sorting or a filter is that one's to answer, and a list
 * being scrolled is left alone, since a request would cancel the scroll.
 */
@Composable
internal fun KeepCardsInPlaceUnderTick(
    listState: LazyGridState,
    songGroups: SongGroups,
    hasPlaceholder: Boolean,
) {
    val previous = remember { arrayOfNulls<SongGroups>(1) }
    SideEffect {
        val last = previous[0]
        previous[0] = songGroups
        if (last == null || last === songGroups || last.filterKey != songGroups.filterKey || listState.isScrollInProgress) return@SideEffect
        val lastSelection = last.groups.selectionItemKeys()
        if (lastSelection == songGroups.groups.selectionItemKeys()) return@SideEffect
        val lastSelectionKeys = lastSelection.toHashSet()
        // The cards outside the section are held first, since the end of the mode moves the section's own back into
        // their sections. Of those, a card the list has scrolled past the top is still among the visible
        // items, and holding by it would let a row the change put under it push every card on screen down, so the
        // first card whose top is in sight is what is held. A card the change took away (a copy) is not in the new
        // groups, and the next one stands in for it.
        val cards = listState.layoutInfo.visibleItemsInfo.filter { it.contentType == "song" }
            .sortedWith(compareBy({ it.key in lastSelectionKeys }, { it.offset.y < 0 }))
        cards.firstNotNullOfOrNull { card ->
            songGroups.groups.itemIndexOf(card.key, hasPlaceholder = hasPlaceholder)?.let { index -> index to card.offset.y }
        }?.let { (index, offset) -> listState.requestScrollToItem(index = index, scrollOffset = -offset) }
    }
}
