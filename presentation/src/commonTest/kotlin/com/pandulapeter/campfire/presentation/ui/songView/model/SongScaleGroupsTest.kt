/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.model

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.chordpro.scales.Scales
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongScaleGroupsTest {

    private val labels = DefaultSectionLabels(
        verse = "Verse",
        chorus = "Chorus",
        bridge = "Bridge",
        tab = "Tab",
        grid = "Grid",
        intro = "Intro",
        preChorus = "Pre-chorus",
        solo = "Solo",
        outro = "Outro",
        shouldNumberSections = true,
    )

    private fun groups(text: String, labels: DefaultSectionLabels = this.labels) =
        songScaleGroupsOf(ChordProParser.parse(text.trimIndent()), labels, ChordNotation.STANDARD)

    private val unnumbered = labels.copy(shouldNumberSections = false)

    private val verse = "{start_of_verse}\n[C]One [F]two [G]three\n{end_of_verse}\n"
    private val chorus = "{start_of_chorus}\n[F]Four [G]five [C]six\n{end_of_chorus}\n"
    private val modulated = "{start_of_bridge}\n[D]One [G]two [A]three [F#m]four\n{end_of_bridge}\n"

    /** Two sections in two keys of their own, so that no group of a song ending in them plays half its chords. */
    private val elsewhere = "{start_of_solo}\n[Eb]x [Ab]x [Bb]x [Cm]x [Eb]x\n{end_of_solo}\n" +
        "{start_of_outro}\n[F#]x [B]x [C#]x [D#m]x [F#]x\n{end_of_outro}\n"

    @Test
    fun `a song one scale fits is one group named nothing`() {
        val groups = groups("{key: C}\n" + verse + chorus + verse)
        assertEquals(1, groups.size)
        assertNull(groups.single().label)
    }

    @Test
    fun `a song with a home names only its moments`() {
        val groups = groups("{key: C}\n" + verse + chorus + modulated + verse)
        assertEquals(listOf(null, "Bridge"), groups.map { it.label })
        assertEquals(listOf(true, false), groups.map { it.isHome })
        assertEquals(listOf("C", "D"), groups.map { it.rootName })
    }

    @Test
    fun `a kind held whole by a group is named once, and the rest by their own headings`() {
        val groups = groups("{key: C}\n" + verse + chorus + modulated + verse + elsewhere)
        assertEquals(listOf("Verse, Chorus", "Bridge", "Solo", "Outro"), groups.map { it.label })
        assertTrue(groups.none { it.isHome })
    }

    @Test
    fun `verses split between groups are named by their numbers`() {
        val lastVerse = "{start_of_verse}\n[D]One [G]two [A]three\n{end_of_verse}\n"
        val groups = groups("{key: C}\n" + verse + verse + lastVerse + elsewhere)
        assertEquals(listOf("Verse 1, Verse 2", "Verse 3", "Solo", "Outro"), groups.map { it.label })
    }

    @Test
    fun `a group names its sections in the order the song plays them, a kind once at its first`() {
        val groups = groups("{key: C}\n" + chorus + verse + modulated + verse + elsewhere)
        assertEquals(listOf("Chorus, Verse", "Bridge", "Solo", "Outro"), groups.map { it.label })
    }

    @Test
    fun `a lone heading keeps its number, and a number without a space before it is the heading's own`() {
        val take = "{start_of_verse: Take 5}\n[C]One [F]two\n{end_of_verse}\n"
        val twice = "{start_of_chorus: Chorus x2}\n[C]One [F]two\n{end_of_chorus}\n"
        assertEquals(listOf("Take 5, Chorus x2", "Bridge", "Solo", "Outro"), groups("{key: C}\n" + take + twice + modulated + elsewhere).map { it.label })
    }

    @Test
    fun `a group offers at most a handful of scales`() {
        val scales = groups("[C]One [G]two").single().scales
        assertTrue(scales.isNotEmpty() && scales.size <= MAX_GROUP_SCALES)
    }

    @Test
    fun `a song's chips leave out the eight-note scales`() =
        assertTrue(groups("[C]One [F]two [G]three").single().scales.none { it.intervals.size == 8 })

    @Test
    fun `a song's chips keep a rarity only as the home scale`() {
        val vamp = groups("{key: C}\n{start_of_verse}\n[Cmaj7]x [Cmaj7]x [Cmaj7]x [Cmaj7]x\n{end_of_verse}").single()
        assertEquals(listOf("major", "major_pentatonic", "major_hexatonic"), vamp.scales.map { it.id })
        val phrygian = groups("{start_of_verse}\n[Em]x [F]x [Em]x [F]x [Em]x [F]x [Em]x [F]x\n{end_of_verse}").single().scales
        assertEquals("phrygian", phrygian.first().id)
        assertTrue(phrygian.drop(1).all { it in Scales.common })
    }

    @Test
    fun `a one-chord vamp keeps its chord's two textbook scales`() {
        fun vamp(chord: String) = groups("{start_of_verse}\n[$chord]x [$chord]x [$chord]x [$chord]x\n{end_of_verse}").single().scales.map { it.id }
        assertEquals(listOf("locrian", "locrian_natural_2", "blues"), vamp("Bm7b5"))
        assertEquals(listOf("lydian_augmented", "ionian_sharp_5"), vamp("Cmaj7#5"))
    }

    @Test
    fun `a chord worth its own scale is named in the reader's notation, with its scales capped`() {
        val german = songScaleGroupsOf(ChordProParser.parse("{start_of_verse}\n[C]One [Am]two [Bb]three [F]four [G]five\n{end_of_verse}"), labels, ChordNotation.GERMAN)
        assertEquals(listOf("B"), german.single().exceptions.map { it.chordNames.single() })
        val crowded = groups("[C]x [A7]x [Dm]x [G]x [C]x [E7]x [Am]x [G]x [C]x [A7]x [Dm]x [G]x [C]x [E7]x [Am]x [G]x")
        assertEquals(listOf("A7", "E7"), crowded.single().exceptions.map { it.chordNames.single() })
        assertTrue(crowded.single().exceptions.all { it.scales.size <= MAX_EXCEPTION_SCALES })
    }

    @Test
    fun `an exception offers the common scales where one holds the chord`() {
        val exception = groups("[C]One [F]two [G]three [E]four").single().exceptions.single()
        assertTrue(exception.scales.isNotEmpty() && exception.scales.all { it in Scales.common })
    }

    @Test
    fun `a recall is named as the page heads it`() {
        fun labelsOf(recall: String) = songScaleGroupsOf(
            ChordProTransposer.transpose(ChordProParser.parse("{key: C}\n$verse$chorus$elsewhere{transpose: 2}\n$recall"), 0),
            labels,
            ChordNotation.STANDARD,
        ).map { it.label }
        assertEquals(listOf("Verse, Chorus", "Solo", "Outro", "Final chorus"), labelsOf("{chorus: Final chorus}"))
        assertEquals(listOf("Verse, Chorus (1)", "Solo", "Outro", "Chorus (2)"), labelsOf("{chorus}"))
    }

    @Test
    fun `verses split between groups are told apart without numbering`() {
        val lastVerse = "{start_of_verse}\n[D]One [G]two [A]three\n{end_of_verse}\n"
        assertEquals(listOf("Verse (1, 2)", "Verse (3)", "Solo", "Outro"), groups("{key: C}\n" + verse + verse + lastVerse + elsewhere, unnumbered).map { it.label })
    }

    @Test
    fun `a repeated label the numbering does not reach is numbered by occurrence`() {
        val refrain = "{start_of_verse: Refrain}\n[C]One [F]two [G]three\n{end_of_verse}\n"
        val modulatedRefrain = "{start_of_verse: Refrain}\n[D]One [G]two [A]three\n{end_of_verse}\n"
        assertEquals(listOf("Refrain (1, 2)", "Refrain (3)", "Solo", "Outro"), groups("{key: C}\n" + refrain + refrain + modulatedRefrain + elsewhere).map { it.label })
    }

    @Test
    fun `a kind is said once only where the group holds all of it`() {
        val outside = "{start_of_verse}\n[C]One [F#]two [Bb]three [E]four [Ab]five [D]six\n{end_of_verse}\n"
        val groups = groups("{key: C}\n" + verse + verse + outside + modulated + elsewhere, unnumbered)
        assertEquals("Verse (1, 2)", groups.first().label)
    }

    @Test
    fun `a chorus recalled a step up is told from the chorus`() {
        val song = ChordProParser.parse("{key: C}\n$verse$chorus{chorus}\n$elsewhere{transpose: 2}\n{chorus}")
        val groups = songScaleGroupsOf(ChordProTransposer.transpose(song, 0), labels, ChordNotation.STANDARD)
        assertEquals(listOf("Verse, Chorus (1, 2)", "Solo", "Outro", "Chorus (3)"), groups.map { it.label })
    }

    @Test
    fun `several headless groups are named by their first words`() {
        val groups = groups("[C]Here comes the opening line of a long song [F]to [G]sing\n\n[D]Then [G]a [A]modulation\n\n$elsewhere")
        assertEquals(listOf(null, null, "Solo", "Outro"), groups.map { it.label })
        assertEquals(listOf("Here comes the opening line of a…", "Then a modulation", null, null), groups.map { it.opening })
    }

    @Test
    fun `a group with named and unnamed parts says so`() {
        val unnamed = "[C]One [F]two [G]three\n\n"
        val chorusFirst = groups("{key: C}\n" + chorus + unnamed + modulated + elsewhere).first()
        assertEquals("Chorus" to (true to false), chorusFirst.label to (chorusFirst.hasUnnamed to chorusFirst.joinsSeveral))
        val withVerse = groups("{key: C}\n" + verse + chorus + unnamed + modulated + elsewhere).first()
        assertEquals("Verse, Chorus" to (true to true), withVerse.label to (withVerse.hasUnnamed to withVerse.joinsSeveral))
    }

    @Test
    fun `a part no scale holds is named, and so is the rest of the song`() {
        val bridge = "{start_of_bridge}\n[C]x [F#]x [Bb]x [E]x [Ab]x [D]x\n{end_of_bridge}\n"
        val groups = groups("{key: C}\n" + verse + chorus + bridge + chorus)
        assertEquals(listOf("Verse, Chorus", "Bridge"), groups.map { it.label })
        assertTrue(groups.last().scales.isEmpty() && groups.last().exceptions.isEmpty())
        assertTrue(groups.none { it.isHome })
        assertTrue(groups.first().scales.isNotEmpty())
    }

    @Test
    fun `a key past the moment budget is named with no scales`() {
        val groups = groups(
            "{start_of_verse}\n[C]x [F]x [G]x [C]x [C]x [F]x [G]x [C]x [C]x [F]x [G]x [C]x [C]x [F]x [G]x [C]x\n{end_of_verse}\n" +
                "{start_of_bridge}\n[D]x [G]x [A]x [D]x\n{end_of_bridge}\n" +
                "{start_of_solo}\n[E]x [A]x [B]x [E]x\n{end_of_solo}\n" +
                "{start_of_verse: Middle}\n[Bb]x [Eb]x [F]x [Bb]x\n{end_of_verse}\n" +
                "{start_of_outro}\n[Ab]x [Db]x [Eb]x [Ab]x\n{end_of_outro}\n",
        )
        assertEquals(listOf("Verse", "Bridge", "Solo", "Middle", "Outro"), groups.map { it.label })
        assertTrue(groups.last().scales.isEmpty())
        assertTrue(groups.dropLast(1).all { it.scales.isNotEmpty() })
    }

    @Test
    fun `a missing part without a heading is told by its first words`() {
        val groups = groups("{key: C}\n" + chorus + "[C]One [F]two [G]three\n\n[C]Far [F#]out [Bb]and [E]away [Ab]we [D]go\n\n" + chorus)
        assertNull(groups.last().label)
        assertEquals("Far out and away we go", groups.last().opening)
        assertTrue(groups.first().hasUnnamed)
    }

    @Test
    fun `the scales section stands after the chords, else after the metadata, else first`() {
        val group = SongScaleGroup(label = null, rootName = "C", scales = emptyList(), exceptions = emptyList(), joinsSeveral = false, hasUnnamed = false, opening = null, isHome = false)
        val scales = RenderSection.Scales(listOf(group), isFolded = false)
        val sections = emptyList<RenderSection>()
        assertEquals(listOf(scales), withScalesSection(sections, listOf(group), isFolded = false))
        assertEquals(sections, withScalesSection(sections, emptyList(), isFolded = false))
        val chords = RenderSection.Chords(cells = emptyList(), isFolded = false)
        assertEquals(listOf(chords, RenderSection.Scales(listOf(group), isFolded = true)), withScalesSection(listOf(chords), listOf(group), isFolded = true))
        val metadata = RenderSection.Metadata(ChordProMetadata())
        val comment = RenderSection.Comment(text = "Slowly", style = CommentStyle.ITALIC)
        assertEquals(listOf(metadata, scales, comment), withScalesSection(listOf(metadata, comment), listOf(group), isFolded = false))
        assertEquals(listOf(scales, comment), withScalesSection(listOf(comment), listOf(group), isFolded = false))
    }
}
