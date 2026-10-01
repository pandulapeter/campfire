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

    private val labels = DefaultSectionLabels(
        verse = "Versszak",
        chorus = "Refrén",
        bridge = "Átkötés",
        tab = "Tab",
        grid = "Rács",
        intro = "Bevezető",
        preChorus = "Pre-refrén",
        solo = "Szóló",
        outro = "Levezetés",
    )

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
            listOf("comment before", "Verse: opens, lines, cuts, lines, ends", "comment after", "Refrén: lines"),
            shape("{c: before}\n{sov: Verse}\n{c: opens}\nla\n{c: cuts}\nla\n{c: ends}\n{eov}\n{c: after}\n{soc}\nla\n{eoc}"),
        )
    }

    @Test
    fun `a recalled chorus repeats the comments it opens and ends with`() {
        assertEquals(
            listOf("Refrén: softly, lines, x2", "Refrén: softly, lines, x2"),
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

    @Test
    fun `a section the file leaves unnamed is still headed, a paragraph by its fold toggle alone`() {
        assertEquals(
            listOf("Versszak: lines", ": lines", "Tab: lines", "Refrén: lines"),
            shape("{sov}\nla\n{eov}\n\nloose\n\n{sot}\ne|-0-|\n{eot}\n\n{soc}\nla\n{eoc}"),
        )
    }

    @Test
    fun `the kinds of section the editor writes are headed in the app's language, and any other kind as the file names it`() {
        assertEquals(
            listOf("Bevezető: lines", "Pre-refrén: lines", "Szóló: lines", "Levezetés: lines", "Interlude: lines", "Szóló 2: lines"),
            shape("{start_of_intro}\nla\n{end_of_intro}\n{start_of_pre-chorus}\nla\n{end_of_pre-chorus}\n{start_of_solo}\nla\n{end_of_solo}\n" +
                "{start_of_outro}\nla\n{end_of_outro}\n{start_of_interlude}\nla\n{end_of_interlude}\n{start_of_solo: Szóló 2}\nla\n{end_of_solo}"),
        )
    }
}
