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
import com.pandulapeter.campfire.chordpro.ChordProTransposer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    /**
     * Each section as the comments and key changes it holds, or as its own text where it is one of those between sections.
     *
     * @param isTransposed Whether the song's own `{transpose}` directives are applied, as they are on the page.
     */
    private fun shape(text: String, shouldShowChords: Boolean = true, isTransposed: Boolean = false, showsTiming: Boolean = true) = prepareSongLyrics(
        song = ChordProParser.parse(text).let { if (isTransposed) ChordProTransposer.transpose(it, 0) else it },
        shouldShowChords = shouldShowChords,
        labels = labels,
        showsTiming = showsTiming,
    ).sections.map { section ->
        when (section) {
            is RenderSection.Comment -> "comment ${section.text}"
            is RenderSection.KeyChange -> "key ${section.key}"
            is RenderSection.Timing -> "timing ${section.tempo} ${section.time}"
            is RenderSection.Lines -> "${section.header}: " + section.parts.joinToString { part ->
                when (part) {
                    is RenderSection.Comment -> part.text
                    is RenderSection.KeyChange -> "key ${part.key}"
                    is SectionPart.Lines -> "lines"
                }
            }
            is RenderSection.Metadata -> "metadata"
        }
    }

    @Test
    fun `a timing change is a line of its own between two sections or where it cuts one`() {
        assertEquals(
            listOf("Versszak: lines", "timing 90 3/4", "Refrén: lines"),
            shape("{tempo: 120}\n{time: 4/4}\n{sov}\n[G]la\n{eov}\n{tempo: 90}\n{time: 3/4}\n{soc}\n[G]la\n{eoc}"),
        )
        assertEquals(
            listOf("Versszak: lines", "timing 90 4/4", ": lines"),
            shape("{tempo: 120}\n{sov}\n[G]la\n{tempo: 90}\n[G]la\n{eov}"),
        )
    }

    @Test
    fun `a timing change inside a chorus makes two cards`() {
        val model = prepareSongLyrics(ChordProParser.parse("{tempo: 120}\n{soc}\n[G]la\n{tempo: 90}\n[G]la\n{eoc}"), shouldShowChords = true, labels = labels)
        val cards = model.sections.filterIsInstance<RenderSection.Lines>()
        assertEquals(listOf(true, true), cards.map { it.isOnCard })
        assertEquals(listOf("chorus#1", "chorus#1~1"), cards.map { it.foldKey })
    }

    @Test
    fun `with the metronome off a timing change is joined over and the folds keep their keys`() {
        val text = "{tempo: 120}\n{sov}\n[G]la\n{tempo: 90}\n[G]la\n{eov}\n{sov}\n[G]la\n{eov}"
        assertEquals(listOf("Versszak: lines", "Versszak: lines"), shape(text, showsTiming = false))
        val foldKeys = { showsTiming: Boolean ->
            prepareSongLyrics(ChordProParser.parse(text), shouldShowChords = true, labels = labels, showsTiming = showsTiming)
                .sections.filterIsInstance<RenderSection.Lines>().map { it.foldKey }
        }
        assertEquals(listOf("verse#1", "verse#2"), foldKeys(false))
        assertEquals(listOf("verse#1", "verse#1~1", "verse#2"), foldKeys(true))
    }

    @Test
    fun `a timing change with no line after it is dropped`() {
        assertEquals(listOf("Versszak: lines"), shape("{tempo: 120}\n{sov}\n[G]la\n{eov}\n{tempo: 90}"))
        assertEquals(
            listOf("Versszak: lines", "comment Note", "timing 80 4/4", "Versszak: lines"),
            shape("{tempo: 120}\n{sov}\n[G]la\n{eov}\n{tempo: 90}\n{comment_italic: Note}\n{tempo: 80}\n{sov}\n[G]la\n{eov}"),
        )
        assertEquals(
            listOf("Versszak: lines"),
            shape("{tempo: 120}\n{sov}\n[G]la\n{eov}\n{tempo: 90}\n{start_of_tab}\ne|--0--|\n{end_of_tab}", shouldShowChords = false),
        )
    }

    @Test
    fun `a key change is named where it stands, between two sections or inside one`() {
        assertEquals(
            listOf("Verse: lines, key A, lines", "key Bb", "Refrén: lines"),
            shape("{key: G}\n{sov: Verse}\n[G]la\n{transpose: 2}\n[G]la\n{eov}\n{transpose: 3}\n{soc}\n[G]la\n{eoc}", isTransposed = true),
        )
    }

    @Test
    fun `a key change before a recall names the key the chorus is repeated in`() {
        assertEquals(
            listOf("Refrén: lines", "key A", "Refrén: lines"),
            shape("{key: G}\n{soc}\n[G]la\n{eoc}\n{transpose: 2}\n{chorus}", isTransposed = true),
        )
    }

    @Test
    fun `a key change says nothing in a song that declares no key, and nothing without the chords`() {
        assertEquals(listOf("Verse: lines"), shape("{sov: Verse}\n[G]la\n{transpose: 2}\n[G]la\n{eov}", isTransposed = true))
        assertEquals(
            listOf("Verse: lines"),
            shape("{key: G}\n{sov: Verse}\n[G]la\n{transpose: 2}\n[G]la\n{eov}", shouldShowChords = false, isTransposed = true),
        )
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
    fun `a numbered chorus recall that opens with a comment is headed by the chorus it repeats`() {
        val sections = prepareSongLyrics(
            song = ChordProParser.parse("{soc}\n{c: Softly}\na\n{eoc}\n{soc}\n{c: Loud}\nb\n{eoc}\n{chorus}"),
            shouldShowChords = true,
            labels = labels.copy(shouldNumberSections = true),
        ).sections.filterIsInstance<RenderSection.Lines>()

        assertEquals("Refrén 2", sections.last().header)
    }

    @Test
    fun `a recall of a chorus labelled with the kind's bare name is numbered like it`() {
        val sections = prepareSongLyrics(
            song = ChordProParser.parse("{soc}\na\n{eoc}\n{soc: Refrén}\nb\n{eoc}\n{chorus}"),
            shouldShowChords = true,
            labels = labels.copy(shouldNumberSections = true),
        ).sections.filterIsInstance<RenderSection.Lines>()

        assertEquals("Refrén 2", sections.last().header)
    }

    @Test
    fun `a chorus recall that opens with a comment is headed and folded by the chorus label`() {
        val sections = prepareSongLyrics(
            song = ChordProParser.parse("{soc: Refrain}\n{c: x}\nla\n{eoc}\n\n{chorus}"),
            shouldShowChords = true,
            labels = labels,
        ).sections.filterIsInstance<RenderSection.Lines>()

        assertEquals(listOf("Refrain", "Refrain"), sections.map { it.header })
        assertTrue(sections.last().foldKey.startsWith("Refrain#"))
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

    /** The stretches of [text] as the song details screen lays them out, behind its metadata section. */
    private fun stretches(text: String): Pair<List<String>, TimingStretches> {
        val model = prepareSongLyrics(song = ChordProParser.parse(text), shouldShowChords = true, labels = labels)
        val sections = withMetadataSection(model.sections, model.song.metadata, shouldShowChords = true, isSongInfoShown = false)
        return sections.map { it::class.simpleName.orEmpty() } to timingStretchesOf(sections)
    }

    @Test
    fun `a change written before the first line starts no stretch and is in force from the first page`() {
        val (shape, stretches) = stretches("{tempo: 120}\n{c: Slowly}\n{tempo: 60}\n{sov}\n[G]la\n{eov}\n{tempo: 90}\n{sov}\n[G]la\n{eov}")
        assertEquals(listOf("Metadata", "Comment", "Timing", "Lines", "Timing", "Lines"), shape)
        assertEquals(listOf(0, 4), stretches.starts.toList())
        assertEquals(listOf(0, 4), stretches.timingSections)
    }

    @Test
    fun `a change at the start of the first section starts no stretch`() {
        val (shape, stretches) = stretches("{tempo: 120}\n{sov}\n{tempo: 60}\n[G]la\n{eov}")
        assertEquals(listOf("Metadata", "Timing", "Lines"), shape)
        assertEquals(listOf(0), stretches.starts.toList())
        assertEquals(listOf(0), stretches.timingSections)
    }

    @Test
    fun `a change after a line of the song starts a stretch of its own`() {
        val (_, stretches) = stretches("{tempo: 120}\n{sov}\n[G]la\n{tempo: 90}\n[G]la\n{eov}")
        assertEquals(listOf(0, 2), stretches.starts.toList())
        assertEquals(listOf(2), stretches.timingSections)
    }

    @Test
    fun `a song that changes nothing is one stretch`() {
        val (_, stretches) = stretches("{tempo: 120}\n{sov}\n[G]la\n{eov}")
        assertEquals(listOf(0), stretches.starts.toList())
        assertEquals(emptyList(), stretches.timingSections)
    }

    private fun firstLyric(text: String) = prepareSongLyrics(
        song = ChordProParser.parse(text),
        shouldShowChords = true,
        labels = labels,
    ).sections.filterIsInstance<RenderSection.Lines>().single().firstLyric

    @Test
    fun `an unnamed section is named by its first sung line without its chords`() {
        assertEquals("There is a house", firstLyric("[Am]There is a [C]house"))
    }

    @Test
    fun `a line of nothing but chords does not name a section`() {
        assertEquals("Sung", firstLyric("[Am] [C]\nSung"))
        assertNull(firstLyric("[Am] [C]\n[G]"))
    }

    @Test
    fun `a long first line is cut to forty characters and an ellipsis`() {
        assertEquals("a".repeat(40) + "…", shortenedForDescription("a".repeat(60)))
    }

    @Test
    fun `the cut never leaves half of a character behind`() {
        val shortened = shortenedForDescription("a".repeat(39) + "\uD83C\uDFB8" + "a".repeat(20))
        assertFalse(shortened.removeSuffix("…").last().isHighSurrogate())
    }
}
