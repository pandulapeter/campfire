/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tabs

import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.model.domain.UserPreferences.Instrument
import com.pandulapeter.campfire.presentation.ui.instrument.tunings
import com.pandulapeter.campfire.presentation.ui.rendering.testSongRenderer
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The demo song's picking pattern, which is written for every instrument a song is likely to be played on, as each of them reads it. */
class DemoSongTabsTest {

    @Test
    fun `every instrument the demo song has a tab for is shown its own, untouched and with nothing said about it`() {
        mapOf(
            Instrument.GUITAR to "e",
            Instrument.UKULELE to "A",
            Instrument.MANDOLIN to "E",
            Instrument.BANJO to "d",
            Instrument.BASS to "G",
        ).forEach { (instrument, topString) ->
            val song = rendered(instrument)

            assertEquals(topString, song.staffNames().first(), instrument.name)
            assertEquals(1, song.staffNames().count { it == topString }, instrument.name)
            assertEquals(emptyList(), song.notices(), instrument.name)
        }
    }

    @Test
    fun `the banjo's short string keeps its lowercase name`() {
        assertEquals(listOf("d", "B", "G", "D", "g"), rendered(Instrument.BANJO).staffNames())
    }

    @Test
    fun `a guitar tuned down is shown the guitar's tab a fret up`() {
        val song = rendered(Instrument.GUITAR, InstrumentTuning.GUITAR_HALF_STEP_DOWN)

        assertEquals(listOf("d#", "A#", "F#", "C#", "G#", "D#"), song.staffNames())
        assertTrue(song.tabLines().any { it.startsWith("G#|-1---") })
        assertEquals(emptyList(), song.notices())
    }

    @Test
    fun `a seven-string guitar is shown the guitar's tab with nothing said about it`() {
        val song = rendered(Instrument.GUITAR_SEVEN_STRING)

        assertEquals(listOf("e", "B", "G", "D", "A", "E"), song.staffNames())
        assertEquals(emptyList(), song.notices())
    }

    @Test
    fun `a keyboard is told the guitar's tab is hidden, and nothing about picking strings`() {
        val song = rendered(Instrument.KEYBOARD)

        assertEquals(emptyList(), song.tabLines())
        assertEquals(listOf("guitar hidden"), song.notices())
    }

    private fun rendered(instrument: Instrument, tuning: InstrumentTuning? = instrument.tunings.firstOrNull()): ChordProSong =
        testSongRenderer().transposedSong(
            text = DEMO_SONG.readText(),
            transposition = 0,
            spelling = UserPreferences.ChordSpelling.Default,
            tabs = TabReading(player = tabPlayerOf(instrument, tuning, ChordNotation.STANDARD, isMoved = true), labels = LABELS, selects = true),
        )

    private fun ChordProSong.tabLines() = blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }.filterIsInstance<ChordProLine.Tab>().map { it.text }

    private fun ChordProSong.staffNames() = tabLines().filter { '|' in it && !it.startsWith(' ') }.map { it.substringBefore('|') }

    /** The comments the tabs left, which are the notices and the comments written inside a tab. */
    private fun ChordProSong.notices() = blocks.filterIsInstance<ChordProBlock.Comment>().filter { it.isInTabOrGrid }.map { it.text }.filter { it in NOTICES || it.endsWith("hidden") }

    private companion object {
        val DEMO_SONG = File("src/commonMain/composeResources/files/demo/traditional_american-house_of_the_rising_sun.cho")
        val LABELS = TabNoticeLabels(
            octaveUp = "up",
            octaveDown = "down",
            otherInstrument = "$INSTRUMENT moved",
            notMoved = "as written",
            notMovedInstrument = "$INSTRUMENT as written",
            hidden = "hidden",
            hiddenInstrument = "$INSTRUMENT hidden",
            instruments = Instrument.entries.associate { it.id to it.id },
        )
        val NOTICES = setOf("up", "down", "as written") + Instrument.entries.flatMap { listOf("${it.id} moved", "${it.id} as written") }
    }
}
