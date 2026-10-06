/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

import com.pandulapeter.campfire.chordpro.model.Chord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChordProChordsTest {

    @Test
    fun `every usual name is read as the notes chord charts agree on`() {
        mapOf(
            "C" to "C E G",
            "Cm" to "C D# G",
            "Cmin" to "C D# G",
            "Cmi" to "C D# G",
            "C-" to "C D# G",
            "C5" to "C G",
            "C2" to "C D E G",
            "C4" to "C F G",
            "Csus" to "C F G",
            "Csus4" to "C F G",
            "Csus2" to "C D G",
            "C7sus4" to "C F G A#",
            "C7sus2" to "C D G A#",
            "C9sus4" to "C D F G A#",
            "Cadd9" to "C D E G",
            "Cadd2" to "C D E G",
            "Cadd4" to "C E F G",
            "Cadd11" to "C E F G",
            "C(add9)" to "C D E G",
            "Cm(add9)" to "C D D# G",
            "C6" to "C E G A",
            "Cm6" to "C D# G A",
            "C69" to "C D E G A",
            "C6/9" to "C D E G A",
            "Cm6/9" to "C D D# G A",
            "C7" to "C E G A#",
            "Cmaj7" to "C E G B",
            "CMaj7" to "C E G B",
            "CM7" to "C E G B",
            "CΔ" to "C E G B",
            "CΔ7" to "C E G B",
            "C∆7" to "C E G B",
            "Cmaj" to "C E G",
            "CM" to "C E G",
            "Cm7" to "C D# G A#",
            "Cmi7" to "C D# G A#",
            "C-7" to "C D# G A#",
            "CmM7" to "C D# G B",
            "Cmmaj7" to "C D# G B",
            "Cm(maj7)" to "C D# G B",
            "C9" to "C D E G A#",
            "Cmaj9" to "C D E G B",
            "Cm9" to "C D D# G A#",
            "Cadd9" to "C D E G",
            "C11" to "C D F G A#",
            "Cm11" to "C D D# F G A#",
            "C13" to "C E G A A#",
            "Cm13" to "C D# G A A#",
            "Cmaj13" to "C E G A B",
            "Cdim" to "C D# F#",
            "C°" to "C D# F#",
            "Cdim7" to "C D# F# A",
            "C°7" to "C D# F# A",
            "Cø" to "C D# F# A#",
            "Cø7" to "C D# F# A#",
            "Cm7b5" to "C D# F# A#",
            "Cm7-5" to "C D# F# A#",
            "Caug" to "C E G#",
            "C+" to "C E G#",
            "C+7" to "C E G# A#",
            "C7+" to "C E G# A#",
            "C7#5" to "C E G# A#",
            "C7+5" to "C E G# A#",
            "Caugmaj7" to "C E G# B",
            "C7b5" to "C E F# A#",
            "C7b9" to "C C# E G A#",
            "C7(b9)" to "C C# E G A#",
            "C7-9" to "C C# E G A#",
            "C7#9" to "C D# E G A#",
            "C7(#9, b13)" to "C D# E G G# A#",
            "Cmaj7#11" to "C E F# G B",
            "C7b13" to "C E G G# A#",
            "C7alt" to "C D# E G# A#",
            "C7(9)" to "C D E G A#",
            "C(no3)" to "C G",
            "C7(omit5)" to "C E A#",
        ).forEach { (name, notes) ->
            val chord = assertNotNull(ChordProChords.parse(name), name)
            assertEquals(notes, ChordProChords.noteNames(chord).joinToString(" "), name)
        }
    }

    @Test
    fun `every root is read with its accidental`() {
        mapOf(
            "C" to 0, "C#" to 1, "Db" to 1, "D" to 2, "D#" to 3, "Eb" to 3, "E" to 4, "Fb" to 4, "E#" to 5, "F" to 5,
            "F#" to 6, "Gb" to 6, "G" to 7, "G#" to 8, "Ab" to 8, "A" to 9, "A#" to 10, "Bb" to 10, "B" to 11, "Cb" to 11,
            "H" to 11, "C♯" to 1, "B♭" to 10,
        ).forEach { (name, root) -> assertEquals(root, ChordProChords.parse(name)?.root, name) }
    }

    @Test
    fun `the bass of a slash chord is kept apart from the chord`() {
        assertEquals(Chord(root = 2, intervals = listOf(0, 4, 7), bass = 6), ChordProChords.parse("D/F#"))
        assertEquals(Chord(root = 2, intervals = listOf(0, 4, 7), bass = 6), ChordProChords.parse("D/f#"))
        assertEquals(Chord(root = 9, intervals = listOf(0, 3, 7, 10), bass = 7), ChordProChords.parse("Am7/G"))
        assertEquals(Chord(root = 0, intervals = listOf(0, 4, 7)), ChordProChords.parse("C/C"))
        assertEquals(listOf("D", "F#", "A"), ChordProChords.noteNames(ChordProChords.parse("D/F#")!!))
        assertEquals(listOf("A#", "C", "E", "G"), ChordProChords.noteNames(ChordProChords.parse("C/Bb")!!))
    }

    @Test
    fun `spellings of one chord share an id`() {
        listOf(
            listOf("C#m7", "Dbm7", "C#min7", "C#mi7", "C#-7", "(C#m7)", "C♯m7", "D♭m7", "c#7"),
            listOf("Bb", "A#", "B♭"),
            listOf("D/F#", "D/Gb", "D/f#"),
            listOf("Cmaj7", "CM7", "CΔ", "CΔ7"),
            listOf("Cm7b5", "Cø", "Cø7", "Cm7-5"),
        ).forEach { names ->
            assertEquals(1, names.map { assertNotNull(ChordProChords.parse(it), it).id }.distinct().size, names.toString())
        }
        assertEquals("C#:0.3.7.10", ChordProChords.parse("Dbm7")?.id)
        assertEquals("D:0.4.7/F#", ChordProChords.parse("D/Gb")?.id)
    }

    @Test
    fun `German names are read in German notation`() {
        assertEquals(11, ChordProChords.parse("H7", ChordNotation.GERMAN)?.root)
        assertEquals(10, ChordProChords.parse("B", ChordNotation.GERMAN)?.root)
        assertEquals(10, ChordProChords.parse("b", ChordNotation.GERMAN)?.root)
        assertEquals(ChordProChords.parse("Bbm"), ChordProChords.parse("b", ChordNotation.GERMAN))
        assertEquals(ChordProChords.parse("Bm"), ChordProChords.parse("h", ChordNotation.GERMAN))
        assertEquals(ChordProChords.parse("G/B"), ChordProChords.parse("G/H", ChordNotation.GERMAN))
        assertEquals(11, ChordProChords.parse("H")?.root)
        assertEquals(listOf("H", "D#", "F#"), ChordProChords.noteNames(ChordProChords.parse("B")!!, ChordNotation.GERMAN))
        assertEquals(listOf("B", "D", "F"), ChordProChords.noteNames(ChordProChords.parse("Bb")!!, ChordNotation.GERMAN, preferFlats = true))
    }

    @Test
    fun `flats are spelled where they are preferred`() {
        assertEquals(listOf("C", "Eb", "G", "Bb"), ChordProChords.noteNames(ChordProChords.parse("Cm7")!!, preferFlats = true))
    }

    @Test
    fun `a name is read exactly where it is recognized`() {
        val roots = listOf("C", "C#", "Db", "H", "Bb", "(A", "f#", "a")
        val middles = listOf("", "m", "maj", "M", "min", "dim", "°", "ø", "aug", "+", "-", "sus", "add", "Δ", "mi", "x", "alt")
        val numbers = listOf("", "2", "4", "5", "6", "69", "7", "9", "11", "13", "1", "٣")
        val tails = listOf("", "b5", "#5", "(b9)", "(#9, b13)", "/G", "/f#", "/9", "add9", "alt", "(no3)", "-5", "+", "/", "(", "()", "sus4", ")", "/E/G", "q")
        roots.forEach { root ->
            middles.forEach { middle ->
                numbers.forEach { number ->
                    tails.forEach { tail ->
                        val name = root + middle + number + tail + if (root.startsWith("(")) ")" else ""
                        val recognized = ChordProChordNames.isChordName(ChordProChordNames.lowercaseMinorExpanded(name) ?: name)
                        assertEquals(recognized, ChordProChords.parse(name) != null, name)
                    }
                }
            }
        }
    }

    @Test
    fun `words in brackets are no chords`() {
        listOf("Bridge", "N.C.", "*softly", "Intro", "x2", "", "Chorus 2x", "Halt").forEach { assertNull(ChordProChords.parse(it), it) }
    }
}
