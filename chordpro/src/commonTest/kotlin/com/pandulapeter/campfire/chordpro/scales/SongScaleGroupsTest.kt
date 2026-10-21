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
    fun `a transpose inside a section starts a part in the new key`() {
        val groups = transposed(
            """
            {key: C}
            {start_of_chorus: Chorus}
            [F]One [G]two [C]three
            {transpose: 1}
            [F]Four [G]five [C]six
            {end_of_chorus}
            """.trimIndent(),
        )
        assertEquals(listOf("C" to "major", "Db" to "major"), groups.map { it.rootName to it.scales.first().id })
        assertEquals(listOf(listOf("Chorus"), listOf("Chorus")), groups.map { it.labels })
    }

    @Test
    fun `the rest of a cut section is played once, so a chord outside its key there is a moment`() {
        val groups = SongScaleSuggestions.of(
            ChordProTransposer.transpose(
                ChordProParser.parse(
                    """
                    {key: C}
                    {start_of_verse: Verse}
                    [C]One [F]two [G]three [C]four
                    {transpose: 2}
                    [C]Five [F]six [E7]seven [Am]eight
                    {end_of_verse}
                    """.trimIndent(),
                ),
                0,
            ),
        )
        assertEquals(listOf("F#7"), groups.flatMap { group -> group.exceptions.map { it.chordName } })
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
    fun `the key names the centre wherever its own scale holds half the section, the chords deciding otherwise`() {
        val inKey = groups(song("C F G C", "G D Em C", key = "C")).single()
        assertEquals("C" to listOf("D"), inKey.rootName to inKey.exceptions.map { it.chordName })
        assertEquals(listOf("C" to "major", "C" to "mixolydian"), rootAndFirst(song("C F G C", "F Bb C F", key = "C")))
        assertEquals(listOf("C" to "major", "E" to "major"), rootAndFirst(song("C F G C", "E A B E", key = "C")))
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
    fun `a rock riff is played over the minor pentatonic and the blues on its root, with no exceptions`() {
        listOf("E G A E", "C Eb F C").forEach { chords ->
            val group = groups(song(chords)).single()
            assertEquals(listOf("minor_pentatonic", "blues", "mixolydian"), group.scales.take(3).map { it.id }, chords)
            assertTrue(group.exceptions.isEmpty(), chords)
        }
    }

    @Test
    fun `a section replaying another's chords joins its group before one of its own kind`() {
        val groups = groups(
            """
            {key: A}
            {start_of_verse: Intro}
            [A]x [C]x [G]x
            {end_of_verse}
            {start_of_chorus: Chorus}
            [Eb]x [Bb]x [Ab]x [Eb]x
            {end_of_chorus}
            {start_of_bridge: Bridge}
            [A]x [C]x [G]x
            {end_of_bridge}
            """,
        )
        assertEquals(listOf(listOf("Intro", "Bridge"), listOf("Chorus")), groups.map { it.labels })
    }

    @Test
    fun `a guess on three chords is left out beside a better answer, and kept alone`() {
        assertEquals(listOf("C" to "major"), rootAndFirst(song("C Am F G C Am F G", "D F G", key = "C")))
        assertEquals(1, groups(song("D F G", key = "A")).size)
    }

    @Test
    fun `a rock verse joins a riff of other chords on its root, and a chorus in another key stands apart`() {
        val groups = groups(
            """
            {key: A}
            {start_of_verse}
            [A]x [A]x [C]x [G]x
            {end_of_verse}
            {start_of_prechorus}
            [D]x [F]x [G]x
            {end_of_prechorus}
            {start_of_chorus}
            [C]x [Gm]x [Am]x [A#]x [F]x [Dm]x [A]x
            {end_of_chorus}
            {start_of_bridge}
            [A]x [C]x [G]x
            {end_of_bridge}
            {start_of_chorus}
            [C]x [Gm]x [Am]x [A#]x [F]x [Dm]x
            {end_of_chorus}
            {start_of_outro}
            [C]x [Gm]x [Am]x [A#]x [F]x [Fm]x [C]x
            {end_of_outro}
            """,
        )
        assertEquals(listOf("A" to "minor_pentatonic", "C" to "mixolydian"), groups.map { it.rootName to it.scales.first().id })
        assertTrue(groups.first().exceptions.isEmpty())
    }

    @Test
    fun `a vamp on one chord is answered as the chord's sheet is, unless a key says otherwise`() {
        assertEquals(listOf("D" to "dorian"), rootAndFirst(song("Dm7 Dm7 Dm7 Dm7")))
        assertEquals(listOf("A" to "natural_minor"), rootAndFirst(song("Am Am Am Am")))
        assertEquals(listOf("E" to "mixolydian"), rootAndFirst(song("E9 E9 E9 E9")))
        assertEquals(listOf("D" to "natural_minor"), rootAndFirst(song("Dm7 Dm7 Dm7 Dm7", key = "Dm")))
        assertEquals(listOf("D" to "dorian", "Eb" to "dorian"), rootAndFirst(song("Dm7 Dm7 Dm7 Dm7", "Ebm7 Ebm7", "Dm7 Dm7")))
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
        assertEquals(listOf("A" to "natural_minor"), rootAndFirst(song("Am Dm E", key = "A Minor")))
        assertEquals(listOf("D" to "dorian"), rootAndFirst(song("Dm G Dm G", key = "D Minor")))
        assertEquals(listOf("Bb" to "major"), rootAndFirst(song("Bb Eb F Gm", key = "B♭")))
        assertEquals(listOf("Bb" to "major"), rootAndFirst(song("Bb Eb F Gm", key = "B♭ Major")))
        assertEquals(listOf("A" to "mixolydian"), rootAndFirst(song("A G D A", key = "A Mixolydian")))
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
    fun `a key's home is kept however many chords it leaves out`() {
        val blueBossa = groups(song("Cm7 Cm7 Fm7 Fm7 Dm7b5 G7 Cm7 Cm7 Ebm7 Ab7 Dbmaj7 Dbmaj7 Dm7b5 G7 Cm7 G7", key = "Cm")).single()
        assertEquals("C" to "natural_minor", blueBossa.rootName to blueBossa.scales.first().id)
        assertEquals(listOf("G7", "Dbmaj7", "Ebm7", "Ab7"), blueBossa.exceptions.map { it.chordName })
        val allOfMe = groups(
            song(
                "Cmaj7 Cmaj7 E7 E7 A7 A7 Dm7 Dm7 E7 E7 Am7 Am7 D7 D7 Dm7 G7",
                "Cmaj7 Cmaj7 E7 E7 A7 A7 Dm7 Dm7 F Fm Cmaj7 A7 Dm7 G7 C6 G7",
                key = "C",
            ),
        ).single()
        assertEquals("C" to "major", allOfMe.rootName to allOfMe.scales.first().id)
        assertEquals(listOf("E7", "A7", "D7", "Fm"), allOfMe.exceptions.map { it.chordName })
        assertEquals(listOf("C" to "major"), rootAndFirst(song("C E7 A7 Dm E7 Am D7 Dm G7 C E7 A7 Dm F Fm C A7 Dm G7 C", key = "C")))
    }

    @Test
    fun `a secondary dominant and a leading-tone diminished chord are the key's, not against it`() {
        listOf("C", null).forEach { key ->
            val gospel = groups(song("C E7 Am C7 F F#dim C/G A7 D7 G7 C G7", key = key)).single()
            assertEquals("C" to "major", gospel.rootName to gospel.scales.first().id)
            assertEquals(listOf("E7", "C7", "F#dim", "A7", "D7"), gospel.exceptions.map { it.chordName })
        }
        val country = groups(song("G G C G G A7 D7 D7 G G7 C Cm G D7 G G", key = "G")).single()
        assertEquals("G" to "major", country.rootName to country.scales.first().id)
        assertEquals(listOf("A7", "G7", "Cm"), country.exceptions.map { it.chordName })
    }

    @Test
    fun `a key must hold half the chords by itself`() {
        assertEquals(listOf("E" to "major"), rootAndFirst(song("E A B E", key = "C")))
        assertEquals(1, groups(song("D F G", key = "A")).size)
    }

    @Test
    fun `the key's own scale beats its mode unless the mode holds two chords more`() {
        listOf("C Gm7 C7 F C Gm7 C7 F G7 C", "C F C F C F Ab Bb C C", "C Caug C6 C7 F F C C").forEach { chords ->
            assertEquals(listOf("C" to "major"), rootAndFirst(song(chords, key = "C")), chords)
        }
        assertEquals(listOf("A" to "mixolydian"), rootAndFirst(song("A G D A A G D A A G D A E A", key = "A")))
        assertEquals(listOf("C" to "mixolydian"), rootAndFirst(song("C7 Bb F G7 C7 Bb F G7", key = "C")))
    }

    @Test
    fun `a part in the key of the fifth is no part of the key, its chords leading into it or not`() =
        assertEquals(listOf("G" to "major", "D" to "major"), rootAndFirst(song("G C D G Em C D G", "D A Bm F#m G D E A", key = "G")))

    @Test
    fun `a jazz blues is one group on its tonic, led by the blues scales, its departures the exceptions`() {
        listOf("F", null).forEach { key ->
            val blues = groups(song("F7 Bb7 F7 Cm7 F7 Bb7 Bdim7 F7 Am7 D7 Gm7 C7 F7 D7 Gm7 C7", key = key)).single()
            assertEquals("F" to "minor_pentatonic", blues.rootName to blues.scales.first().id, key)
            assertEquals(listOf("Bb7", "D7", "C7", "Bdim7", "Am7"), blues.exceptions.map { it.chordName }, key)
        }
        listOf("E", null).forEach { key ->
            val twelveBar = groups(song("E E E E A A E E B7 A E B7", key = key)).single()
            assertEquals("E" to "minor_pentatonic", twelveBar.rootName to twelveBar.scales.first().id, key)
            assertTrue(twelveBar.exceptions.isEmpty(), key)
        }
        assertEquals(listOf("D" to "major"), rootAndFirst(song("D A G D D A G D", key = "D")))
    }

    @Test
    fun `a passing tonic seventh is a moment of the key, not a blues`() {
        val folk = groups(song("G G G7 C C G G D7 G G", key = "G")).single()
        assertEquals("G" to "major", folk.rootName to folk.scales.first().id)
        assertEquals(listOf("G7"), folk.exceptions.map { it.chordName })
        val gospel = groups(song("C F G C7 F G C C", key = "C")).single()
        assertEquals("C" to "major", gospel.rootName to gospel.scales.first().id)
        assertEquals(listOf("C7"), gospel.exceptions.map { it.chordName })
        assertEquals(listOf("A" to "minor_pentatonic"), rootAndFirst(song("A A A A7 D D A A E D A E", key = "A")))
    }

    @Test
    fun `a blues is heard on its tonic seventh, not on the chord its V leads to`() {
        assertEquals(listOf("F" to "minor_pentatonic"), rootAndFirst(song("F7 Bb7 F7 Cm7 F7 Bb7 Bdim7 F7 Am7 D7 Gm7 C7 F7 D7 Gm7 C7")))
        assertEquals(listOf("E" to "minor_pentatonic"), rootAndFirst(song("E7 A7 E7 B7 A7 E7")))
    }

    @Test
    fun `a tonic seventh needs its target unplayed or played as a seventh, and the first chord's seventh comes first`() {
        assertEquals(listOf("G" to "major"), rootAndFirst(song("D7 G C D7")))
        assertEquals(listOf("E" to "minor_pentatonic"), rootAndFirst(song("E7 A7 B7 A7")))
    }

    @Test
    fun `a cadence into a secondary dominant is no cadence home`() {
        val allOfMe = groups(song("Cmaj7 Cmaj7 E7 E7 A7 A7 Dm7 Dm7 E7 E7 Am7 Am7 D7 D7 Dm7 G7")).single()
        assertEquals("C" to "major", allOfMe.rootName to allOfMe.scales.first().id)
        assertEquals(listOf("E7", "A7", "D7"), allOfMe.exceptions.map { it.chordName })
    }

    @Test
    fun `a guessed root is weighed by its home scale, not by the minor family`() {
        val flyMe = groups(song("Am7 Dm7 G7 Cmaj7 Fmaj7 Bm7b5 E7 Am7 A7 Dm7 G7 Cmaj7 Am7 Dm7 G7 Cmaj7 Bm7b5 E7")).single()
        assertEquals("C" to "major", flyMe.rootName to flyMe.scales.first().id)
        assertEquals(listOf("E7", "A7"), flyMe.exceptions.map { it.chordName })
    }

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
    fun `recalls after a transpose count once, so the song's key stays its home`() {
        val groups = SongScaleSuggestions.of(ChordProTransposer.transpose(ChordProParser.parse("$modulatedChorus\n{transpose: 2}\n{chorus}\n{chorus}\n{chorus}"), 0))
        assertEquals(listOf("C" to true, "D" to false), groups.map { it.rootName to it.isHome })
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
