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
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    private fun groups(text: String) = songScaleGroupsOf(ChordProParser.parse(text.trimIndent()), labels)

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
    fun `a group offers at most a handful of scales`() =
        assertEquals(MAX_GROUP_SCALES, groups("[C]One [G]two").single().scales.size)

    @Test
    fun `the scales section stands after the chords, else after the metadata, else first`() {
        val group = SongScaleGroup(label = null, rootName = "C", scales = emptyList())
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
