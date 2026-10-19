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
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongScaleGroupsTest {

    private fun groups(text: String) = SongScaleGroups.of(ChordProParser.parse(text.trimIndent()))

    private val ScaleGroup.labels
        get() = blocks.map { block ->
            when (block) {
                is ChordProBlock.Section -> block.label ?: block.type.toString()
                else -> "Recall " + (block as ChordProBlock.ChorusRecall).label.orEmpty()
            }
        }

    private val modulatedChorus = """
        {key: C}
        {start_of_verse: Verse}
        [C]One [F]two [G]three [C]four
        {end_of_verse}
        {start_of_chorus: Chorus}
        [F]Five [G]six [C]seven
        {end_of_chorus}
    """.trimIndent()

    private fun transposed(text: String) = SongScaleGroups.of(ChordProTransposer.transpose(ChordProParser.parse(text), 0))

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
    fun `a rock riff is played over the minor pentatonic and the blues on its root`() {
        listOf("E G A E" to "G", "C Eb F C" to "Eb").forEach { (chords, outside) ->
            val group = groups(song(chords)).single()
            assertEquals(listOf("major", "minor_pentatonic", "blues"), group.scales.take(3).map { it.id }, chords)
            assertEquals(listOf(outside), group.exceptions.map { it.chordName }, chords)
        }
    }

    @Test
    fun `a Dorian vamp of triads is Dorian, not melodic minor`() {
        assertEquals(listOf("A" to "dorian"), rootAndFirst(song("Am D")))
        assertEquals(listOf("D" to "dorian"), rootAndFirst(song("Dm G Dm G")))
        assertEquals(listOf("E" to "dorian"), rootAndFirst(song("Em G D A")))
    }

    @Test
    fun `a minor key is read from its m`() {
        assertEquals(listOf("A" to "natural_minor"), rootAndFirst(song("Am Dm E", key = "A minor")))
        assertEquals(listOf("A" to "natural_minor"), rootAndFirst(song("Am Dm E", key = "Am")))
        assertEquals(listOf("E"), groups(song("Am Dm E", key = "A minor")).single().exceptions.map { it.chordName })
        assertEquals(listOf("A" to "major"), rootAndFirst(song("A D E", key = "Amaj")))
    }

    @Test
    fun `an eight-note answer is never taken on another root`() =
        assertEquals(listOf("A" to "natural_minor"), rootAndFirst(song("Am Am/G# Am/G D/F# Fmaj7 G Am", key = "Am")))

    @Test
    fun `a verse and a chorus in one minor key are one group`() {
        val group = groups(song("Am Dm Em", "Am Dm E", key = "Am")).single()
        assertEquals("A" to "natural_minor", group.rootName to group.scales.first().id)
        assertEquals(listOf("E" to "harmonic_minor"), group.exceptions.map { it.chordName to it.scales.first().id })
    }

    @Test
    fun `a verse with a secondary dominant joins its key's chorus`() =
        assertEquals(1, groups(song("C A7 Dm G7", "F G C", key = "C")).size)

    @Test
    fun `a chorus recalled after a transpose is a part in the new key`() {
        val groups = transposed("$modulatedChorus\n{transpose: 2}\n{chorus}")
        assertEquals(listOf("C", "D"), groups.map { it.rootName })
        assertTrue(groups[1].blocks.single() is ChordProBlock.ChorusRecall)
        assertEquals("major", groups[1].scales.first().id)
    }

    @Test
    fun `two recalls after one transpose are one group`() {
        val groups = transposed("$modulatedChorus\n{transpose: 2}\n{chorus}\n{chorus}")
        assertEquals(2, groups.size)
        assertEquals(2, groups[1].blocks.size)
        assertTrue(groups[1].blocks.all { it is ChordProBlock.ChorusRecall })
    }

    @Test
    fun `a chorus recalled in its own key is not counted twice`() {
        val withRecall = transposed("$modulatedChorus\n{chorus}").single()
        val without = transposed(modulatedChorus).single()
        assertEquals(listOf("Verse", "Chorus", "Recall "), withRecall.labels)
        assertEquals(without.rootName to without.scales, withRecall.rootName to withRecall.scales)
    }

    @Test
    fun `an untransposed song reads its recall as written`() =
        assertEquals(listOf("C"), SongScaleGroups.of(ChordProParser.parse("$modulatedChorus\n{transpose: 2}\n{chorus}")).map { it.rootName })

    @Test
    fun `a section plays chords where any of its lines do`() {
        val blocks = ChordProParser.parse(
            """
            {start_of_chorus}
            Words
            [C]More [G]words
            {end_of_chorus}
            {start_of_verse}
            Only words
            {end_of_verse}
            {start_of_grid}
            | C . . . | G . . . |
            {end_of_grid}
            {chorus}
            """.trimIndent(),
        ).blocks
        assertEquals(listOf(true, false, true, true), blocks.map(SongScaleGroups::playsChords))
        val silent = ChordProParser.parse("{start_of_chorus}\nWords\n{end_of_chorus}\n{chorus}").blocks.last()
        assertTrue(silent is ChordProBlock.ChorusRecall && !SongScaleGroups.playsChords(silent))
    }
}
