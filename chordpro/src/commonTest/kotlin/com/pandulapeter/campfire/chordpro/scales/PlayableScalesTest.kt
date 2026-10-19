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
        assertFalse(PlayableScales.isDominantBlues(0, listOf(ChordProChords.parse("C")!!, ChordProChords.parse("F7")!!)))
        assertFalse(PlayableScales.isDominantBlues(0, listOf(ChordProChords.parse("C7")!!, ChordProChords.parse("Dm")!!)))
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
}
