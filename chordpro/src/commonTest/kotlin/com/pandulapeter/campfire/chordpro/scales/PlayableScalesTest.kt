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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayableScalesTest {

    private fun firstOf(name: String, count: Int) = PlayableScales.over(ChordProChords.parse(name)!!).take(count).map { it.id }

    private fun isBlues(root: Int, vararg names: String) = PlayableScales.isDominantBlues(root, names.map { ChordProChords.parse(it)!! })

    private fun over(root: Int, vararg names: String) = PlayableScales.over(root, names.map { ChordProChords.parse(it)!! }).map { it.id }

    @Test
    fun `a major key offers the pentatonic and hexatonic inside its scale, right after the common ones`() {
        val scales = over(0, "C", "F", "G")
        assertTrue("major_pentatonic" in scales && "major_hexatonic" in scales)
        assertEquals("major", scales.first())
        assertTrue(scales.indexOf("major_pentatonic") < scales.indexOf("bebop_major"))
        assertFalse("blues" in scales)
    }

    @Test
    fun `a minor key offers its pentatonic and the blues scale`() {
        val scales = over(9, "Am", "Dm", "Em")
        assertEquals("natural_minor", scales.first())
        assertTrue("minor_pentatonic" in scales && "blues" in scales)
    }

    @Test
    fun `a dominant blues offers the blues scales no scale holding its chords could`() {
        val scales = over(0, "C7", "F7", "G7")
        assertEquals(listOf("minor_pentatonic", "blues", "major_pentatonic", "major_blues", "mixolydian"), scales)
        assertTrue(PlayableScales.isDominantBlues(0, listOf(ChordProChords.parse("C9")!!)))
        assertFalse(PlayableScales.isDominantBlues(0, listOf(ChordProChords.parse("C7")!!, ChordProChords.parse("Dm")!!)))
    }

    @Test
    fun `a minor key with a major fifth still offers its pentatonic and the blues`() =
        assertEquals(listOf("harmonic_minor", "minor_pentatonic", "blues"), over(9, "Am", "Dm", "E7").take(3))

    @Test
    fun `a Mixolydian vamp offers the minor pentatonic and the blues after the major pentatonic`() =
        assertEquals(listOf("mixolydian", "major_pentatonic", "minor_pentatonic", "blues"), over(4, "E", "D", "A").take(4))

    @Test
    fun `a blues with a seventh on the IV is a dominant blues, a plain V7 cadence is not`() {
        fun isBlues(vararg names: String) = PlayableScales.isDominantBlues(0, names.map { ChordProChords.parse(it)!! })
        assertTrue(isBlues("C", "F7", "G"))
        assertTrue(isBlues("C7", "F", "G"))
        assertFalse(isBlues("C", "F", "G7"))
        assertFalse("blues" in over(0, "C", "F", "G7"))
        assertEquals(listOf("minor_pentatonic", "blues", "major_pentatonic", "major_blues", "mixolydian"), over(0, "C", "F7", "G").take(5))
    }

    @Test
    fun `rare scales come last`() {
        val scales = PlayableScales.over(0, listOf("C", "F", "G").map { ChordProChords.parse(it)!! })
        assertTrue(scales.indexOfLast { it.intervals.size <= 6 } < scales.indexOfFirst { it.intervals.size == 8 })
        assertEquals(listOf("natural_minor", "minor_pentatonic", "blues", "minor_hexatonic", "bebop_harmonic_minor"), over(9, "Am", "Dm", "Em"))
    }

    @Test
    fun `a lone dominant seventh offers Mixolydian, then the pentatonics and the blues`() =
        assertEquals(listOf("mixolydian", "major_pentatonic", "minor_pentatonic", "blues", "major_blues"), over(0, "C7").take(5))

    @Test
    fun `a rock riff offers the minor pentatonic and the blues on its root`() {
        assertEquals(listOf("minor_pentatonic", "blues"), over(4, "E", "G", "A", "E").take(2))
        assertEquals(listOf("minor_pentatonic", "blues"), over(0, "C", "Eb", "F", "C").take(2))
        assertFalse(PlayableScales.isRockRiff(0, listOf("C", "Ab", "Bb").map { ChordProChords.parse(it)!! }))
        assertFalse(PlayableScales.isRockRiff(0, listOf("C", "F", "G").map { ChordProChords.parse(it)!! }))
    }

    @Test
    fun `a dominant seventh chord's sheet offers the blues after Mixolydian`() {
        val scales = PlayableScales.over(ChordProChords.parse("A7")!!).map { it.id }
        assertTrue(scales.indexOf("mixolydian") < scales.indexOf("blues"))
        assertTrue("minor_pentatonic" in scales)
        assertFalse("blues" in PlayableScales.over(ChordProChords.parse("Amaj7")!!).map { it.id })
    }

    @Test
    fun `every suggestion is a scale on the root, never the chromatic one`() =
        assertTrue(listOf("C", "C7", "Cm7", "Cdim7", "Caug").none { Scales.CHROMATIC_ID in PlayableScales.over(ChordProChords.parse(it)!!).map { scale -> scale.id } })

    @Test
    fun `a twelve-bar blues is one group of the blues scales`() {
        val groups = SongScaleGroups.of(
            ChordProParser.parse(
                """
                {key: E}
                {start_of_verse}
                [E7]One [A7]two [E7]three [B7]four [A7]five [E7]six
                {end_of_verse}
                """.trimIndent(),
            ),
        )
        assertEquals(1, groups.size)
        assertEquals("E", groups.single().rootName)
        assertEquals("minor_pentatonic", groups.single().scales.first().id)
    }

    @Test
    fun `an altered dominant is not a blues`() {
        listOf("G7#5", "G7b9").forEach { assertFalse(isBlues(7, it), it) }
        listOf("C7b5", "C7b13").forEach { assertFalse(isBlues(0, it), it) }
        listOf("C7#9", "C9", "C13").forEach { assertTrue(isBlues(0, it), it) }
        assertFalse("blues" in firstOf("G7#5", 5))
    }

    @Test
    fun `an altered turnaround keeps a song's blues`() {
        assertTrue(isBlues(4, "E7", "A7", "E7", "B7b9"))
        assertFalse(isBlues(4, "E7b9", "A7", "B7"))
    }

    @Test
    fun `a chord's sheet starts with its quality's textbook scales`() {
        assertEquals(listOf("locrian", "locrian_natural_2"), firstOf("Cm7b5", 2))
        assertEquals(listOf("diminished"), firstOf("Cdim7", 1))
        assertEquals(listOf("mixolydian", "dorian"), firstOf("C7sus4", 2))
        assertEquals(listOf("dorian", "melodic_minor"), firstOf("Cm6", 2))
        assertEquals(listOf("dorian", "natural_minor"), firstOf("Cm7", 2))
        assertEquals(listOf("whole_tone", "altered"), firstOf("G7#5", 2))
        assertEquals(listOf("dominant_diminished", "phrygian_dominant"), firstOf("G7b9", 2))
        assertEquals(listOf("lydian"), firstOf("Cmaj7#11", 1))
        assertEquals(listOf("major", "lydian"), firstOf("Cmaj7", 2))
        assertEquals(listOf("mixolydian"), firstOf("C7", 1))
    }

    @Test
    fun `a first choice the chord's notes rule out is skipped`() = assertEquals(listOf("mixolydian_flat_6"), firstOf("C7b13", 1))
}
