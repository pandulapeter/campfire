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
import androidx.compose.runtime.rememberCoroutineScope
import com.pandulapeter.campfire.presentation.ui.components.list.ListAnchor

/**
 * Holds the cards of the song list where they are on screen while the Selection section above them changes: a tick
 * adds a copy to its end, and the first tick of a session, like the end of the mode, puts a header and a divider above
 * everything or takes them away. A lazy grid keeps its first visible item in place by key, which only helps while that
 * item is below the section; with the section or its header at the top of the viewport, everything after it would be
 * pushed down a row. So, in the composition the new order arrives in, the grid is asked to keep the first card whose
 * top is in sight exactly where it is, by its index in the new groups, the content above it moving out of the way, as
 * the Choose songs sheet keeps its rows ([com.pandulapeter.campfire.presentation.ui.components.checklist.KeepChecklistRowsInPlace]).
 *
 * A request for a different position is a jump to the grid, which answers it by forgetting where every item was: the
 * copy that joins the section, and the header and the divider the first tick brings, would simply be there, and the
 * rows of the section above the held card would snap up rather than slide. So the change is narrated by [anchor]
 * instead, as a tag tapped on a row narrates its filter
 * ([com.pandulapeter.campfire.presentation.ui.components.list.anchoredTransition]): what was on screen slides from where it
 * was, and what was not fades in. A request for the position the grid is already at resets nothing, and the grid's own
 * item animation is left to it.
 *
 * Called after [com.pandulapeter.campfire.presentation.ui.components.list.ScrollToTopWhenChanged], so that its request is
 * the one the grid measures with. A change of the search, the sorting or a filter is that one's to answer, and a list
 * being scrolled is left alone, since a request would cancel the scroll.
 */
@Composable
internal fun KeepCardsInPlaceUnderTick(
    listState: LazyGridState,
    songGroups: SongGroups,
    hasPlaceholder: Boolean,
    anchor: ListAnchor,
) {
    val previous = remember { arrayOfNulls<SongGroups>(1) }
    val coroutineScope = rememberCoroutineScope()
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
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val cards = visibleItems.filter { it.contentType == SONG_CONTENT_TYPE }
            .sortedWith(compareBy({ it.key in lastSelectionKeys }, { it.offset.y < 0 }))
        cards.firstNotNullOfOrNull { card ->
            songGroups.groups.itemIndexOf(card.key, hasPlaceholder = hasPlaceholder)?.let { index -> index to card.offset.y }
        }?.let { (index, offset) ->
            val isJump = index != listState.firstVisibleItemIndex || -offset != listState.firstVisibleItemScrollOffset
            listState.requestScrollToItem(index = index, scrollOffset = -offset)
            if (isJump) {
                anchor.animateFrom(visibleItems.associate { it.key to it.offset }, coroutineScope)
            }
        }
    }
}
