/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songLayout

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.CommentPlacement
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.chordpro.model.SectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SectionGroupsTest {

    private fun section(text: String, isContinuation: Boolean = false) = ChordProBlock.Section(
        type = SectionType.Verse,
        label = null,
        lines = listOf(ChordProLine.Lyrics(text = text, chords = emptyList())),
        isContinuation = isContinuation,
    )

    private fun comment(text: String, placement: CommentPlacement) = ChordProBlock.Comment(
        text = text,
        style = CommentStyle.PLAIN,
        placement = placement,
    )

    @Test
    fun `a section is grouped with its opening comments, its cuts, its continuations and its ending comment`() {
        val blocks = listOf(
            comment("Softly", CommentPlacement.START_OF_SECTION),
            section("One"),
            comment("Louder", CommentPlacement.IN_SECTION),
            section("Two", isContinuation = true),
            ChordProBlock.Break,
            section("Three", isContinuation = true),
            ChordProBlock.Transpose(semitones = 2, key = "E"),
            section("Four", isContinuation = true),
            comment("Fade out", CommentPlacement.IN_SECTION),
        )

        assertEquals(listOf(blocks), blocks.joinCutSections())
    }

    @Test
    fun `a comment between two sections is a group of its own`() {
        val blocks = listOf(section("One"), comment("Then", CommentPlacement.BETWEEN_SECTIONS), section("Two"))

        assertEquals(blocks.map(::listOf), blocks.joinCutSections())
    }

    @Test
    fun `a chorus recall is never joined`() {
        val blocks = listOf(section("One"), ChordProBlock.ChorusRecall(label = null), section("Two", isContinuation = true))

        assertEquals(blocks.map(::listOf), blocks.joinCutSections())
    }

    @Test
    fun `a timing change before a continuation is joined only where timings are joined`() {
        val blocks = listOf(section("One"), ChordProBlock.Timing(tempo = "120", time = null), section("Two", isContinuation = true))

        assertEquals(listOf(blocks), blocks.joinCutSections(joinsTimings = true))
        assertEquals(blocks.map(::listOf), blocks.joinCutSections(joinsTimings = false))
    }

    @Test
    fun `the first section of a group led by a comment is the section`() {
        val verse = section("One")
        val group = listOf(comment("Softly", CommentPlacement.START_OF_SECTION), verse)

        assertSame(verse, group.firstSection())
    }
}
