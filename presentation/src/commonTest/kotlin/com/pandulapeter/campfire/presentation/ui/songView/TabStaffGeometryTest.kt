/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import com.pandulapeter.campfire.chordpro.model.TabRowLine
import com.pandulapeter.campfire.presentation.ui.songView.TabStaffGeometry.TabBar
import com.pandulapeter.campfire.presentation.ui.songView.TabStaffGeometry.TabString
import kotlin.test.Test
import kotlin.test.assertEquals

class TabStaffGeometryTest {

    @Test
    fun `a string runs from bar to bar, interrupted by every fret on it`() {
        val geometry = tabStaffGeometryOf(listOf(TabRowLine("e|--3--10-|", isStaffLine = true)))

        assertEquals(
            listOf(TabString(line = 0, from = 1.5f, to = 4f), TabString(line = 0, from = 5f, to = 7f), TabString(line = 0, from = 9f, to = 10.5f)),
            geometry.strings,
        )
    }

    @Test
    fun `a bar line crosses every string of the staff that has a bar in its column`() {
        val geometry = tabStaffGeometryOf(
            listOf(
                TabRowLine("  Am", isStaffLine = false),
                TabRowLine("e|--0--|", isStaffLine = true),
                TabRowLine("B|--1--|", isStaffLine = true),
                TabRowLine("G|--2--|", isStaffLine = true),
            ),
        )

        assertEquals(listOf(TabBar(column = 1, fromLine = 1, toLine = 3), TabBar(column = 7, fromLine = 1, toLine = 3)), geometry.bars)
    }

    @Test
    fun `a bar on one string alone is a bar of its own`() {
        val geometry = tabStaffGeometryOf(listOf(TabRowLine("e|--0--|", isStaffLine = true), TabRowLine("B|--1---", isStaffLine = true)))

        assertEquals(listOf(TabBar(column = 1, fromLine = 0, toLine = 1), TabBar(column = 7, fromLine = 0, toLine = 0)), geometry.bars)
    }

    @Test
    fun `the dashes and bars of a line that is not a staff are text`() {
        val line = TabRowLine("Intro -- | x2", isStaffLine = false)

        assertEquals(TabStaffGeometry(strings = emptyList(), bars = emptyList()), tabStaffGeometryOf(listOf(line)))
        assertEquals("Intro -- | x2", line.drawnText)
    }

    @Test
    fun `a staff line is drawn as text with its strokes blanked out, every character in its column`() {
        assertEquals("e   3h5    x3", TabRowLine("e|--3h5--| x3", isStaffLine = true).drawnText)
    }
}
