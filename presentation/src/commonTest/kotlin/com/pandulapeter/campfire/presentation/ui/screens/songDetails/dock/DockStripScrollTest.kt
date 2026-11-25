/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.SectionPart
import com.pandulapeter.campfire.presentation.ui.songView.model.SongScaleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class DockStripScrollTest {

    private val names = setOf("Am", "C", "D", "F", "E")
    private val sections = listOf(
        verse("Am", "C", "D"),
        RenderSection.Comment(text = "Instrumental", style = CommentStyle.PLAIN),
        verse("F", "Am"),
        verse("E"),
    )

    @Test
    fun `a page starting with a section is headed by the first chord the section plays`() {
        assertEquals("Am", firstChordFrom(sections, 0, names))
        assertEquals("F", firstChordFrom(sections, 2, names))
        assertEquals("E", firstChordFrom(sections, 3, names))
    }

    @Test
    fun `the chords on the screen are every chord its sections play`() =
        assertEquals(setOf("Am", "C", "D", "F"), chordsOnScreen(sections, 0..2, names))

    @Test
    fun `a grid's cells are chords on the screen too`() {
        val grid = RenderSection.Lines(
            header = "Intro",
            foldKey = "intro",
            parts = listOf(
                SectionPart.Lines(
                    lines = listOf(
                        ChordProLine.Grid(tokens = listOf(GridToken.Bar("|"), GridToken.Chord("E"), GridToken.Bar("|"), GridToken.Chord("C~D"), GridToken.Bar("|"))),
                    ),
                ),
            ),
            isOnCard = false,
        )
        assertEquals(setOf("E", "C", "D"), chordsOnScreen(listOf(grid), 0..0, names))
    }

    @Test
    fun `a tab's line of chord names is chords on the screen but its staff is not`() {
        val tab = RenderSection.Lines(
            header = "Picking pattern",
            foldKey = "tab",
            parts = listOf(
                SectionPart.Lines(
                    lines = listOf(
                        ChordProLine.Tab(text = "  Am            C             E"),
                        ChordProLine.Tab(text = "e|-------------|-------------|-------------|", continuesEnvironment = true),
                    ),
                ),
            ),
            isOnCard = false,
        )
        assertEquals(setOf("Am", "C", "E"), chordsOnScreen(listOf(tab), 0..0, names))
    }

    @Test
    fun `a screen of no chords marks the chord it is headed by`() = assertEquals(setOf("F"), chordsOnScreen(sections, 1..1, names))

    @Test
    fun `a section of no chords is headed by the next chord played`() = assertEquals("F", firstChordFrom(sections, 1, names))

    @Test
    fun `past the last chord the page is headed by the last one played`() = assertEquals("E", firstChordFrom(sections, 4, names))

    @Test
    fun `annotations and chords the strip does not draw are passed over`() {
        val annotated = listOf(verse("N.C.", "D"))
        assertEquals("D", firstChordFrom(annotated, 0, names))
    }

    @Test
    fun `a song playing none of the strip's chords heads no page`() = assertNull(firstChordFrom(listOf(verse("G")), 0, names))

    @Test
    fun `a page is in the group of the last section at or before its first`() {
        val groups = listOf(scaleGroup(0, 1, 4), scaleGroup(2, 3), scaleGroup(6))
        assertEquals(0, scaleGroupAt(groups, 0))
        assertEquals(1, scaleGroupAt(groups, 3))
        assertEquals(0, scaleGroupAt(groups, 5))
        assertEquals(2, scaleGroupAt(groups, 9))
    }

    @Test
    fun `a page before every group's sections is in the first group the song comes to`() =
        assertEquals(1, scaleGroupAt(listOf(scaleGroup(5), scaleGroup(3)), 1))

    @Test
    fun `groups naming no section put a page in none`() = assertNull(scaleGroupAt(listOf(scaleGroup()), 0))

    private fun scaleGroup(vararg sections: Int) = SongScaleGroup(
        label = null,
        rootName = "A",
        scales = emptyList(),
        exceptions = emptyList(),
        joinsSeveral = false,
        hasUnnamed = false,
        opening = null,
        isHome = false,
        sections = sections.toList(),
    )

    private fun verse(vararg chords: String) = RenderSection.Lines(
        header = "Verse",
        foldKey = "verse",
        parts = listOf(
            SectionPart.Lines(
                lines = listOf(
                    ChordProLine.Lyrics(
                        text = "words ".repeat(chords.size),
                        chords = chords.mapIndexed { index, name -> ChordProLine.Lyrics.Chord(position = index * 6, name = name, isAnnotation = false) },
                    ),
                ),
            ),
        ),
        isOnCard = false,
    )
}
