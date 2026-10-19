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

    @Test
    fun `a song one scale fits is one group named nothing`() {
        val groups = groups("{key: C}\n" + verse + chorus + verse)
        assertEquals(1, groups.size)
        assertNull(groups.single().label)
    }

    @Test
    fun `a kind held whole by a group is named once, and the rest by their own headings`() {
        val groups = groups("{key: C}\n" + verse + chorus + modulated + verse)
        assertEquals(listOf("Verse, Chorus", "Bridge"), groups.map { it.label })
        assertEquals(listOf("C", "D"), groups.map { it.rootName })
    }

    @Test
    fun `verses split between groups are named by their numbers`() {
        val lastVerse = "{start_of_verse}\n[D]One [G]two [A]three\n{end_of_verse}\n"
        val groups = groups("{key: C}\n" + verse + verse + lastVerse)
        assertEquals(listOf("Verse 1, Verse 2", "Verse 3"), groups.map { it.label })
    }

    @Test
    fun `a group names its sections in the order the song plays them, a kind once at its first`() {
        val groups = groups("{key: C}\n" + chorus + verse + modulated + verse)
        assertEquals(listOf("Chorus, Verse", "Bridge"), groups.map { it.label })
    }

    @Test
    fun `a lone heading keeps its number, and a number without a space before it is the heading's own`() {
        val take = "{start_of_verse: Take 5}\n[C]One [F]two\n{end_of_verse}\n"
        val twice = "{start_of_chorus: Chorus x2}\n[C]One [F]two\n{end_of_chorus}\n"
        assertEquals(listOf("Take 5, Chorus x2", "Bridge"), groups("{key: C}\n" + take + twice + modulated).map { it.label })
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
    fun `an exception is named in the reader's notation, capped`() {
        val german = songScaleGroupsOf(ChordProParser.parse("{start_of_verse}\n[C]One [Bb]two [F]three [G]four\n{end_of_verse}"), labels, ChordNotation.GERMAN)
        assertEquals(listOf("B"), german.single().exceptions.map { it.chordName })
        val crowded = groups("[C]One [C]two [C]three [C]four [C]five [C]six [C]seven [F]x [G]x [F]x [G]x [A7]x [E7]x [D7]x [Bb]x [Ab]x")
        assertEquals(MAX_GROUP_EXCEPTIONS, crowded.single().exceptions.size)
        assertTrue(crowded.single().exceptions.all { it.scales.size <= MAX_EXCEPTION_SCALES })
    }

    @Test
    fun `a recall is named as the page heads it`() {
        fun labelsOf(recall: String) = songScaleGroupsOf(
            ChordProTransposer.transpose(ChordProParser.parse("{key: C}\n$verse$chorus{transpose: 2}\n$recall"), 0),
            labels,
            ChordNotation.STANDARD,
        ).map { it.label }
        assertEquals(listOf("Verse, Chorus", "Final chorus"), labelsOf("{chorus: Final chorus}"))
        assertEquals(listOf("Verse, Chorus (1)", "Chorus (2)"), labelsOf("{chorus}"))
    }

    @Test
    fun `verses split between groups are told apart without numbering`() {
        val lastVerse = "{start_of_verse}\n[D]One [G]two [A]three\n{end_of_verse}\n"
        assertEquals(listOf("Verse (1, 2)", "Verse (3)"), groups("{key: C}\n" + verse + verse + lastVerse, unnumbered).map { it.label })
    }

    @Test
    fun `a repeated label the numbering does not reach is numbered by occurrence`() {
        val refrain = "{start_of_verse: Refrain}\n[C]One [F]two [G]three\n{end_of_verse}\n"
        val modulatedRefrain = "{start_of_verse: Refrain}\n[D]One [G]two [A]three\n{end_of_verse}\n"
        assertEquals(listOf("Refrain (1, 2)", "Refrain (3)"), groups("{key: C}\n" + refrain + refrain + modulatedRefrain).map { it.label })
    }

    @Test
    fun `a kind is said once only where the group holds all of it`() {
        val outside = "{start_of_verse}\n[G]One [B]two [C]three [Cm]four\n{end_of_verse}\n"
        val groups = groups("{key: C}\n" + verse + verse + outside + modulated, unnumbered)
        assertEquals("Verse (1, 2)", groups.first().label)
    }

    @Test
    fun `a chorus recalled a step up is told from the chorus`() {
        val song = ChordProParser.parse("{key: C}\n$verse$chorus{chorus}\n{transpose: 2}\n{chorus}")
        val groups = songScaleGroupsOf(ChordProTransposer.transpose(song, 0), labels, ChordNotation.STANDARD)
        assertEquals(listOf("Verse, Chorus (1, 2)", "Chorus (3)"), groups.map { it.label })
    }

    @Test
    fun `several headless groups are named by their first words`() {
        val groups = groups("[C]Here comes the opening line of a long song [F]to [G]sing\n\n[D]Then [G]a [A]modulation")
        assertEquals(listOf(null, null), groups.map { it.label })
        assertEquals(listOf("Here comes the opening line of a…", "Then a modulation"), groups.map { it.opening })
    }

    @Test
    fun `a group with named and unnamed parts says so`() {
        val unnamed = "[C]One [F]two [G]three\n\n"
        val chorusFirst = groups("{key: C}\n" + chorus + unnamed + modulated).first()
        assertEquals("Chorus" to (true to false), chorusFirst.label to (chorusFirst.hasUnnamed to chorusFirst.joinsSeveral))
        val withVerse = groups("{key: C}\n" + verse + chorus + unnamed + modulated).first()
        assertEquals("Verse, Chorus" to (true to true), withVerse.label to (withVerse.hasUnnamed to withVerse.joinsSeveral))
    }

    @Test
    fun `the scales section stands after the chords, else after the metadata, else first`() {
        val group = SongScaleGroup(label = null, rootName = "C", scales = emptyList(), exceptions = emptyList(), joinsSeveral = false, hasUnnamed = false, opening = null)
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
