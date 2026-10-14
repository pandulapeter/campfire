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
import com.pandulapeter.campfire.chordpro.model.TabNotice
import com.pandulapeter.campfire.data.model.domain.UserPreferences.Instrument
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TabPlayersTest {

    @Test
    fun `a staff that names nothing is in the standard tuning of the player's instrument where that has as many strings`() {
        val ukulele = tabPlayerOf(Instrument.UKULELE, InstrumentTuning.UKULELE_LOW_G, ChordNotation.STANDARD, isMoved = true)

        assertEquals(InstrumentTuning.UKULELE.strings, ukulele.assumedTuning(unnamed(4), null)?.strings)
        assertEquals("ukulele", ukulele.assumedTuning(unnamed(4), null)?.instrument)
    }

    @Test
    fun `a staff that names nothing is a bass's of four or five strings and a guitar's of six to eight elsewhere`() {
        val ukulele = tabPlayerOf(Instrument.UKULELE, InstrumentTuning.UKULELE, ChordNotation.STANDARD, isMoved = true)

        assertEquals(listOf("bass_five_string", "guitar", "guitar_seven_string", "guitar_eight_string"), (5..8).map { ukulele.assumedTuning(unnamed(it), null)?.instrument })
        assertEquals("bass", tabPlayerOf(Instrument.GUITAR, InstrumentTuning.GUITAR, ChordNotation.STANDARD, isMoved = true).assumedTuning(unnamed(4), null)?.instrument)
        assertNull(ukulele.assumedTuning(unnamed(3), null))
    }

    @Test
    fun `a tab's selector names the instrument it is in the standard tuning of`() {
        val guitar = tabPlayerOf(Instrument.GUITAR, InstrumentTuning.GUITAR, ChordNotation.STANDARD, isMoved = true)

        assertEquals(InstrumentTuning.MANDOLIN.strings, guitar.assumedTuning(unnamed(4), "mandolin")?.strings)
        assertEquals("bass", guitar.assumedTuning(unnamed(4), "ukulele_baritone")?.instrument)
    }

    @Test
    fun `a staff that names its strings is of the instrument whose open strings sound them`() {
        val guitar = tabPlayerOf(Instrument.GUITAR, InstrumentTuning.GUITAR, ChordNotation.STANDARD, isMoved = true)
        val bass = tabPlayerOf(Instrument.BASS, InstrumentTuning.BASS, ChordNotation.STANDARD, isMoved = true)

        assertEquals("ukulele", guitar.assumedTuning(pitchClasses(InstrumentTuning.UKULELE), null)?.instrument)
        assertEquals("mandolin", guitar.assumedTuning(pitchClasses(InstrumentTuning.MANDOLIN), null)?.instrument)
        assertEquals("bass", bass.assumedTuning(pitchClasses(InstrumentTuning.BASS), null)?.instrument)
        assertEquals("bass", guitar.assumedTuning(pitchClasses(InstrumentTuning.MANDOLIN), "bass")?.instrument)
        assertEquals("guitar", guitar.assumedTuning(pitchClasses(InstrumentTuning.GUITAR_HALF_STEP_DOWN), null)?.instrument)
        assertEquals("ukulele", guitar.assumedTuning(listOf(7, null, null, 9), null)?.instrument)
    }

    @Test
    fun `a twelve-string guitar is a guitar to the tabs`() {
        val twelveString = tabPlayerOf(Instrument.GUITAR_TWELVE_STRING, InstrumentTuning.GUITAR_DROP_D, ChordNotation.STANDARD, isMoved = true)

        assertEquals(listOf("guitar_twelve_string", "guitar"), twelveString.selectors)
        assertEquals("guitar", twelveString.tuning?.instrument)
        assertEquals("guitar", twelveString.assumedTuning(unnamed(6), null)?.instrument)
    }

    @Test
    fun `only an instrument a tab cannot be played on hides the tabs`() {
        assertTrue(tabPlayerOf(Instrument.KEYBOARD, null, ChordNotation.STANDARD, isMoved = true).hidesTabs)
        assertTrue(tabPlayerOf(Instrument.VIOLIN, InstrumentTuning.VIOLIN, ChordNotation.STANDARD, isMoved = true).hidesTabs)
        assertFalse(tabPlayerOf(Instrument.OTHER, null, ChordNotation.STANDARD, isMoved = true).hidesTabs)
        assertFalse(tabPlayerOf(Instrument.KEYBOARD, null, ChordNotation.STANDARD, isMoved = false).hidesTabs)
    }

    @Test
    fun `a tab is moved for nobody where it is only shown as written`() {
        assertNull(tabPlayerOf(Instrument.GUITAR, InstrumentTuning.GUITAR_DROP_D, ChordNotation.STANDARD, isMoved = false).tuning)
        assertNull(tabPlayerOf(Instrument.OTHER, null, ChordNotation.STANDARD, isMoved = true).tuning)
    }

    @Test
    fun `strings are named in the reader's notation`() {
        assertEquals("H", tabPlayerOf(Instrument.GUITAR, InstrumentTuning.GUITAR, ChordNotation.GERMAN, isMoved = true).stringName(59))
    }

    @Test
    fun `the editor's empty staff is the player's strings, or a guitar's where they play none`() {
        assertEquals(listOf("d", "a", "G", "D", "A", "D"), emptyTabStaffOf(Instrument.GUITAR, InstrumentTuning.GUITAR_DADGAD, ChordNotation.STANDARD).map { it.substringBefore('|') })
        assertEquals("E|----------------|", emptyTabStaffOf(Instrument.GUITAR, InstrumentTuning.GUITAR, ChordNotation.STANDARD).last())
        assertEquals(listOf("A", "E", "C", "G"), emptyTabStaffOf(Instrument.UKULELE, InstrumentTuning.UKULELE, ChordNotation.STANDARD).map { it.substringBefore('|') })
        assertEquals(listOf("e", "B", "G", "D", "A", "E"), emptyTabStaffOf(Instrument.KEYBOARD, null, ChordNotation.STANDARD).map { it.substringBefore('|') })
    }

    @Test
    fun `the editor's empty staff names the strings in the notation the field is typed in`() {
        assertEquals(listOf(" mi", " Si", "Sol", " Re", " La", " Mi"), emptyTabStaffOf(Instrument.GUITAR, InstrumentTuning.GUITAR, ChordNotation.LATIN).map { it.substringBefore('|') })
        assertEquals(listOf("e", "H", "G", "D", "A", "E"), emptyTabStaffOf(Instrument.GUITAR, InstrumentTuning.GUITAR, ChordNotation.GERMAN).map { it.substringBefore('|') })
    }

    @Test
    fun `a tab for another instrument starts as that instrument's standard strings, and one for the player's as theirs`() {
        val ukulele = emptyTabStaffFor(Instrument.UKULELE, Instrument.GUITAR, InstrumentTuning.GUITAR_DADGAD, ChordNotation.STANDARD)
        val guitar = emptyTabStaffFor(Instrument.GUITAR, Instrument.GUITAR, InstrumentTuning.GUITAR_DADGAD, ChordNotation.STANDARD)
        val unnamed = emptyTabStaffFor(null, Instrument.GUITAR, InstrumentTuning.GUITAR_DADGAD, ChordNotation.STANDARD)

        assertEquals(listOf("A", "E", "C", "G"), ukulele.map { it.substringBefore('|') })
        assertEquals(listOf("d", "a", "G", "D", "A", "D"), guitar.map { it.substringBefore('|') })
        assertEquals(guitar, unnamed)
    }

    @Test
    fun `a tab is offered for every instrument with frets and for no other`() {
        assertTrue(Instrument.BANJO in tabInstruments)
        assertTrue(Instrument.GUITAR_TWELVE_STRING in tabInstruments)
        assertFalse(Instrument.KEYBOARD in tabInstruments)
        assertFalse(Instrument.VIOLIN in tabInstruments)
        assertFalse(Instrument.OTHER in tabInstruments)
    }

    private fun unnamed(strings: Int) = List<Int?>(strings) { null }

    private fun pitchClasses(tuning: InstrumentTuning) = tuning.strings.map { it.mod(12) }

    @Test
    fun `a notice names the instrument a tab is written for where it is not the reader's`() {
        val labels = TabNoticeLabels(
            octaveUp = "up",
            octaveDown = "down",
            otherInstrument = "$INSTRUMENT tab, moved",
            notMoved = "as written",
            notMovedInstrument = "$INSTRUMENT tab, as written",
            hidden = "hidden",
            hiddenInstrument = "$INSTRUMENT tab hidden",
            instruments = mapOf("guitar" to "Guitar"),
        )

        assertEquals("Guitar tab, moved", labels.describe(TabNotice(TabNotice.Kind.MOVED, instrument = "guitar")))
        assertEquals("down", labels.describe(TabNotice(TabNotice.Kind.MOVED, octaves = -1)))
        assertEquals("as written", labels.describe(TabNotice(TabNotice.Kind.NOT_MOVED)))
        assertEquals("Guitar tab hidden", labels.describe(TabNotice(TabNotice.Kind.HIDDEN, instrument = "guitar")))
        assertEquals("hidden", labels.describe(TabNotice(TabNotice.Kind.HIDDEN, instrument = "kazoo")))
    }
}
