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

import kotlin.test.Test
import kotlin.test.assertEquals

class SearchCardHoldTest {

    private val card = HeldSearchCard(index = 7, offset = -12, filterKey = "results", itemKey = "card", transitionNumber = 3)

    private fun next(
        filterKey: String = "results",
        transitionNumber: Int = 3,
        isSearchOpen: Boolean = true,
        progress: Float? = 0.5f,
        isScrollInProgress: Boolean = false,
        indexOf: (Any) -> Int? = { null },
    ) = card.next(
        filterKey = filterKey,
        transitionNumber = transitionNumber,
        isSearchOpen = isSearchOpen,
        progress = progress,
        isScrollInProgress = isScrollInProgress,
        indexOf = indexOf,
    )

    @Test
    fun `the same list keeps the card held while the narration runs`() = assertEquals(SearchCardHoldStep.Hold(card), next())

    @Test
    fun `results re-ranked under an open search let go and stop the narration`() =
        assertEquals(SearchCardHoldStep.Release(stopsTransition = true), next(filterKey = "other results"))

    @Test
    fun `a list that a newer narration arrived with lets go and leaves that narration running`() =
        assertEquals(SearchCardHoldStep.Release(stopsTransition = false), next(filterKey = "tag", transitionNumber = 4))

    @Test
    fun `the full list arriving after closing holds the card at its index in it`() = assertEquals(
        SearchCardHoldStep.Hold(card.copy(index = 21, filterKey = "everything")),
        next(filterKey = "everything", isSearchOpen = false, indexOf = { key -> if (key == "card") 21 else null }),
    )

    @Test
    fun `the full list arriving after closing without the card lets go`() =
        assertEquals(SearchCardHoldStep.Release(stopsTransition = false), next(filterKey = "everything", isSearchOpen = false))

    @Test
    fun `a scroll lets go`() = assertEquals(SearchCardHoldStep.Release(stopsTransition = false), next(isScrollInProgress = true))

    @Test
    fun `the end of the narration lets go`() = assertEquals(SearchCardHoldStep.Release(stopsTransition = false), next(progress = null))
}
