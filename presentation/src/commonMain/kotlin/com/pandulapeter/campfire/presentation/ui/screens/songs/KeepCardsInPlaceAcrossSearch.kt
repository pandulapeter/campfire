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

import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import com.pandulapeter.campfire.presentation.ui.components.appBar.AppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.appBar.LIST_APP_BAR_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.appBar.searchTravelSpec
import com.pandulapeter.campfire.presentation.ui.components.list.ListAnchor
import com.pandulapeter.campfire.presentation.ui.components.list.SECTION_START_ROOM
import com.pandulapeter.campfire.presentation.ui.components.list.SECTION_HEADER_ROW_HEIGHT
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt

/**
 * What the song list looked like in the composition that opened the search: the card at the top left of what could be
 * read, and where it was. Read off the layout the search opens over, while composing, because the groups that keep
 * that card in its column ([withoutSectionHeaders]) are built in that same composition. Null while the search is closed,
 * and when it opens over no card.
 */
@Composable
internal fun rememberSearchOpening(
    listState: LazyGridState,
    songGroups: SongGroups,
    isSearchOpen: Boolean,
    columnCount: Int,
): SearchOpening? {
    val headerRowHeightPx = with(LocalDensity.current) { SECTION_HEADER_ROW_HEIGHT.roundToPx() }
    return remember(isSearchOpen) {
        if (!isSearchOpen) return@remember null
        // Not observed: the opening is a picture of one moment, and the list scrolling on must not compose it again.
        Snapshot.withoutReadObservation {
            val layoutInfo = listState.layoutInfo
            // The pinned header's row covers the cards under it, so the one the eye is on is the first below it.
            layoutInfo.visibleItemsInfo.topLeftCard(visibleTop = headerRowHeightPx - layoutInfo.beforeContentPadding)?.let { card ->
                songGroups.groups.slotOf(card.key, columnCount)?.let { slot ->
                    SearchOpening(
                        songGroups = songGroups,
                        slot = slot,
                        cardOffset = card.offset.y,
                    )
                }
            }
        }
    }
}

/**
 * Keeps the card at the top left of the song list where it is on screen as the search opens and closes, while the
 * headers leave or come back and the cards around it close up on it or move back into their sections.
 *
 * Opening, the groups have kept the card in its column ([SearchOpening.slot]), and only its row is left to keep. The app
 * bar fills in at the same time and moves the list down by the room a pinned header's row took ([SEARCH_LIST_SHIFT]), so
 * the card's row is asked for that much higher in the list, and the jump is narrated by [anchor] on the bar's own spring
 * ([searchTravelSpec]): every card slides from where it was by as much as the list moves under it, and the card stands
 * still. A request is a jump the grid forgets every item animation over, which leaves the headers to vanish at once
 * rather than fade; the grid's own animation would keep the first row it has by key, moving the card down with the bar.
 *
 * Closing over the songs it opened over, the list neither scrolled nor shown other songs since, the card goes back to
 * where it was as the search opened, which is the position the list was at; otherwise the top left card's row is kept the same way, though the card may
 * have to move to the column its section gives it. Asked for even when the numbers are the ones the grid is at: the
 * request makes the grid hold that index rather than follow its first card's key down below a returning header. Such a
 * request is no jump, so the grid's own item animation is left to it.
 *
 * The cards above the one kept grow or shrink by the artist line an open search shows on them meanwhile, and a grid
 * keeps the first row it shows rather than the card, so the position is asked for again as long as the jump is narrated
 * ([hold]).
 *
 * Called after [KeepCardsInPlaceUnderTick], so that its request is the one the grid measures with.
 */
@Composable
internal fun KeepCardsInPlaceAcrossSearch(
    listState: LazyGridState,
    isSearchOpen: Boolean,
    songGroups: SongGroups,
    groups: List<SongGroup>,
    opening: SearchOpening?,
    hasPlaceholder: Boolean,
    appBarOverlap: () -> AppBarOverlap,
    anchor: ListAnchor,
    hold: SearchCardHold,
) {
    val state = remember { SearchTransitionState(wasOpen = isSearchOpen) }
    val coroutineScope = rememberCoroutineScope()
    val listShiftPx = with(LocalDensity.current) { SEARCH_LIST_SHIFT.toPx() }
    SideEffect {
        // A search that has shown other songs since it opened has sent the list to the top, and its gap with it.
        if (state.opening?.songGroups?.filterKey != songGroups.filterKey) state.opening?.hasShownOtherSongs = true
        if (isSearchOpen == state.wasOpen) return@SideEffect
        state.wasOpen = isSearchOpen
        val layoutInfo = listState.layoutInfo
        val visibleOffsets = layoutInfo.visibleItemsInfo.associate { it.key to it.offset }
        // How far down the list moves under the app bar as it fills in or empties: by all of the shift opening, and closing
        // only back to a list with headers, since a headerless one stays below the filled-in bar.
        val coverage = appBarOverlap().coverage
        val targetCoverage = if (isSearchOpen || hasPlaceholder || groups.none { it.header != null }) 0f else 1f
        val listShift = (listShiftPx * (coverage - targetCoverage)).roundToInt()
        fun keep(index: Int, offset: Int) {
            val isJump = index != listState.firstVisibleItemIndex || offset != listState.firstVisibleItemScrollOffset
            listState.requestScrollToItem(index = index, scrollOffset = offset)
            if (isJump) {
                anchor.animateFrom(listState, visibleOffsets, coroutineScope, animationSpec = searchTravelSpec())
                hold.hold(index = index, offset = offset)
            }
        }
        if (isSearchOpen) {
            state.opening = opening
            if (opening == null || listState.isScrollInProgress) return@SideEffect
            groups.itemIndexOf(opening.slot.key, hasPlaceholder)?.let { index -> keep(index, listShift - opening.cardOffset) }
            return@SideEffect
        }
        val last = state.opening
        state.opening = null
        if (listState.isScrollInProgress) return@SideEffect
        if (last != null && !last.hasBeenScrolled && !last.hasShownOtherSongs && last.songGroups == songGroups) {
            groups.itemIndexOf(last.slot.key, hasPlaceholder)?.let { index -> keep(index, -last.cardOffset) }
        } else {
            val card = layoutInfo.visibleItemsInfo.topLeftCard(visibleTop = 0) ?: return@SideEffect
            groups.itemIndexOf(card.key, hasPlaceholder)?.let { index -> keep(index, listShift - card.offset.y) }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { state.opening?.hasBeenScrolled = true }
    }
}

/**
 * How far the song list moves down as the app bar fills in for the search: the bar's height less the room the list
 * keeps above a section's start while it is under the bar's buttons ([SECTION_START_ROOM]), which is a pinned header's row.
 */
private val SEARCH_LIST_SHIFT = LIST_APP_BAR_HEIGHT - SECTION_START_ROOM

/**
 * The songs the search opened over, and the card at the top left of them and where it was ([rememberSearchOpening]).
 *
 * @property cardOffset Where the card was laid out, in the grid's content coordinates.
 * @property hasBeenScrolled Whether the list has been scrolled since, which leaves the card's old place nobody's.
 * @property hasShownOtherSongs Whether the search has shown other songs since, which sent the list to the top: neither
 *   the gap that kept the card in its column ([slotFor]) nor the card's old place is brought back by a query cleared
 *   again, and closing keeps whichever card is at the top left then.
 */
internal class SearchOpening(
    val songGroups: SongGroups,
    val slot: SongCardSlot,
    val cardOffset: Int,
) {
    var hasBeenScrolled = false
    var hasShownOtherSongs = false

    /** The card the results of [current] keep in its column, if they are still the ones it was picked for. */
    fun slotFor(current: SongGroups, columnCount: Int) = slot.takeIf {
        !hasShownOtherSongs && current.filterKey == songGroups.filterKey && it.columnCount == columnCount
    }
}

/**
 * The position of the card [KeepCardsInPlaceAcrossSearch] keeps in place, asked for again every time the grid is measured
 * while the narration of the change runs ([holdingSearchCard]), and let go of at its end or at the first scroll.
 */
internal class SearchCardHold {
    private var index = -1
    private var offset = 0

    fun hold(index: Int, offset: Int) {
        this.index = index
        this.offset = offset
    }

    fun apply(listState: LazyGridState, anchor: ListAnchor) {
        // Read whether or not a card is held, so that the narration that starts with a hold measures the grid again.
        val progress = anchor.transitionProgress
        if (index < 0) return
        if (progress == null || listState.isScrollInProgress) {
            index = -1
        } else {
            listState.requestScrollToItem(index = index, scrollOffset = offset)
        }
    }
}

/**
 * Asks for the position [hold] keeps before the grid it is applied to is measured. The narration's progress is read in
 * the measuring, so it measures again on every frame of the narration, as the cards above the held one change height.
 */
internal fun Modifier.holdingSearchCard(hold: SearchCardHold, listState: LazyGridState, anchor: ListAnchor) = layout { measurable, constraints ->
    hold.apply(listState, anchor)
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

private class SearchTransitionState(var wasOpen: Boolean) {
    var opening: SearchOpening? = null
}

/** The first card more than half of which is below [visibleTop], in the grid's content coordinates. */
private fun List<LazyGridItemInfo>.topLeftCard(visibleTop: Int) =
    firstOrNull { it.contentType == SONG_CONTENT_TYPE && it.offset.y + it.size.height / 2 >= visibleTop }
