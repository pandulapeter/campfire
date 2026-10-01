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
    private fun lyrics(count: Int, prefix: String = "Line") = (1..count).map { ChordProLine.Lyrics("$prefix $it", emptyList()) }
    private fun PrintDocument.placeOf(text: String) = pages.withIndex().firstNotNullOf { (index, page) ->
        page.texts.firstOrNull { it.text == text }?.let { index to it.x }
    }

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
        val verse = ChordProBlock.Section(SectionType.Verse, "Verse", listOf(ChordProLine.Lyrics("Sung words", emptyList())))
        val entry = song(emptyList(), listOf(ChordProBlock.Section(SectionType.Custom("instrumental"), "Instrumental", instrumentalLines), verse))
        val settings = PrintSettings(showChords = false)
        val document = layout(source(entry), settings)
        assertEquals(layout(source(song(emptyList(), listOf(verse))), settings), document)
        assertEquals(1, document.pages.size)
        assertFalse(document.pages.flatMap { it.texts }.any { it.text == "Instrumental" })
        assertTrue(layout(source(entry)).pages.size > 1)
    }

    @Test fun aTabOnlySectionLeavesOutItsLabelWhenChordsAreHidden() {
        val riff = ChordProBlock.Section(SectionType.Custom("riff"), "Riff", listOf(ChordProLine.Tab("e|--0--2--|"), ChordProLine.Tab("B|--1--3--|", continuesEnvironment = true)))
        val texts = layout(source(song(emptyList(), listOf(riff))), PrintSettings(showChords = false)).pages.flatMap { it.texts }.map { it.text }
        assertFalse("Riff" in texts)
        assertTrue("Riff" in layout(source(song(emptyList(), listOf(riff)))).pages.flatMap { it.texts }.map { it.text })
    }

    @Test fun aSectionWrittenWithoutLinesKeepsItsLabel() {
        val cue = ChordProBlock.Section(SectionType.Bridge, "Bridge", emptyList())
        assertTrue("Bridge" in layout(source(song(emptyList(), listOf(cue)))).pages.flatMap { it.texts }.map { it.text })
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
        assertEquals(layout(source(song(emptyList(), emptyList())), settings.copy(showComments = false)),
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

    @Test fun aHeadingStaysInTheColumnOfItsFirstSection() {
        val settings = PrintSettings(columns = 2, startSongsOnNewPage = false)
        (1..90).forEach { count ->
            val first = song(lyrics(count, "First")).copy(title = "First")
            val second = song(lyrics(10, "Second")).copy(title = "Second")
            val document = layout(PrintSource("Two songs", songs = listOf(first, second)), settings)
            assertEquals(document.placeOf("Second"), document.placeOf("Second 1"), "after $count lines")
        }
    }

    @Test fun aBreakAtTheStartOfASongDoesNotStrandItsHeading() {
        val entry = song(emptyList(), listOf(ChordProBlock.Break, ChordProBlock.Section(SectionType.Verse, "Verse", lyrics(3))))
        val document = layout(source(entry), PrintSettings(columns = 2))
        assertEquals(document.placeOf("A song"), document.placeOf("Line 1"))
    }

    @Test fun aSectionTooTallToShareAColumnWithItsHeadingStartsUnderIt() {
        val settings = PrintSettings(columns = 2)
        val margin = settings.marginMm * 72f / 25.4f
        val capacity = settings.paper.height - 2 * margin - 18f
        // The section is one row shorter than a column: its label and all but two of the rows that would fill one.
        val document = layout(source(song(lyrics((capacity / (settings.fontSize * 1.45f)).toInt() - 2))), settings)
        assertEquals(document.placeOf("A song"), document.placeOf("Line 1"))
    }

    @Test fun aMissingSongKeepsItsHeadingWithItsNotice() {
        val settings = PrintSettings(columns = 2, startSongsOnNewPage = false)
        (1..90).forEach { count ->
            val first = song(lyrics(count, "First")).copy(title = "First")
            val missing = first.copy(title = "Gone", song = null)
            val document = layout(PrintSource("Two songs", songs = listOf(first, missing)), settings)
            assertEquals(document.placeOf("Gone"), document.placeOf("Missing"), "after $count lines")
        }
    }

    @Test fun thePageNumberIsCentredInsideTheBandReservedForIt() {
        listOf(10, 25).forEach { marginMm ->
            val settings = PrintSettings(marginMm = marginMm)
            val document = layout(source(song(lyrics(200))), settings)
            val margin = marginMm * 72f / 25.4f
            val bottom = document.height - margin - 18f
            assertTrue(document.pages.size > 1)
            document.pages.forEachIndexed { index, page ->
                val number = page.texts.single { it.text == "${index + 1} / ${document.pages.size}" }
                assertTrue(number.y + 11 <= document.height - margin && number.y >= bottom, number.toString())
                assertEquals(document.width / 2, number.x + measure(number.text, number.size, false) / 2, 0.01f)
                (page.texts - number).forEach { assertTrue(it.y + it.size * 1.45f <= bottom + 0.01f, it.toString()) }
            }
        }
    }

    @Test fun aChordOnlyLineTakesOneRow() {
        val entry = song(listOf(
            ChordProLine.Lyrics("", listOf(ChordProLine.Lyrics.Chord(0, "G", false), ChordProLine.Lyrics.Chord(0, "C", false))),
            ChordProLine.Lyrics("Sung words", emptyList()),
        ))
        val texts = layout(source(entry)).pages.single().texts
        assertEquals(texts.first { it.text == "G" }.y + 12 * 1.45f, texts.first { it.text == "Sung words" }.y, 0.01f)
        assertFalse(texts.any { it.text.isBlank() || it.text.all { char -> char == '\u00A0' || char == ' ' } })
    }

    @Test fun anAnnotationWiderThanTheColumnNeitherOpensWithAnEmptyRowNorPushesTheLyricsAway() {
        val settings = PrintSettings(columns = 2)
        val annotation = "Slowly, with the whole room singing along, " .repeat(3).trim()
        val entry = song(listOf(ChordProLine.Lyrics("Lyrics here follow", listOf(
            ChordProLine.Lyrics.Chord(0, annotation, true),
            ChordProLine.Lyrics.Chord(12, "G", false),
        ))))
        val document = layout(source(entry), settings)
        val texts = document.pages.single().texts
        val margin = settings.marginMm * 72f / 25.4f
        val columnWidth = (document.width - 2 * margin - 18) / 2
        val label = texts.first { it.text == "Verse" }
        val annotationRows = texts.filter { annotation.contains(it.text.trim()) && it.bold && it.text.isNotBlank() && it.text != "Verse" }
        assertEquals(label.y + 12 * 1.45f, annotationRows.minOf { it.y }, 0.01f)
        val lyrics = texts.first { it.text.startsWith("Lyrics here") }
        val follow = texts.first { it.text.contains("follow") }
        assertTrue(follow.y <= lyrics.y + 12 * 2.75f + 0.01f, "$lyrics $follow")
        texts.filter { it.size != 9 }.forEach { assertTrue(it.x + measure(it.text, it.size, it.bold) <= margin + columnWidth + 0.01f, it.toString()) }
    }
}
