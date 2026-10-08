/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chords

import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import kotlin.test.Test
import kotlin.test.assertEquals

class ChordShapeInsertionTest {

    private fun apply(
        text: String,
        caret: Int,
        instrument: ChordInstrument = ChordInstrument.GUITAR,
        notation: ChordNotation = ChordNotation.STANDARD,
        storedShapes: Map<String, String> = emptyMap(),
    ): Pair<String, String> {
        val insertion = chordShapeInsertion(text, caret, notation, instrument, storedShapes)
        val result = text.substring(0, insertion.offset) + insertion.text + text.substring(insertion.offset)
        return result to result.substring(insertion.selectionStart, insertion.selectionEnd)
    }

    private val song = "{title: X}\n{key: C}\n\n[C]Hello [Am]there"

    @Test
    fun `a chord at the caret is defined with the shape the player would see, its frets selected`() {
        assertEquals(
            "{title: X}\n{key: C}\n{define: Am base-fret 1 frets x 0 2 2 1 0 fingers 0 0 2 3 1 0}\n\n[C]Hello [Am]there" to "x 0 2 2 1 0",
            apply(song, song.indexOf("Am")),
        )
        val stored = mapOf(ChordProChords.parse("C")!!.id to "x 3 5 5 5 3")
        assertEquals("x 1 3 3 3 1", apply(song, song.indexOf("[C]"), storedShapes = stored).second)
        assertEquals("0 0 0 3", apply(song, song.indexOf("[C]") + 3, instrument = ChordInstrument.UKULELE).second)
    }

    @Test
    fun `the keyboard writes keys`() {
        val (text, selected) = apply(song, song.indexOf("[C]") + 1, instrument = ChordInstrument.KEYBOARD)
        assertEquals("0 4 7", selected)
        assertEquals(true, "{define: C keys 0 4 7}" in text)
    }

    @Test
    fun `with the caret in no chord the name is left to be typed`() {
        val insertion = chordShapeInsertion(song, song.indexOf("Hello") + 2, ChordNotation.STANDARD, ChordInstrument.GUITAR, emptyMap())
        assertEquals("{define: }\n", insertion.text)
        assertEquals(insertion.offset + "{define: ".length, insertion.selectionStart)
        assertEquals(insertion.selectionStart, insertion.selectionEnd)
    }

    @Test
    fun `a chord already defined gets no second line, the caret goes to its frets`() {
        val defined = "{title: X}\n{define: Am frets x 0 2 2 1 0}\n\n[Am]there"
        val insertion = chordShapeInsertion(defined, defined.lastIndexOf("Am"), ChordNotation.STANDARD, ChordInstrument.GUITAR, emptyMap())
        assertEquals("", insertion.text)
        assertEquals("x 0 2 2 1 0", defined.substring(insertion.selectionStart, insertion.selectionEnd))
        // Defined for another instrument only, it gets a line of its own.
        assertEquals("2 0 0 0", apply(defined, defined.lastIndexOf("Am"), instrument = ChordInstrument.UKULELE).second)
    }

    @Test
    fun `a German chord is read and written in German`() {
        val german = "{title: X}\n\n[H7]la [B]lo"
        val (text, selected) = apply(german, german.indexOf("H7"), notation = ChordNotation.GERMAN)
        assertEquals("x 2 1 2 0 2", selected)
        assertEquals(true, "{define: H7 base-fret 1 frets x 2 1 2 0 2" in text)
        assertEquals("10 14 17".split(" ").size, apply(german, german.indexOf("[B]") + 1, instrument = ChordInstrument.KEYBOARD, notation = ChordNotation.GERMAN).second.split(" ").size)
        assertEquals(true, "{define: B keys 0 4 7}" in apply(german, german.indexOf("[B]") + 1, instrument = ChordInstrument.KEYBOARD, notation = ChordNotation.GERMAN).first)
    }
}
