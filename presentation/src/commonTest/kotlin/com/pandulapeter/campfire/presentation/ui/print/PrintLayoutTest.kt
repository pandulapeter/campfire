/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

import com.pandulapeter.campfire.chordpro.model.*
import com.pandulapeter.campfire.data.model.domain.PrintSettings
import kotlin.test.*

internal class PrintLayoutTest {
    private val labels = PrintLabels("Key", "Capo", "Tempo", "Time", "Missing")
    private fun measure(text: String, size: Int, bold: Boolean) = text.count { it != '\u200B' } * size * 0.6f
    private fun layout(source: PrintSource, settings: PrintSettings = PrintSettings()) = layoutPrintDocument(source, settings, labels, ::measure)
    private fun song(lines: List<ChordProLine>, blocks: List<ChordProBlock>? = null) = PrintSong("song.cho", "A song", "Artist", song = ChordProSong(
        ChordProMetadata(key = "D", capo = 2), blocks ?: listOf(ChordProBlock.Section(SectionType.Verse, "Verse", lines))))
    private fun source(song: PrintSong) = PrintSource("A song", songs = listOf(song))

    @Test fun longSongsKeepEveryLineAndChordWithinPrintableColumns() {
        val lines = (1..180).map { ChordProLine.Lyrics("Line $it " + "words ".repeat(14), listOf(ChordProLine.Lyrics.Chord(0, "D", false))) }
        val settings = PrintSettings(columns = 2, fontSize = 20, marginMm = 25)
        val document = layout(source(song(lines)), settings)
        assertTrue(document.pages.size > 1)
        val texts = document.pages.flatMap { it.texts }
        assertEquals(180, texts.count { it.text == "D" })
        val margin = settings.marginMm * 72f / 25.4f
        val columnWidth = (document.width - 2 * margin - 18) / 2
        texts.forEach { item ->
            assertTrue(item.x >= margin)
            assertTrue(item.x + measure(item.text, item.size, item.bold) <= document.width - margin + 0.01f, item.toString())
            assertTrue(item.y < document.height - margin + 0.01f)
            if (item.text != "D" && item.size != 9) assertTrue(measure(item.text, item.size, item.bold) <= columnWidth)
        }
        document.pages.forEach { page ->
            page.texts.filter { it.text == "D" }.forEach { chord ->
                assertTrue(page.texts.any { it.x == chord.x && it.y > chord.y && it.y < chord.y + 30 && !it.bold })
            }
        }
    }

    @Test fun adjacentChordsPadTheLyricsSoTheyRemainOverTheirOwnSyllables() {
        val line = ChordProLine.Lyrics("ab", listOf(ChordProLine.Lyrics.Chord(0, "Cmaj7", false), ChordProLine.Lyrics.Chord(1, "D", false)))
        val texts = layout(source(song(listOf(line)))).pages.single().texts
        val lyrics = texts.first { it.text.startsWith("a") }
        val chord = texts.first { it.text == "D" }
        assertEquals(lyrics.x + measure(lyrics.text.substringBefore('b'), lyrics.size, false), chord.x, 0.01f)
        assertTrue(chord.y < lyrics.y)
    }

    @Test fun lyricsOnlyOmitsChordsTabsGridsAndCommentsWhenRequested() {
        val entry = song(emptyList(), listOf(
            ChordProBlock.Comment("A comment", CommentStyle.PLAIN),
            ChordProBlock.Section(SectionType.Paragraph, null, listOf(
                ChordProLine.Lyrics("Sung words", listOf(ChordProLine.Lyrics.Chord(0, "D", false))),
                ChordProLine.Tab("e|--0--2--|"), ChordProLine.Grid(listOf(GridToken.Chord("D"))),
            )),
        ))
        val text = layout(source(entry), PrintSettings(showChords = false, showComments = false, showMetadata = false)).pages.flatMap { it.texts }.map { it.text }
        assertTrue("Sung words" in text)
        assertFalse(text.any { it.contains("comment") || it.contains("Capo") || it.contains("Artist") || it == "D" || it.contains("e|") })
    }

    @Test fun hiddenInstrumentalChordLinesDoNotReserveSpace() {
        val instrumentalLines = (1..100).map {
            ChordProLine.Lyrics(if (it % 2 == 0) "    " else "", listOf(ChordProLine.Lyrics.Chord(0, "D", false)))
        }
        fun entry(lines: List<ChordProLine>) = song(emptyList(), listOf(
            ChordProBlock.Section(SectionType.Custom("instrumental"), "Instrumental", lines),
            ChordProBlock.Section(SectionType.Verse, "Verse", listOf(ChordProLine.Lyrics("Sung words", emptyList()))),
        ))
        val settings = PrintSettings(showChords = false)
        val document = layout(source(entry(instrumentalLines)), settings)
        assertEquals(layout(source(entry(emptyList())), settings), document)
        assertEquals(1, document.pages.size)
        assertTrue(layout(source(entry(instrumentalLines))).pages.size > 1)
    }

    @Test fun chordOnlyLinesKeepVisibleAnnotationsWhenChordsAreHidden() {
        val entry = song(listOf(ChordProLine.Lyrics("", listOf(
            ChordProLine.Lyrics.Chord(0, "D", false),
            ChordProLine.Lyrics.Chord(0, "Solo", true),
        ))))
        val settings = PrintSettings(showChords = false)
        val texts = layout(source(entry), settings).pages.single().texts
        assertTrue(texts.any { it.text == "Solo" })
        assertFalse(texts.any { it.text == "D" })
        assertEquals(layout(source(song(emptyList())), settings.copy(showComments = false)),
            layout(source(entry), settings.copy(showComments = false)))
    }

    @Test fun setlistRunningOrderPreservesSelectedPositionsKeysAndMissingSongs() {
        val first = song(listOf(ChordProLine.Lyrics("First lyrics", emptyList()))).copy(title = "First", index = 1)
        val missing = first.copy(fileName = "missing.cho", title = "Missing song", index = 3, song = null)
        val last = first.copy(title = "Last", index = 5)
        val source = PrintSource("Concert", isSetlist = true, songs = listOf(first, missing, last))
        val text = layout(source, PrintSettings(setlistMode = PrintSettings.SetlistMode.RUNNING_ORDER)).pages.flatMap { it.texts }.map { it.text }
        assertEquals(listOf("1. First (Key: D)", "3. Missing song [Missing]", "5. Last (Key: D)"), text.filter { it.firstOrNull()?.isDigit() == true && it.length > 8 })
        assertFalse("First lyrics" in text)
    }

    @Test fun setlistSongsStartOnNewPagesAndKeepOriginalOrder() {
        val first = song(listOf(ChordProLine.Lyrics("First lyrics", emptyList()))).copy(title = "First", index = 1)
        val last = first.copy(title = "Last", index = 2)
        val document = layout(PrintSource("Concert", isSetlist = true, songs = listOf(first, last)))
        assertEquals(3, document.pages.size)
        assertEquals(listOf("Concert", "1. First", "2. Last"), document.pages.map { it.texts.first().text })
    }

    @Test fun landscapeLetterUsesPhysicalPaperSizeAndEmptySelectionProducesNoPages() {
        val settings = PrintSettings(paper = PrintSettings.Paper.LETTER, isLandscape = true)
        val document = layout(PrintSource("Empty", songs = emptyList()), settings)
        assertEquals(792f, document.width)
        assertEquals(612f, document.height)
        assertTrue(document.pages.isEmpty())
    }

    @Test fun wrappedTextPreservesSpacesAndSurrogatePairs() {
        val input = "Long words with  spaces and 🎸 " + "x".repeat(80)
        val parts = wrapPrintText(input, 12f) { it.length.toFloat() }
        assertEquals(input, parts.joinToString(""))
        assertTrue(parts.all { it.length <= 12 && !it.last().isHighSurrogate() && !it.first().isLowSurrogate() })
    }

    @Test fun tablatureWrapsAllStringsAtTheSameColumns() {
        val lines = listOf("e", "B", "G", "D", "A", "E").mapIndexed { i, prefix ->
            ChordProLine.Tab("$prefix|" + "--$i--|".repeat(30), continuesEnvironment = i > 0)
        }
        val document = layout(source(song(lines)), PrintSettings(columns = 2, fontSize = 16))
        val prefixes = document.pages.flatMap { it.texts }.filter { it.text.contains('|') }.map { it.text.substringBefore('|') }
        assertTrue(prefixes.size > 6)
        prefixes.chunked(6).forEach { assertEquals(listOf("e", "B", "G", "D", "A", "E"), it) }
    }
}
