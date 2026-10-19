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
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongScaleGroupsTest {

    private fun groups(text: String) = SongScaleGroups.of(ChordProParser.parse(text.trimIndent()))

    private val ScaleGroup.labels get() = sections.map { it.label ?: it.type.toString() }

    @Test
    fun `a song one scale fits is one group of every section that plays a chord`() {
        val groups = groups(
            """
            {key: C}
            {start_of_verse: Verse}
            [C]One [Am]two [F]three [G]four
            {end_of_verse}
            {comment: No chords here}
            {start_of_chorus: Chorus}
            [F]Five [G]six [C]seven
            {end_of_chorus}
            """,
        )
        assertEquals(1, groups.size)
        assertEquals("C", groups.single().rootName)
        assertEquals("major", groups.single().scales.first().id)
        assertEquals(listOf("Verse", "Chorus"), groups.single().labels)
    }

    @Test
    fun `a solo in another key is a group of its own, and the verse after it goes back to the verses`() {
        val groups = groups(
            """
            {key: C}
            {start_of_verse: Intro}
            [C]One [G]two
            {end_of_verse}
            {start_of_verse: Verse 1}
            [C]One [F]two [G]three
            {end_of_verse}
            {start_of_verse: Solo}
            [D]One [F#m]two [A]three [Bm]four
            {end_of_verse}
            {start_of_verse: Verse 2}
            [C]One [F]two [G]three
            {end_of_verse}
            """,
        )
        assertEquals(listOf(listOf("Intro", "Verse 1", "Verse 2"), listOf("Solo")), groups.map { it.labels })
        assertEquals(listOf("C", "D"), groups.map { it.rootName })
        assertEquals("major", groups[1].scales.first().id)
    }

    @Test
    fun `a transpose is read for the key it names, the chords after it as the transposer left them`() {
        val song = ChordProParser.parse(
            """
            {key: C}
            {start_of_verse: Verse}
            [C]One [F]two [G]three
            {end_of_verse}
            {transpose: 2}
            {start_of_verse: Last verse}
            [C]One [F]two [G]three
            {end_of_verse}
            """.trimIndent(),
        )
        val transposed = SongScaleGroups.of(ChordProTransposer.transpose(song, 0))
        assertEquals(listOf("C", "D"), transposed.map { it.rootName })
        assertEquals(listOf("major", "major"), transposed.map { it.scales.first().id })
        assertEquals(listOf("C"), SongScaleGroups.of(song).map { it.rootName })
    }

    @Test
    fun `a chorus leaving the verse's scale gets the scales that fit it`() {
        val groups = groups(
            """
            {key: C}
            {start_of_verse: Verse}
            [C]One [F]two [G]three [Bdim]four
            {end_of_verse}
            {start_of_chorus: Chorus}
            [C]One [Bb]two [F]three
            {end_of_chorus}
            """,
        )
        assertEquals(2, groups.size)
        assertEquals(listOf("C", "C"), groups.map { it.rootName })
        assertTrue(groups[1].scales.any { it.id == "mixolydian" })
        assertTrue(groups[0].scales.none { it.id == "mixolydian" })
    }

    @Test
    fun `a song without chords has no groups`() = assertTrue(groups("Just words\nand more words").isEmpty())
}
