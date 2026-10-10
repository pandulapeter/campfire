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

import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.presentation.ui.chords.ChordCell
import com.pandulapeter.campfire.presentation.ui.chords.SelectedShape
import kotlin.test.Test
import kotlin.test.assertContentEquals

class SongUnitsTest {

    @Test
    fun `every line of lyrics is a line and an empty line or a comment is none`() {
        val units = SongUnits.of(listOf(verseWithStanzas())) { true }
        assertContentEquals(intArrayOf(1, 0, 1, 1, 0, 1, 1), units.lineCount)
    }

    @Test
    fun `a cut after an empty line or in front of a comment falls between stanzas`() {
        val units = SongUnits.of(listOf(verseWithStanzas())) { true }
        assertContentEquals(booleanArrayOf(false, false, true, false, true, false, false), units.isStanzaBreakBefore)
    }

    @Test
    fun `a section that is not cut is one chunk of all of its lines`() {
        val units = SongUnits.of(listOf(verseWithStanzas())) { false }
        assertContentEquals(intArrayOf(5), units.lineCount)
        assertContentEquals(booleanArrayOf(false), units.isStanzaBreakBefore)
    }

    @Test
    fun `the metadata and the rows of chord diagrams are no lines`() {
        val chords = RenderSection.Chords(cells = List(3) { cell }, isFolded = false)
        val units = SongUnits.of(listOf(RenderSection.Metadata(ChordProMetadata()), chords)) { true }
        assertContentEquals(intArrayOf(0, 1, 1, 1), units.unitSections)
        assertContentEquals(intArrayOf(0, 0, 0, 0), units.lineCount)
        assertContentEquals(booleanArrayOf(false, false, false, false), units.isStanzaBreakBefore)
    }

    @Test
    fun `the lines of a grid are no lines of lyrics but may still be cut between`() {
        val section = lines(
            SectionPart.Lines(listOf(lyrics("Before"), grid(), grid(), lyrics("After"))),
        )
        val units = SongUnits.of(listOf(section)) { true }
        assertContentEquals(intArrayOf(1, 0, 0, 1), units.lineCount)
        assertContentEquals(booleanArrayOf(false, true, true, true), units.isCuttableBefore)
        assertContentEquals(booleanArrayOf(false, false, false, false), units.isStanzaBreakBefore)
    }

    @Test
    fun `a slice keeps the lines and the stanza breaks of its units`() {
        val units = SongUnits.of(listOf(lines(SectionPart.Lines(listOf(lyrics("Intro")))), verseWithStanzas())) { true }.slice(1, 2)
        assertContentEquals(intArrayOf(1, 0, 1, 1, 0, 1, 1), units.lineCount)
        assertContentEquals(booleanArrayOf(false, false, true, false, true, false, false), units.isStanzaBreakBefore)
    }

    private val cell = ChordCell(
        name = "G",
        soundingName = null,
        instrument = ChordInstrument.GUITAR,
        root = 7,
        selection = SelectedShape(null, SelectedShape.Source.DEFAULT),
    )

    private fun verseWithStanzas() = lines(
        SectionPart.Lines(listOf(lyrics("One"), ChordProLine.Blank, lyrics("Two"), lyrics("Three"))),
        RenderSection.Comment(text = "Slower", style = CommentStyle.ITALIC),
        SectionPart.Lines(listOf(lyrics("Four"), lyrics("Five"))),
    )

    private fun lines(vararg parts: SectionPart) = RenderSection.Lines(
        header = "Verse",
        foldKey = "verse#1",
        parts = parts.toList(),
        isOnCard = false,
    )

    private fun lyrics(text: String) = ChordProLine.Lyrics(text = text, chords = listOf(ChordProLine.Lyrics.Chord(position = 0, name = "G", isAnnotation = false)))

    private fun grid() = ChordProLine.Grid(tokens = listOf(GridToken.Bar("|"), GridToken.Chord("G"), GridToken.Bar("|")))
}
