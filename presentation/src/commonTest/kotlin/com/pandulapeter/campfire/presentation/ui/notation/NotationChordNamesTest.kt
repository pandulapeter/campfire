/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NotationChordNamesTest {

    @Test
    fun `chord symbols are read from the music in order, without annotations, fields, or comments`() {
        val tune = """
            X:1
            T:"Not a chord"
            K:G
            "G"G2 "^fine"A "D7/F#"B|"Em"c % "C" in a comment
            w:"words"
            %%text "Am"
        """.trimIndent()
        assertEquals(listOf("G", "D7/F#", "Em"), abcChordSymbols(tune))
    }

    @Test
    fun `the key a numbering counts from is the tune's first`() {
        assertEquals("G", abcKeyOf("X:1\nK:G\nK:D"))
        assertEquals("Em", abcKeyOf("K:Em"))
        assertEquals("Bbm", abcKeyOf("K:Bb minor"))
        assertEquals("Am", abcKeyOf("K:A dorian"))
        assertEquals("D", abcKeyOf("K:Dmix"))
        assertEquals("F#", abcKeyOf("K:F#maj % sharp"))
        assertEquals(null, abcKeyOf("K:none"))
    }

    @Test
    fun `symbols are named as the lyrics' chords are`() {
        val tune = "K:G\n\"G\"G \"D7\"A \"Em\"B"
        val symbols = abcChordSymbols(tune)
        assertEquals(listOf("A", "E7", "F♯m"), abcChordNames(tune, symbols, NotationChordSpelling(semitones = 2, preferFlats = null, notation = ChordNotation.STANDARD)))
        assertEquals(listOf("A♭", "E♭7", "Fm"), abcChordNames(tune, symbols, NotationChordSpelling(semitones = 1, preferFlats = true, notation = ChordNotation.STANDARD)))
        val wider = "K:G\n\"G\"G \"D7\"A \"Em\"B \"Bb\"c \"B\"d"
        fun namesIn(notation: ChordNotation) = abcChordNames(wider, abcChordSymbols(wider), NotationChordSpelling(semitones = 0, preferFlats = null, notation = notation))
        assertEquals(listOf("G", "D7", "Em", "B", "H"), namesIn(ChordNotation.GERMAN))
        assertEquals(listOf("Sol", "Re7", "Mim", "Si♭", "Si"), namesIn(ChordNotation.LATIN))
        assertEquals(listOf("1", "5(7)", "6-", "♭3", "3"), namesIn(ChordNotation.NASHVILLE))
        assertEquals(listOf("I", "V7", "vi", "♭III", "III"), namesIn(ChordNotation.ROMAN))
    }

    @Test
    fun `a transposed chord is the written one with its root moved and the rest kept`() {
        assertTrue(isTransposedChord("D7/F#", "E7/G♯", 2))
        assertTrue(isTransposedChord("Bbm", "Cm", 2))
        assertTrue(isTransposedChord("N.C.", "N.C.", 0))
        assertFalse(isTransposedChord("D7", "E", 2))
        assertFalse(isTransposedChord("D7", "F7", 2))
        assertFalse(isTransposedChord("G", "A/C♯", 2))
    }

    @Test
    fun `chord names replace the renderer's only where every chord drawn is the symbol at its place`() {
        val drawing = drawingWithChords("E", "B7")
        val renamed = drawing.withChordNames(symbols = listOf("D", "A7"), names = listOf("Re", "La7"), semitones = 2)
        assertEquals(listOf("Re", "La7"), renamed.chordTexts())
        assertSame(drawing, drawing.withChordNames(symbols = listOf("A7", "D"), names = listOf("La7", "Re"), semitones = 2))
        assertSame(drawing, drawing.withChordNames(symbols = listOf("D"), names = listOf("Re"), semitones = 2))
    }

    private fun drawingWithChords(vararg names: String) = NotationDrawing(
        listOf(
            NotationSystem(
                width = 100f,
                height = 50f,
                shapes = emptyList(),
                glyphs = emptyList(),
                texts = names.map { name ->
                    NotationText(x = 0f, y = 0f, anchor = NotationAnchor.START, runs = listOf(NotationTextRun.Words(name, FONT, NotationTextRole.CHORD, dy = 0f)))
                } + NotationText(x = 0f, y = 0f, anchor = NotationAnchor.START, runs = listOf(NotationTextRun.Words("la", FONT, NotationTextRole.LYRIC, dy = 0f))),
            ),
        ),
    )

    private fun NotationDrawing.chordTexts() = systems.flatMap { system ->
        system.texts.flatMap { it.runs }.filterIsInstance<NotationTextRun.Words>().filter { it.role == NotationTextRole.CHORD }.map { it.text }
    }

    private companion object {
        val FONT = NotationFont(size = 12f, isBold = false, isItalic = false, isMusic = false)
    }
}
