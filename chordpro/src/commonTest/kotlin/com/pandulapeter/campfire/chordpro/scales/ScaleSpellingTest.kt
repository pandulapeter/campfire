/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.scales

import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScaleSpellingTest {

    private fun names(id: String, root: String, notation: ChordNotation = ChordNotation.STANDARD) =
        ScaleSpelling.noteNames(Scales.fromId(id)!!, root, notation).joinToString(" ")

    @Test
    fun `a scale is spelled one letter per degree`() {
        assertEquals("C D E F G A B", names("major", "C"))
        assertEquals("A B C D E F G", names("natural_minor", "A"))
        assertEquals("F G A Bb C D E", names("major", "F"))
        assertEquals("Eb F Gb Ab Bb C Db", names("dorian", "Eb"))
        assertEquals("C Eb F Gb G Bb", names("blues", "C"))
    }

    @Test
    fun `a note the letter cannot name simply is named with the root's accidental`() {
        assertEquals("F# G# A# B C# D# F", names("major", "F#"))
        assertEquals("B D D# F F# G# A#", names("lydian_sharp_2", "B"))
        assertEquals("Db F G Ab C", names("chinese", "Db"))
    }

    @Test
    fun `the names follow the notation`() {
        assertEquals("H C# D E F# G A", names("natural_minor", "B", ChordNotation.GERMAN))
        assertEquals("Do Re Mi Fa Sol La Si", names("major", "C", ChordNotation.LATIN))
        assertEquals("C D E F G A B", names("major", "C", ChordNotation.ROMAN))
    }

    @Test
    fun `every scale in every key spells one name per note, each the note it is`() {
        val roots = listOf("C", "C#", "Db", "D", "D#", "Eb", "E", "F", "F#", "Gb", "G", "G#", "Ab", "A", "A#", "Bb", "B")
        Scales.all.forEach { scale ->
            roots.forEach { root ->
                val names = ScaleSpelling.noteNames(scale, root)
                val rootPitchClass = ChordProChords.pitchClass(root)!!
                assertEquals(scale.intervals.map { (rootPitchClass + it) % 12 }, names.map { ChordProChords.pitchClass(it) }, "$root ${scale.id}")
            }
        }
    }

    @Test
    fun `a mode's parent root is spelled from the mode's root`() {
        assertEquals("C", ScaleSpelling.parentRootName(Scales.fromId("dorian")!!, "D"))
        assertEquals("Eb", ScaleSpelling.parentRootName(Scales.fromId("mixolydian")!!, "Bb"))
        assertEquals("C", ScaleSpelling.parentRootName(Scales.fromId("minor_pentatonic")!!, "A"))
        assertEquals("Db", ScaleSpelling.parentRootName(Scales.fromId("dorian")!!, "Eb"))
        assertNull(ScaleSpelling.parentRootName(Scales.fromId("major")!!, "C"))
    }
}
