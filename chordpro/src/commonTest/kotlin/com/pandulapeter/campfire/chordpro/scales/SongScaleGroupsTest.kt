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

    private fun rootAndFirst(text: String) = groups(text).map { it.rootName to it.scales.first().id }

    /** A song of one section per entry of [sections], each chord a word of its own, under [key] where there is one. */
    private fun song(vararg sections: String, key: String? = null) = (listOfNotNull(key?.let { "{key: $it}" }) +
        sections.mapIndexed { index, chords ->
            "{start_of_verse: Part ${index + 1}}\n" + chords.split(' ').joinToString(" ") { "[$it]x" } + "\n{end_of_verse}"
        }).joinToString("\n")

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

    @Test
    fun `a key is read from the chords, not from the first chord`() {
        assertEquals(listOf("C" to "major"), rootAndFirst(song("F G C Am")))
        assertEquals(listOf("C" to "major"), rootAndFirst(song("F G C F G C")))
        assertEquals(listOf("D" to "major"), rootAndFirst(song("G A D Bm")))
        assertEquals(listOf("G" to "major"), rootAndFirst(song("C D Am G D C Am G Em G Em G Em A7sus4")))
    }

    @Test
    fun `a mode is the answer only on a root that outweighs the home key`() {
        assertEquals(listOf("E" to "mixolydian"), rootAndFirst(song("E D A E")))
        assertEquals(listOf("D" to "mixolydian"), rootAndFirst(song("D C G")))
        assertEquals(listOf("C" to "major"), rootAndFirst(song("F C G F")))
        assertEquals(listOf("A" to "natural_minor"), rootAndFirst(song("Am F C G")))
    }

    @Test
    fun `the key names the centre only where its scale holds the section`() {
        assertEquals(listOf("C" to "major", "G" to "major"), rootAndFirst(song("C F G C", "G D Em C", key = "C")))
        assertEquals(listOf("C" to "major", "F" to "major"), rootAndFirst(song("C F G C", "F Bb C F", key = "C")))
        assertEquals(listOf("C" to "major"), rootAndFirst(song("C Am F G", key = "D")))
        assertEquals(listOf("A" to "natural_minor"), rootAndFirst(song("Am Dm Em", key = "Am")))
    }

    @Test
    fun `a mode needs the part to start on its root or the key to name it`() {
        assertEquals(listOf("A" to "natural_minor"), rootAndFirst(song("Am G F G")))
        assertEquals(listOf("G" to "mixolydian"), rootAndFirst(song("G F C G")))
        assertEquals(listOf("E" to "mixolydian"), rootAndFirst(song("D A E E", key = "E")))
        assertEquals(listOf("A" to "major"), rootAndFirst(song("D A E E")))
    }

    @Test
    fun `a Dorian vamp of triads is Dorian, not melodic minor`() {
        assertEquals(listOf("A" to "dorian"), rootAndFirst(song("Am D")))
        assertEquals(listOf("D" to "dorian"), rootAndFirst(song("Dm G Dm G")))
        assertEquals(listOf("E" to "dorian"), rootAndFirst(song("Em G D A")))
    }

    @Test
    fun `a minor key is read from its m`() {
        assertEquals(listOf("A" to "harmonic_minor"), rootAndFirst(song("Am Dm E", key = "A minor")))
        assertEquals(listOf("A" to "harmonic_minor"), rootAndFirst(song("Am Dm E", key = "Am")))
        assertEquals(listOf("A" to "major"), rootAndFirst(song("A D E", key = "Amaj")))
    }

    @Test
    fun `an eight-note answer is never taken on another root`() =
        assertTrue(groups(song("Am Am/G# Am/G D/F# Fmaj7 G Am", key = "Am")).none { it.rootName == "D" })
}
