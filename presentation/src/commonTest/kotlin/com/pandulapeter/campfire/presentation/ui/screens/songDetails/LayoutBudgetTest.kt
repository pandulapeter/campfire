/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LayoutBudgetTest {

    @Test
    fun `a song within the budget is left alone`() {
        val sections = List(20) { section(lines = 40) }

        val (fitting, isCut) = LayoutBudget.fit(sections)

        assertSame(sections, fitting)
        assertFalse(isCut)
    }

    @Test
    fun `a songbook stops at the end of the last section that fits`() {
        val sections = List(100) { section(lines = 40) }

        val (fitting, isCut) = LayoutBudget.fit(sections)

        assertEquals(LayoutBudget.MAX_LINES / 40, fitting.size)
        assertEquals(sections.take(fitting.size), fitting)
        assertTrue(isCut)
    }

    @Test
    fun `a file with no blank line is cut inside its one section`() {
        val (fitting, isCut) = LayoutBudget.fit(listOf(section(lines = 60_000)))

        assertEquals(LayoutBudget.MAX_LINES, (fitting.single() as RenderSection.Lines).lines.size)
        assertTrue(isCut)
    }

    @Test
    fun `long lines are counted by their characters`() {
        val (fitting, isCut) = LayoutBudget.fit(List(10) { section(lines = 10, lineLength = 5_000) })

        assertEquals(LayoutBudget.MAX_CHARACTERS / 50_000, fitting.size)
        assertTrue(isCut)
    }

    @Test
    fun `comments count too`() {
        val comment = RenderSection.Comment(text = "x".repeat(LayoutBudget.MAX_CHARACTERS), style = CommentStyle.PLAIN)

        val (fitting, isCut) = LayoutBudget.fit(listOf(section(lines = 1), comment))

        assertEquals(1, fitting.size)
        assertTrue(isCut)
    }

    private fun section(lines: Int, lineLength: Int = 10) = RenderSection.Lines(
        header = null,
        foldKey = "verse#1",
        parts = listOf(SectionPart.Lines(List(lines) { ChordProLine.Lyrics(text = "x".repeat(lineLength), chords = emptyList()) })),
        isOnCard = false,
    )
}
