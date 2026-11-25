/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.SectionPart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class DockStripScrollTest {

    private val names = setOf("Am", "C", "D", "F", "E")
    private val sections = listOf(
        verse("Am", "C", "D"),
        RenderSection.Comment(text = "Instrumental", style = CommentStyle.PLAIN),
        verse("F", "Am"),
        verse("E"),
    )

    @Test
    fun `a page starting with a section is headed by the first chord the section plays`() {
        assertEquals("Am", firstChordFrom(sections, 0, names))
        assertEquals("F", firstChordFrom(sections, 2, names))
        assertEquals("E", firstChordFrom(sections, 3, names))
    }

    @Test
    fun `a section of no chords is headed by the next chord played`() = assertEquals("F", firstChordFrom(sections, 1, names))

    @Test
    fun `past the last chord the page is headed by the last one played`() = assertEquals("E", firstChordFrom(sections, 4, names))

    @Test
    fun `annotations and chords the strip does not draw are passed over`() {
        val annotated = listOf(verse("N.C.", "D"))
        assertEquals("D", firstChordFrom(annotated, 0, names))
    }

    @Test
    fun `a song playing none of the strip's chords heads no page`() = assertNull(firstChordFrom(listOf(verse("G")), 0, names))

    private fun verse(vararg chords: String) = RenderSection.Lines(
        header = "Verse",
        foldKey = "verse",
        parts = listOf(
            SectionPart.Lines(
                lines = listOf(
                    ChordProLine.Lyrics(
                        text = "words ".repeat(chords.size),
                        chords = chords.mapIndexed { index, name -> ChordProLine.Lyrics.Chord(position = index * 6, name = name, isAnnotation = false) },
                    ),
                ),
            ),
        ),
        isOnCard = false,
    )
}
