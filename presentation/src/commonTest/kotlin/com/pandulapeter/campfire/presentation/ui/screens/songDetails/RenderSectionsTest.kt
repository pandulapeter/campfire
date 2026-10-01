/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.chordpro.ChordProParser
import kotlin.test.Test
import kotlin.test.assertEquals

class RenderSectionsTest {

    private val labels = DefaultSectionLabels(chorus = "Chorus", bridge = "Bridge", tab = "Tab", grid = "Grid")

    /** Each section as the comments it holds, or as its own text where it is a comment between sections. */
    private fun shape(text: String, shouldShowChords: Boolean = true) = prepareSongLyrics(
        song = ChordProParser.parse(text),
        shouldShowChords = shouldShowChords,
        labels = labels,
    ).sections.map { section ->
        when (section) {
            is RenderSection.Comment -> "comment ${section.text}"
            is RenderSection.Lines -> "${section.header}: " + section.parts.joinToString { (it as? RenderSection.Comment)?.text ?: "lines" }
            is RenderSection.Metadata -> "metadata"
        }
    }

    @Test
    fun `the comments written in a section fold with it and the ones between sections stand alone`() {
        assertEquals(
            listOf("comment before", "Verse: opens, lines, cuts, lines, ends", "comment after", "Chorus: lines"),
            shape("{c: before}\n{sov: Verse}\n{c: opens}\nla\n{c: cuts}\nla\n{c: ends}\n{eov}\n{c: after}\n{soc}\nla\n{eoc}"),
        )
    }

    @Test
    fun `a recalled chorus repeats the comments it opens and ends with`() {
        assertEquals(
            listOf("Chorus: softly, lines, x2", "Chorus: softly, lines, x2"),
            shape("{soc}\n{c: softly}\nla\n{c: x2}\n{eoc}\n\n{chorus}"),
        )
    }

    @Test
    fun `lyrics-only mode hides the comments written in a tab or a grid with it`() {
        val text = "{sov: Solo}\nla\n{sot}\n{c: in tab}\ne|-0-|\n{eot}\n{c: after tab}\nla\n{eov}\n\n" +
            "{sot: Riff}\ne|-0-|\n{c: in riff}\n{eot}\n{c: after riff}"

        assertEquals(
            listOf("Solo: lines, in tab, lines, after tab, lines", "Riff: lines, in riff", "comment after riff"),
            shape(text),
        )
        assertEquals(
            listOf("Solo: lines, after tab, lines", "comment after riff"),
            shape(text, shouldShowChords = false),
        )
    }
}
