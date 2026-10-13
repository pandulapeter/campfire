/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import kotlin.test.Test
import kotlin.test.assertEquals

/** The editor's Find finds every occurrence once, and picks up from where the caret is. */
internal class EditorSearchMatchesTest {

    @Test
    fun `every occurrence is found`() =
        assertEquals(listOf(1, 6), searchMatchStarts(text = "[Am] [Am]", query = "Am", isMatchCase = true))

    @Test
    fun `the case is ignored unless it has to match`() {
        assertEquals(listOf(1, 6), searchMatchStarts(text = "[Am] [am]", query = "AM", isMatchCase = false))
        assertEquals(emptyList(), searchMatchStarts(text = "[Am] [am]", query = "AM", isMatchCase = true))
    }

    @Test
    fun `occurrences do not overlap`() =
        assertEquals(listOf(0, 2), searchMatchStarts(text = "aaaaa", query = "aa", isMatchCase = true))

    @Test
    fun `an empty query matches nothing`() =
        assertEquals(emptyList(), searchMatchStarts(text = "verse", query = "", isMatchCase = false))

    @Test
    fun `the current match is the first one at or after the selection`() {
        assertEquals(1, currentMatchIndex(matchStarts = listOf(2, 8, 14), selectionStart = 8))
        assertEquals(2, currentMatchIndex(matchStarts = listOf(2, 8, 14), selectionStart = 9))
    }

    @Test
    fun `a selection past the last match wraps around to the first`() =
        assertEquals(0, currentMatchIndex(matchStarts = listOf(2, 8), selectionStart = 9))

    @Test
    fun `there is no current match without matches`() =
        assertEquals(-1, currentMatchIndex(matchStarts = emptyList(), selectionStart = 0))
}
