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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import com.pandulapeter.campfire.presentation.ui.components.list.ListAnchor

/**
 * The position of the card [KeepCardsInPlaceAcrossSearch] keeps in place, asked for again every time the grid is measured
 * while the narration of the change runs ([holdingSearchCard]), and let go of at its end or at the first scroll. A list
 * that arrives meanwhile is followed ([follow]).
 */
internal class SearchCardHold {
    private var card: HeldSearchCard? = null

    fun hold(card: HeldSearchCard) {
        this.card = card
    }

    /**
     * Follows the hold into the list a composition brought, before the grid measures it: called from a side effect
     * that runs after [com.pandulapeter.campfire.presentation.ui.components.list.ScrollToTopWhenChanged]'s, so that a
     * release leaves its request for the top standing, and a hold kept by key overrides it.
     */
    fun follow(
        filterKey: String,
        isSearchOpen: Boolean,
        anchor: ListAnchor,
        listState: LazyGridState,
        indexOf: (Any) -> Int?,
    ) {
        val card = card ?: return
        step(
            card.next(
                filterKey = filterKey,
                transitionNumber = anchor.transitionNumber,
                isSearchOpen = isSearchOpen,
                progress = anchor.transitionProgress,
                isScrollInProgress = listState.isScrollInProgress,
                indexOf = indexOf,
            ),
            anchor = anchor,
        )
    }

    fun apply(listState: LazyGridState, anchor: ListAnchor) {
        // Read whether or not a card is held, so that the narration that starts with a hold measures the grid again.
        val progress = anchor.transitionProgress
        val card = card ?: return
        // The list measured is the one the last composition's follow saw, so the card's own key is the current one.
        val next = card.next(
            filterKey = card.filterKey,
            transitionNumber = card.transitionNumber,
            isSearchOpen = true,
            progress = progress,
            isScrollInProgress = listState.isScrollInProgress,
            indexOf = { null },
        )
        step(next, anchor)
        if (next is SearchCardHoldStep.Hold) listState.requestScrollToItem(index = next.card.index, scrollOffset = next.card.offset)
    }

    private fun step(next: SearchCardHoldStep, anchor: ListAnchor) {
        when (next) {
            is SearchCardHoldStep.Hold -> card = next.card
            is SearchCardHoldStep.Release -> {
                card = null
                if (next.stopsTransition) anchor.stopTransition()
            }
        }
    }
}

/**
 * A card [SearchCardHold] keeps at [index] and [offset], with what the hold was set for: the list ([filterKey]), the
 * card ([itemKey]), and the transition of the [ListAnchor] that narrates the jump ([transitionNumber]).
 */
internal data class HeldSearchCard(
    val index: Int,
    val offset: Int,
    val filterKey: String,
    val itemKey: Any,
    val transitionNumber: Int,
)

/** What [SearchCardHold] does next: keep holding [Hold.card], or let go, stopping the narration with it or not. */
internal sealed interface SearchCardHoldStep {
    data class Hold(val card: HeldSearchCard) : SearchCardHoldStep

    data class Release(val stopsTransition: Boolean) : SearchCardHoldStep
}

/**
 * Whether the card is still held once the list built for [filterKey] is shown, while the [ListAnchor]'s transition
 * [transitionNumber] runs at [progress] (null for none).
 *
 * A narration that ended or a scroll lets go. So does a list built for anything else, in one of three ways. A newer
 * transition is a tag or a language tapped on a row, which narrates its own change around its row in the same frame: the
 * hold lets go and leaves that narration alone. An open search has re-ranked its results under a query typed or pasted
 * meanwhile, whose best matches are at the top the list is asked to go to: the hold lets go and stops the narration,
 * which tells of a layout that is gone. A closed search has just brought the full list, which arrives frames after the
 * query is emptied, so the index held is one into the results: the card is held at its index in the new list
 * ([indexOf]), and let go of where it is not in it.
 */
internal fun HeldSearchCard.next(
    filterKey: String,
    transitionNumber: Int,
    isSearchOpen: Boolean,
    progress: Float?,
    isScrollInProgress: Boolean,
    indexOf: (Any) -> Int?,
): SearchCardHoldStep = when {
    progress == null || isScrollInProgress -> SearchCardHoldStep.Release(stopsTransition = false)
    filterKey == this.filterKey -> SearchCardHoldStep.Hold(this)
    transitionNumber != this.transitionNumber -> SearchCardHoldStep.Release(stopsTransition = false)
    isSearchOpen -> SearchCardHoldStep.Release(stopsTransition = true)
    else -> indexOf(itemKey)?.let { index -> SearchCardHoldStep.Hold(copy(index = index, filterKey = filterKey)) }
        ?: SearchCardHoldStep.Release(stopsTransition = false)
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
