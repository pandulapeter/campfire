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

import kotlin.test.Test
import kotlin.test.assertEquals

class PinnedSearchTopEdgeTest {

    @Test
    fun `a page at rest draws nothing over the room under the status bar`() =
        assertEquals(PinnedSearchTopEdge(fade = 0f, fadeBelowInset = 0f, cover = 0f), edge(firstVisibleItemIndex = 0, scrollOffset = 0, fieldTop = 300))

    @Test
    fun `the fade grows through the room as the controls are scrolled up into it`() {
        assertEquals(PinnedSearchTopEdge(fade = 0.5f, fadeBelowInset = 0.5f, cover = 0f), edge(firstVisibleItemIndex = 0, scrollOffset = 24, fieldTop = 200))
        assertEquals(PinnedSearchTopEdge(fade = 1f, fadeBelowInset = 1f, cover = 0f), edge(firstVisibleItemIndex = 0, scrollOffset = 96, fieldTop = 100))
    }

    @Test
    fun `controls still in the room stay faded as the field arrives under them`() {
        assertEquals(PinnedSearchTopEdge(fade = 1f, fadeBelowInset = 0.75f, cover = 0.25f), edge(firstVisibleItemIndex = 0, scrollOffset = 200, fieldTop = 18))
        assertEquals(PinnedSearchTopEdge(fade = 1f, fadeBelowInset = 0f, cover = 1f), edge(firstVisibleItemIndex = 0, scrollOffset = 200, fieldTop = 0))
    }

    @Test
    fun `a pinned field has the room filled in and no fade over it`() =
        assertEquals(PinnedSearchTopEdge(fade = 1f, fadeBelowInset = 0f, cover = 1f), edge(firstVisibleItemIndex = 5, scrollOffset = 10, fieldTop = -48))

    private fun edge(firstVisibleItemIndex: Int, scrollOffset: Int, fieldTop: Int?) = pinnedSearchTopEdge(
        firstVisibleItemIndex = firstVisibleItemIndex,
        scrollOffset = scrollOffset,
        fieldTop = fieldTop,
        fadeHeight = 48f,
        arrivalHeight = 24f,
    )
}
