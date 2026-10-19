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

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScaleExceptionsTest {

    private fun groups(chords: String, key: String? = null) = SongScaleGroups.of(
        ChordProParser.parse(
            (listOfNotNull(key?.let { "{key: $it}" }) + "{start_of_verse}" + chords.split(' ').joinToString(" ") { "[$it]x" } + "{end_of_verse}")
                .joinToString("\n"),
        ),
    )

    private fun group(chords: String, key: String? = null) = groups(chords, key).single()

    private val ScaleGroup.home get() = rootName to scales.first().id

    private val ScaleException.first get() = Triple(chordName, rootName, scales.first().id)

    @Test
    fun `a secondary dominant is an exception to the major key`() {
        val group = group("C A7 Dm G7")
        assertEquals("C" to "major", group.home)
        assertEquals(listOf(Triple("A7", "A", "mixolydian")), group.exceptions.map { it.first })
        assertTrue("mixolydian_flat_6" in group.exceptions.single().scales.map { it.id })
    }

    @Test
    fun `a major fifth in a minor key is the harmonic minor of the key`() {
        listOf("Am Dm E7" to "E7", "Am G F E" to "E", "Am7 Dm7 Am7 E7" to "E7", "Em D C B7" to "B7").forEach { (chords, outside) ->
            val group = group(chords)
            val tonic = chords.take(1)
            assertEquals(tonic to "natural_minor", group.home, chords)
            assertEquals(listOf(Triple(outside, tonic, "harmonic_minor")), group.exceptions.map { it.first }, chords)
        }
        val blues = group("Am7 Dm7 Am7 E7").scales.map { it.id }
        assertTrue("minor_pentatonic" in blues && "blues" in blues)
    }

    @Test
    fun `the minor family holds a minor song with a raised sixth`() {
        val house = group("Am C D F Am C E")
        assertEquals("A" to "natural_minor", house.home)
        assertEquals(Triple("D", "A", "dorian"), house.exceptions.single { it.chordName == "D" }.first)
        val hotel = group("Bm F# A E G D Em F#")
        assertEquals("B" to "natural_minor", hotel.home)
        assertEquals(Triple("E", "B", "dorian"), hotel.exceptions.single { it.chordName == "E" }.first)
        assertEquals(Triple("F#", "B", "harmonic_minor"), hotel.exceptions.single { it.chordName == "F#" }.first)
    }

    @Test
    fun `a borrowed chord is an exception on its own root`() {
        val group = group("C Ab Bb C")
        assertEquals("C" to "mixolydian", group.home)
        assertEquals(listOf("Ab" to "Ab"), group.exceptions.map { it.chordName to it.rootName })
        assertEquals(listOf("Bb", "E7"), group("Am Dm Bb E7").exceptions.map { it.chordName })
    }

    @Test
    fun `a jazz blues is Mixolydian with the IV and V named`() {
        val group = group("C7 F7 C7 Dm7 G7 C7")
        assertEquals("C" to "mixolydian", group.home)
        assertEquals(listOf("F7" to "F", "G7" to "G"), group.exceptions.map { it.chordName to it.rootName })
    }

    @Test
    fun `a song most of whose chords no scale holds has no group`() = assertTrue(groups("G B C Cm").isEmpty())

    @Test
    fun `a dominant blues has no exceptions`() {
        val group = group("E7 A7 E7 E7 A7 A7 E7 E7 B7 A7 E7 B7")
        assertEquals("E" to "minor_pentatonic", group.home)
        assertTrue(group.exceptions.isEmpty())
        assertTrue(group("C Am F G").exceptions.isEmpty())
    }

    @Test
    fun `a diatonic song with one outside chord keeps its group`() {
        val group = group("C F G E")
        assertEquals("C" to "major", group.home)
        assertEquals(listOf("E"), group.exceptions.map { it.chordName })
        assertTrue("phrygian_dominant" in group.exceptions.single().scales.map { it.id })
    }

    @Test
    fun `an exception offers only seven-note scales holding the chord, the common ones first`() {
        val exception = group("C A7 Dm G7").exceptions.single()
        assertTrue(exception.scales.none { it.id == "minor_pentatonic" || it.id == "blues" })
        val borrowed = group("C Am F Fm C").exceptions.single().scales
        assertTrue(borrowed.first() in Scales.common && "dorian" in borrowed.map { it.id })
        listOf("C A7 Dm G7", "C C#dim Dm G7", "Am Dm Bb E7", "C Ab Bb C", "Am C D F Am C E", "C E7 Am F G", "C Bb F G").forEach { chords ->
            group(chords).exceptions.forEach { exception ->
                val chord = ChordProChords.parse(exception.chordName)!!
                val root = ChordProChords.pitchClass(exception.rootName)!!
                val notes = chord.intervals.map { (chord.root + it).mod(12) }
                assertTrue(exception.scales.all { it.intervals.size == 7 && Scales.pitchClasses(it, root).containsAll(notes) }, chords)
            }
        }
    }

    @Test
    fun `an exception names the chord as the song first writes it`() =
        assertEquals(listOf("D/F#"), group("Am Am/G# Am/G D/F# Fmaj7 G Am", key = "Am").exceptions.map { it.chordName })
}
