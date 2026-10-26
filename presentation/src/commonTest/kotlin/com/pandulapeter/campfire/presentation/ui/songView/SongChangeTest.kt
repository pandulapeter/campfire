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

import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.SectionPart
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongChangeTest {

    private val chordsOff = LeavingParts(chords = true, chordsSection = true, scalesSection = true, instrumentPill = true, timing = false)
    private val nothing = LeavingParts(chords = false, chordsSection = false, scalesSection = false, instrumentPill = false, timing = false)

    @Test
    fun `the chords switched off take the Chords and Scales sections and the key changes`() {
        assertTrue(RenderSection.Chords(cells = emptyList(), isFolded = false).leavesWith(chordsOff, 0..0))
        assertTrue(RenderSection.Scales(groups = emptyList(), isFolded = false).leavesWith(chordsOff, 0..0))
        assertTrue(RenderSection.KeyChange(key = "A").leavesWith(chordsOff, 0..0))
    }

    @Test
    fun `the song's first section and its comments stay`() {
        assertFalse(RenderSection.Metadata(ChordProMetadata(title = "A")).leavesWith(chordsOff, 0..0))
    }

    @Test
    fun `a line of lyrics stays while its chord row leaves`() {
        val section = lines(lyrics("Words"), chordRow())
        assertFalse(section.leavesWith(chordsOff, 0..0))
        assertTrue(section.leavesWith(chordsOff, 1..1))
    }

    @Test
    fun `a section of nothing but tablature leaves with its header`() {
        val section = lines(tab(), tab())
        assertTrue(section.leavesWith(chordsOff, 0..1))
    }

    @Test
    fun `a section keeps its header where any of its lines stays`() {
        val section = lines(tab(), lyrics("Words"))
        assertFalse(section.leavesWith(chordsOff, 0..0))
    }

    @Test
    fun `nothing leaves with a change that takes nothing`() {
        assertFalse(RenderSection.Chords(cells = emptyList(), isFolded = false).leavesWith(nothing, 0..0))
        assertFalse(lines(chordRow()).leavesWith(nothing, 0..0))
    }

    @Test
    fun `tempo changes leave with the metronome`() {
        assertTrue(RenderSection.Timing(tempo = "90", time = "3/4").leavesWith(nothing.copy(timing = true), 0..0))
    }

    private fun lines(vararg lines: ChordProLine) = RenderSection.Lines(
        header = "Verse",
        foldKey = "verse#1",
        parts = listOf(SectionPart.Lines(lines.toList())),
        isOnCard = false,
    )

    private fun lyrics(text: String) = ChordProLine.Lyrics(text = text, chords = listOf(ChordProLine.Lyrics.Chord(position = 0, name = "G", isAnnotation = false)))

    private fun chordRow() = ChordProLine.Lyrics(text = " ", chords = listOf(ChordProLine.Lyrics.Chord(position = 0, name = "G", isAnnotation = false)))

    private fun tab() = ChordProLine.Tab(text = "e|---0---|")
}
