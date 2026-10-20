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
    fun `a secondary dominant of a minor chord is played as the harmonic minor it leads into`() {
        val group = group("C A7 Dm G7")
        assertEquals("C" to "major", group.home)
        assertEquals(listOf(Triple("A7", "D", "harmonic_minor")), group.exceptions.map { it.first })
        assertEquals(listOf(Triple("E7", "A", "harmonic_minor")), group("C E7 Am F G", key = "C").exceptions.map { it.first })
    }

    @Test
    fun `a secondary dominant of a major chord is Mixolydian on its own root`() =
        assertEquals(listOf(Triple("D7", "D", "mixolydian")), group("C F D7 G7 C").exceptions.map { it.first })

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
    fun `a flat second in a minor song is its Phrygian`() {
        assertEquals(listOf(Triple("Bb", "A", "phrygian")), group("Am Bb Am Bb", key = "Am").exceptions.map { it.first })
        assertEquals(listOf(Triple("Bb", "A", "phrygian"), Triple("E7", "A", "harmonic_minor")), group("Am Dm Bb E7").exceptions.map { it.first })
        assertEquals(listOf(Triple("Ab", "G", "phrygian")), group("Gm Ab Gm Ab Gm", key = "Gm").exceptions.map { it.first })
    }

    @Test
    fun `a borrowed chord is played over the home's own mode where that is closest`() {
        assertEquals(listOf("C" to "minor_pentatonic"), groups("C Ab Bb C").map { it.home })
        assertEquals(listOf(Triple("G", "A", "mixolydian")), group("A D E E7 A D E E7 A D E E7 G", key = "A").exceptions.map { it.first })
        assertEquals(listOf("Bb", "E7"), group("Am Dm Bb E7").exceptions.map { it.chordName })
    }

    @Test
    fun `a jazz blues is Mixolydian with the IV and V named`() {
        val group = group("C7 F7 C7 Dm7 G7 C7")
        assertEquals("C" to "mixolydian", group.home)
        assertEquals(listOf("F7" to "F", "G7" to "G"), group.exceptions.map { it.chordName to it.rootName })
    }

    @Test
    fun `a song most of whose chords no scale holds has no group`() = assertTrue(groups("C F# Bb E Ab D").isEmpty())

    @Test
    fun `a home holding half the chords answers a part of borrowed chords on its own`() {
        val creep = group("G B Cmaj7 Cm", key = "G")
        assertEquals("G" to "major", creep.home)
        assertEquals(listOf(Triple("B", "E", "harmonic_minor")), creep.exceptions.filter { it.chordName == "B" }.map { it.first })
        assertTrue(creep.exceptions.any { it.chordName == "Cm" })
    }

    @Test
    fun `a home leaving out more than two chords and a third of them has no group`() {
        assertTrue(groups("C Ebm F#m A Dbm Em").isEmpty())
        assertEquals("C" to "major", group("C Dm Em F G Am Cmaj7 Dm7 G7 Fmaj7 Am7 A7 E7 D7 C").home)
    }

    @Test
    fun `every chord a home leaves out is an exception, the most played first`() =
        assertEquals(listOf("E7", "A7"), group("C E7 Am F G C E7 Am A7 Dm G7 C").exceptions.map { it.chordName })

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
        assertEquals(listOf(Triple("E", "A", "harmonic_minor")), group.exceptions.map { it.first })
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
