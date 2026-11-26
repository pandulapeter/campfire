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
import com.pandulapeter.campfire.presentation.ui.songView.SongPiece
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.SectionPart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class ChordsOnScreenTest {

    private val names = setOf("Am", "C", "D", "F", "E")
    private val sections = listOf(
        verse("Am", "C", "D"),
        RenderSection.Comment(text = "Instrumental", style = CommentStyle.PLAIN),
        verse("F", "Am"),
        verse("E"),
    )

    @Test
    fun `a page starting with a section is headed by the first chord the section plays`() {
        assertEquals("Am", firstChordFrom(sections, 0, 0, names))
        assertEquals("F", firstChordFrom(sections, 2, 0, names))
        assertEquals("E", firstChordFrom(sections, 3, 0, names))
    }

    @Test
    fun `the chords on the screen are every chord its lines play`() =
        assertEquals(setOf("Am", "C", "D", "F"), chordsOnScreen(sections, listOf(piece(0), piece(1), piece(2)), names))

    @Test
    fun `a section split over two pages marks on each only the chords of its lines there`() {
        val verse = verse("Am", "C", "D").copy(
            parts = listOf(
                SectionPart.Lines(lines = listOf(lyrics("Am"), lyrics("C"), lyrics("D"))),
            ),
        )
        assertEquals(setOf("Am", "C"), chordsOnScreen(listOf(verse), listOf(piece(0, 0..0), piece(0, 1..1)), names))
        assertEquals(setOf("D"), chordsOnScreen(listOf(verse), listOf(piece(0, 2..2)), names))
    }

    @Test
    fun `a page of no chords in the middle of a section is headed by the next chord its section plays`() {
        val verse = RenderSection.Lines(
            header = "Verse",
            foldKey = "verse",
            parts = listOf(SectionPart.Lines(lines = listOf(lyrics("Am"), ChordProLine.Lyrics(text = "words", chords = emptyList()), lyrics("E")))),
            isOnCard = false,
        )
        assertEquals(setOf("E"), chordsOnScreen(listOf(verse), listOf(piece(0, 1..1)), names))
    }

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
        assertEquals(setOf("E", "C", "D"), chordsOnScreen(listOf(grid), listOf(piece(0)), names))
    }

    @Test
    fun `a tab's line of chord names is chords on the screen without a second shape's star but its staff is not`() {
        val tab = RenderSection.Lines(
            header = "Picking pattern",
            foldKey = "tab",
            parts = listOf(
                SectionPart.Lines(
                    lines = listOf(
                        ChordProLine.Tab(text = "  Am            C*            E"),
                        ChordProLine.Tab(text = "e|-------------|-------------|-------------|", continuesEnvironment = true),
                    ),
                ),
            ),
            isOnCard = false,
        )
        assertEquals(setOf("Am", "C", "E"), chordsOnScreen(listOf(tab), listOf(piece(0, 0..1)), names))
    }

    @Test
    fun `a screen of no chords marks the chord it is headed by`() = assertEquals(setOf("F"), chordsOnScreen(sections, listOf(piece(1)), names))

    @Test
    fun `a section of no chords is headed by the next chord played`() = assertEquals("F", firstChordFrom(sections, 1, 0, names))

    @Test
    fun `past the last chord the page is headed by the last one played`() = assertEquals("E", firstChordFrom(sections, 4, 0, names))

    @Test
    fun `annotations and chords the strip does not draw are passed over`() {
        val annotated = listOf(verse("N.C.", "D"))
        assertEquals("D", firstChordFrom(annotated, 0, 0, names))
    }

    @Test
    fun `a song playing none of the strip's chords heads no page`() = assertNull(firstChordFrom(listOf(verse("G")), 0, 0, names))

    private fun piece(section: Int, items: IntRange = 0..0) = SongPiece(start = 0, end = 1, section = section, items = items)

    private fun lyrics(chord: String) = ChordProLine.Lyrics(
        text = "words",
        chords = listOf(ChordProLine.Lyrics.Chord(position = 0, name = chord, isAnnotation = false)),
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
