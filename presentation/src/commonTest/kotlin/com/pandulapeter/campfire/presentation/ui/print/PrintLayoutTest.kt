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
import com.pandulapeter.campfire.presentation.ui.chords.ChordDiagramGeometry
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.DefaultSectionLabels
import kotlinx.coroutines.test.runTest
import kotlin.test.*

internal class PrintLayoutTest {
    private val labels = PrintLabels("Key", "Transposition", "Capo", "Tempo", "$TEMPO_VALUE BPM", "Time", "Missing", DefaultSectionLabels(verse = "Verse", chorus = "Chorus", bridge = "Bridge", tab = "Tab", grid = "Grid", intro = "Intro", preChorus = "Pre-chorus", solo = "Solo", outro = "Outro"))
    private fun measure(text: String, style: PrintStyle) = text.count { it != '\u200B' } * style.size * 0.6f
    private suspend fun layout(source: PrintSource, settings: PrintSettings = PrintSettings()) = layoutPrintDocument(source, settings, labels, ::measure)
    private fun song(lines: List<ChordProLine>, blocks: List<ChordProBlock>? = null) = PrintSong("song.cho", "A song", "Artist", song = ChordProSong(
        ChordProMetadata(key = "D", capo = 2), blocks ?: listOf(ChordProBlock.Section(SectionType.Verse, "Verse", lines))))
    private fun source(song: PrintSong) = PrintSource("A song", songs = listOf(song))
    private fun lyrics(count: Int, prefix: String = "Line") = (1..count).map { ChordProLine.Lyrics("$prefix $it", emptyList()) }
    private fun PrintDocument.placeOf(text: String) = pages.withIndex().firstNotNullOf { (index, page) ->
        page.texts.firstOrNull { it.text == text }?.let { index to it.x }
    }

    @Test fun longSongsKeepEveryLineAndChordWithinPrintableColumns() = runTest {
        val lines = (1..180).map { ChordProLine.Lyrics("Line $it " + "words ".repeat(14), listOf(ChordProLine.Lyrics.Chord(0, "D", false))) }
        (2..PrintSettings.MAX_COLUMNS).forEach { columns ->
            val settings = PrintSettings(columns = columns, fontSize = 20, marginMm = 25)
            val document = layout(source(song(lines)), settings)
            assertTrue(document.pages.size > 1)
            val texts = document.pages.flatMap { it.texts }
            assertEquals(180, texts.count { it.text == "D" })
            val margin = settings.marginMm * 72f / 25.4f
            val columnWidth = (document.width - 2 * margin - 18 * (columns - 1)) / columns
            texts.forEach { item ->
                assertTrue(item.x >= margin)
                assertTrue(item.x + measure(item.text, item.style) <= document.width - margin + 0.01f, item.toString())
                assertTrue(item.y < document.height - margin + 0.01f)
                if (item.text != "D" && item.style.size != 9) assertTrue(measure(item.text, item.style) <= columnWidth)
            }
            document.pages.forEach { page ->
                page.texts.filter { it.text == "D" }.forEach { chord ->
                    assertTrue(page.texts.any { it.x == chord.x && it.y > chord.y && it.y < chord.y + 30 && !it.style.bold })
                }
            }
        }
    }

    @Test fun everyColumnCountIsLaidOutInBothOrientations() = runTest {
        val song = song(lyrics(400))
        listOf(false, true).forEach { isLandscape ->
            (1..PrintSettings.MAX_COLUMNS).forEach { columns ->
                val texts = layout(source(song), PrintSettings(isLandscape = isLandscape, columns = columns)).pages.first().texts
                assertEquals(columns, texts.filter { it.text.startsWith("Line ") }.map { it.x }.distinct().size, "$columns, landscape: $isLandscape")
            }
        }
    }

    @Test fun adjacentChordsPadTheLyricsSoTheyRemainOverTheirOwnSyllables() = runTest {
        val line = ChordProLine.Lyrics("ab", listOf(ChordProLine.Lyrics.Chord(0, "Cmaj7", false), ChordProLine.Lyrics.Chord(1, "D", false)))
        val texts = layout(source(song(listOf(line)))).pages.single().texts
        val lyrics = texts.first { it.text.startsWith("a") }
        val chord = texts.first { it.text == "D" }
        assertEquals(lyrics.x + measure(lyrics.text.substringBefore('b'), lyrics.style), chord.x, 0.01f)
        assertTrue(chord.y < lyrics.y)
    }

    @Test fun aKeyChangeIsPrintedWhereItStandsAndOnlyWithTheChords() = runTest {
        val entry = song(emptyList(), listOf(
            ChordProBlock.Section(SectionType.Verse, "Verse", lyrics(1)),
            ChordProBlock.Transpose(2, key = "E"),
            ChordProBlock.Section(SectionType.Verse, "Last", lyrics(1, prefix = "End")),
            ChordProBlock.Transpose(0),
        ))
        val texts = layout(source(entry)).pages.single().texts
        val keyChange = texts.single { it.text == "Key: E" }
        assertTrue(keyChange.y > texts.first { it.text == "Line 1" }.y && keyChange.y < texts.first { it.text == "End 1" }.y)
        assertTrue(layout(source(entry), PrintSettings(showKey = false)).pages.single().texts.none { it.text == "Key: E" })
    }

    @Test fun aTimingChangeIsPrintedWhereItStandsAndKeptWithWhatFollows() = runTest {
        val entry = song(emptyList(), listOf(
            ChordProBlock.Section(SectionType.Verse, "Verse", lyrics(1)),
            ChordProBlock.Timing(tempo = "90", time = "3/4"),
            ChordProBlock.Section(SectionType.Verse, "Last", lyrics(1, prefix = "End")),
            ChordProBlock.Timing(tempo = "60", time = "3/4"),
        ))
        val texts = layout(source(entry)).pages.single().texts
        val timing = texts.single { it.text == "Tempo: 90 BPM   Time: 3/4" }
        assertTrue(timing.y > texts.first { it.text == "Line 1" }.y && timing.y < texts.first { it.text == "End 1" }.y)
        assertTrue(texts.none { it.text.startsWith("Tempo: 60") })
        assertTrue(layout(source(entry), PrintSettings(showTempo = false)).pages.single().texts.none { it.text.startsWith("Tempo") })
    }

    @Test fun aTimingChangeIsNotLeftAtTheEndOfAColumn() = runTest {
        val document = layout(source(song(emptyList(), (1..80).flatMap { index ->
            listOf(ChordProBlock.Timing(tempo = "${60 + index}", time = null), ChordProBlock.Section(SectionType.Verse, "Verse $index", lyrics(3, prefix = "V$index")))
        })))
        (1..80).forEach { index ->
            assertEquals(document.placeOf("Tempo: ${60 + index} BPM"), document.placeOf("Verse $index"))
        }
    }

    @Test fun lyricsOnlyOmitsChordsTabsGridsAndCommentsWhenRequested() = runTest {
        val entry = song(emptyList(), listOf(
            ChordProBlock.Comment("A comment", CommentStyle.PLAIN),
            ChordProBlock.Section(SectionType.Paragraph, null, listOf(
                ChordProLine.Lyrics("Sung words", listOf(ChordProLine.Lyrics.Chord(0, "D", false))),
                ChordProLine.Tab("e|--0--2--|"), ChordProLine.Grid(listOf(GridToken.Chord("D"))),
            )),
        ))
        val text = layout(source(entry), PrintSettings(showChords = false, showKey = false, showComments = false, showMetadata = false)).pages.flatMap { it.texts }.map { it.text }
        assertTrue("Sung words" in text)
        assertFalse(text.any { it.contains("comment") || it.contains("Capo") || it.contains("Artist") || it == "D" || it.contains("e|") })
    }

    @Test fun hiddenInstrumentalChordLinesDoNotReserveSpace() = runTest {
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

    @Test fun aTabOnlySectionLeavesOutItsLabelWhenChordsAreHidden() = runTest {
        val riff = ChordProBlock.Section(SectionType.Custom("riff"), "Riff", listOf(ChordProLine.Tab("e|--0--2--|"), ChordProLine.Tab("B|--1--3--|", continuesEnvironment = true)))
        val texts = layout(source(song(emptyList(), listOf(riff))), PrintSettings(showChords = false)).pages.flatMap { it.texts }.map { it.text }
        assertFalse("Riff" in texts)
        assertTrue("Riff" in layout(source(song(emptyList(), listOf(riff)))).pages.flatMap { it.texts }.map { it.text })
    }

    @Test fun aBareTabIsHeadedTabAndLeftOutWithItsHeadingWhenChordsAreHidden() = runTest {
        val tab = song(emptyList(), listOf(ChordProBlock.Section(SectionType.Paragraph, null, listOf(ChordProLine.Tab("e|---0---|")))))
        val texts = layout(source(tab)).pages.flatMap { it.texts }
        val heading = texts.single { it.text == "Tab" }
        assertTrue(heading.style.bold)
        assertEquals(90, heading.style.gray)
        assertTrue(texts.any { it.text.startsWith("e|") && it.y > heading.y })
        val hidden = layout(source(tab), PrintSettings(showChords = false)).pages.flatMap { it.texts }.map { it.text }
        assertFalse("Tab" in hidden || hidden.any { it.startsWith("e|") })
    }

    @Test fun aBareGridIsHeadedGridAndLeftOutWithItsHeadingWhenChordsAreHidden() = runTest {
        val grid = song(emptyList(), listOf(ChordProBlock.Section(SectionType.Paragraph, null, listOf(
            ChordProLine.Grid(listOf(GridToken.Bar("|"), GridToken.Chord("G"), GridToken.Bar("|"))),
        ))))
        val texts = layout(source(grid)).pages.flatMap { it.texts }
        val heading = texts.single { it.text == "Grid" }
        assertTrue(heading.style.bold)
        assertEquals(90, heading.style.gray)
        assertTrue(texts.any { it.text.contains("G") && it.style.monospace && it.y > heading.y })
        val hidden = layout(source(grid), PrintSettings(showChords = false)).pages.flatMap { it.texts }.map { it.text }
        assertFalse("Grid" in hidden || hidden.any { it.contains("G |") })
    }

    @Test fun aSectionWrittenWithoutLinesKeepsItsLabel() = runTest {
        val cue = ChordProBlock.Section(SectionType.Bridge, "Bridge", emptyList())
        assertTrue("Bridge" in layout(source(song(emptyList(), listOf(cue)))).pages.flatMap { it.texts }.map { it.text })
    }

    @Test fun chordOnlyLinesKeepVisibleAnnotationsWhenChordsAreHidden() = runTest {
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

    @Test fun setlistRunningOrderPreservesSelectedPositionsKeysAndMissingSongs() = runTest {
        val first = song(listOf(ChordProLine.Lyrics("First lyrics", emptyList()))).copy(title = "First", index = 1)
        val missing = first.copy(fileName = "missing.cho", title = "Missing song", index = 3, song = null)
        val last = first.copy(title = "Last", index = 5)
        val source = PrintSource("Concert", isSetlist = true, songs = listOf(first, missing, last))
        val text = layout(source, PrintSettings(setlistMode = PrintSettings.SetlistMode.RUNNING_ORDER)).pages.flatMap { it.texts }.map { it.text }
        assertEquals(listOf("1. First (Key: D)", "3. Missing song [Missing]", "5. Last (Key: D)"), text.filter { it.firstOrNull()?.isDigit() == true && it.length > 8 })
        assertFalse("First lyrics" in text)
    }

    @Test fun setlistSongsStartOnNewPagesAndKeepOriginalOrder() = runTest {
        val first = song(listOf(ChordProLine.Lyrics("First lyrics", emptyList()))).copy(title = "First", index = 1)
        val last = first.copy(title = "Last", index = 2)
        val document = layout(PrintSource("Concert", isSetlist = true, songs = listOf(first, last)))
        assertEquals(3, document.pages.size)
        assertEquals(listOf("Concert", "1. First", "2. Last"), document.pages.map { it.texts.first().text })
    }

    @Test fun landscapeLetterUsesPhysicalPaperSizeAndEmptySelectionProducesNoPages() = runTest {
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

    @Test fun tablatureWrapsAllStringsAtTheSameColumns() = runTest {
        val lines = listOf("e", "B", "G", "D", "A", "E").mapIndexed { i, prefix ->
            ChordProLine.Tab("$prefix|" + "--$i--|".repeat(30), continuesEnvironment = i > 0)
        }
        val document = layout(source(song(lines)), PrintSettings(columns = 2, fontSize = 16))
        val prefixes = document.pages.flatMap { it.texts }.filter { it.text.contains('|') }.map { it.text.substringBefore('|') }
        assertTrue(prefixes.size > 6)
        prefixes.chunked(6).forEach { assertEquals(listOf("e", "B", "G", "D", "A", "E"), it) }
        val strings = document.pages.first().texts.filter { it.text.contains('|') }
        assertTrue(strings[6].y - strings[5].y >= 16 * 1.45f + 16 * 0.7f - 0.01f)
    }

    @Test fun aWrappedPreformattedRunHasNoGapBetweenItsSystems() = runTest {
        val lines = listOf("G" + " ".repeat(60) + "C", "Words " .repeat(12)).mapIndexed { i, text -> ChordProLine.Tab(text, continuesEnvironment = i > 0) }
        val page = layout(source(song(lines)), PrintSettings(columns = 2, fontSize = 16)).pages.single().texts
        val texts = page.filter { it.style.size == 16 && it.y > page.first { label -> label.text == "Verse" }.y }
        assertTrue(texts.size > 2)
        texts.zipWithNext().forEach { (upper, lower) -> assertEquals(16 * 1.45f, lower.y - upper.y, 0.01f) }
    }

    @Test fun aHeadingStaysInTheColumnOfItsFirstSection() = runTest {
        val settings = PrintSettings(columns = 2, startSongsOnNewPage = false)
        (1..90).forEach { count ->
            val first = song(lyrics(count, "First")).copy(title = "First")
            val second = song(lyrics(10, "Second")).copy(title = "Second")
            val document = layout(PrintSource("Two songs", songs = listOf(first, second)), settings)
            assertEquals(document.placeOf("Second"), document.placeOf("Second 1"), "after $count lines")
        }
    }

    @Test fun aBreakAtTheStartOfASongDoesNotStrandItsHeading() = runTest {
        val entry = song(emptyList(), listOf(ChordProBlock.Break, ChordProBlock.Section(SectionType.Verse, "Verse", lyrics(3))))
        val document = layout(source(entry), PrintSettings(columns = 2))
        assertEquals(document.placeOf("A song"), document.placeOf("Line 1"))
    }

    @Test fun aSectionTooTallToShareAColumnWithItsHeadingStartsUnderIt() = runTest {
        val settings = PrintSettings(columns = 2)
        val margin = settings.marginMm * 72f / 25.4f
        val capacity = settings.paper.height - 2 * margin - 18f
        // The section is one row shorter than a column: its label and all but two of the rows that would fill one.
        val document = layout(source(song(lyrics((capacity / (settings.fontSize * 1.45f)).toInt() - 2))), settings)
        assertEquals(document.placeOf("A song"), document.placeOf("Line 1"))
    }

    @Test fun aMissingSongKeepsItsHeadingWithItsNotice() = runTest {
        val settings = PrintSettings(columns = 2, startSongsOnNewPage = false)
        (1..90).forEach { count ->
            val first = song(lyrics(count, "First")).copy(title = "First")
            val missing = first.copy(title = "Gone", song = null)
            val document = layout(PrintSource("Two songs", songs = listOf(first, missing)), settings)
            assertEquals(document.placeOf("Gone"), document.placeOf("Missing"), "after $count lines")
        }
    }

    @Test fun thePageNumberIsCentredInsideTheBandReservedForIt() = runTest {
        listOf(10, 25).forEach { marginMm ->
            val settings = PrintSettings(marginMm = marginMm)
            val document = layout(source(song(lyrics(200))), settings)
            val margin = marginMm * 72f / 25.4f
            val bottom = document.height - margin - 18f
            assertTrue(document.pages.size > 1)
            document.pages.forEachIndexed { index, page ->
                val number = page.texts.single { it.text == "${index + 1} / ${document.pages.size}" }
                assertTrue(number.y + 11 <= document.height - margin && number.y >= bottom, number.toString())
                assertEquals(document.width / 2, number.x + measure(number.text, number.style) / 2, 0.01f)
                (page.texts - number).forEach { assertTrue(it.y + it.style.size * 1.45f <= bottom + 0.01f, it.toString()) }
            }
        }
    }

    @Test fun aChordOnlyLineTakesOneRow() = runTest {
        val entry = song(listOf(
            ChordProLine.Lyrics("", listOf(ChordProLine.Lyrics.Chord(0, "G", false), ChordProLine.Lyrics.Chord(0, "C", false))),
            ChordProLine.Lyrics("Sung words", emptyList()),
        ))
        val texts = layout(source(entry)).pages.single().texts
        assertEquals(texts.first { it.text == "G" }.y + 12 * 1.45f, texts.first { it.text == "Sung words" }.y, 0.01f)
        assertFalse(texts.any { it.text.isBlank() || it.text.all { char -> char == '\u00A0' || char == ' ' } })
    }

    @Test fun anAnnotationWiderThanTheColumnNeitherOpensWithAnEmptyRowNorPushesTheLyricsAway() = runTest {
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
        val annotationRows = texts.filter { annotation.contains(it.text.trim()) && it.style.italic && it.text.isNotBlank() }
        assertEquals(label.y + label.style.size * 1.45f, annotationRows.minOf { it.y }, 0.01f)
        val lyrics = texts.first { it.text.contains("here") }
        val follow = texts.first { it.text.contains("follow") }
        assertTrue(follow.y <= lyrics.y + 12 * 2.75f + 0.01f, "$lyrics $follow")
        texts.filter { it.style.size != 9 }.forEach { assertTrue(it.x + measure(it.text, it.style) <= margin + columnWidth + 0.01f, it.toString()) }
    }

    @Test fun aChorusRecallIsHeadedByItsOwnLabel() = runTest {
        val chorus = ChordProBlock.Section(SectionType.Chorus, null, listOf(ChordProLine.Lyrics("Sing along", emptyList())))
        val texts = layout(source(song(emptyList(), listOf(chorus, ChordProBlock.ChorusRecall("Last time", listOf(chorus))))))
            .pages.flatMap { it.texts }.map { it.text }
        assertEquals(1, texts.count { it == "Chorus" })
        assertEquals(1, texts.count { it == "Last time" })
        assertEquals(2, texts.count { it == "Sing along" })
        assertTrue(texts.indexOf("Last time") > texts.indexOf("Chorus"))
    }

    @Test fun aChorusRecallOpeningWithACommentIsHeadedByTheChorusLabel() = runTest {
        val chorus = ChordProBlock.Section(SectionType.Chorus, "Refrain", lyrics(1))
        val comment = ChordProBlock.Comment("x", CommentStyle.PLAIN, CommentPlacement.START_OF_SECTION)
        val texts = layout(source(song(emptyList(), listOf(comment, chorus, ChordProBlock.ChorusRecall(null, listOf(comment, chorus))))))
            .pages.flatMap { it.texts }.map { it.text }
        assertEquals(2, texts.count { it == "Refrain" })
        assertEquals(0, texts.count { it == "Chorus" })
    }

    @Test fun aChorusRecallWithNothingToRecallPrintsItsHeading() = runTest {
        val texts = layout(source(song(emptyList(), listOf(ChordProBlock.ChorusRecall(null))))).pages.flatMap { it.texts }
        assertEquals(1, texts.count { it.text == "Chorus" && it.style.bold })
    }

    @Test fun anUnnamedVerseIsHeadedAsAVerseAndAParagraphIsNotHeaded() = runTest {
        val unnamed = ChordProBlock.Section(SectionType.Verse, null, lyrics(2, "Plain"))
        val named = ChordProBlock.Section(SectionType.Verse, "Verse 2", lyrics(2, "Named"))
        val paragraph = ChordProBlock.Section(SectionType.Paragraph, null, lyrics(2, "Loose"))
        val texts = layout(source(song(emptyList(), listOf(unnamed, named, paragraph)))).pages.flatMap { it.texts }
        assertEquals(1, texts.count { it.text == "Verse" && it.style.bold })
        assertEquals(1, texts.count { it.text == "Verse 2" && it.style.bold })
        assertTrue(texts.first { it.text == "Verse" }.y < texts.first { it.text == "Plain 1" }.y)
        val labelSize = texts.first { it.text == "Verse 2" }.style.size
        assertEquals(listOf("Verse", "Verse 2"), texts.filter { it.style.bold && it.style.size == labelSize }.map { it.text })
    }

    @Test fun aGridLineBreaksBetweenBars() = runTest {
        val chords = listOf("C", "F", "G", "Am", "Dm", "Em", "F", "G")
        fun grid(prefix: List<GridToken>) = prefix + listOf(GridToken.Bar("|:")) + chords.flatMapIndexed { index, chord ->
            listOf(GridToken.Chord(chord), GridToken.Beat, GridToken.Bar(if (index == chords.lastIndex) ":|" else "|"))
        }
        suspend fun rows(tokens: List<GridToken>): List<String> {
            val entry = song(emptyList(), listOf(ChordProBlock.Section(SectionType.Paragraph, null, listOf(ChordProLine.Grid(tokens)))))
            // A two-column A4 page at 20 points holds three of these bars to a row, and not four.
            return layout(source(entry), PrintSettings(columns = 2, fontSize = 20)).pages.flatMap { it.texts }.filter { it.style.size == 20 && it.style.bold }.map { it.text }
        }
        val bars = setOf("|", "|:", ":|")
        val tokens = grid(emptyList())
        val rows = rows(tokens)
        assertTrue(rows.size > 2, rows.toString())
        rows.dropLast(1).forEach { assertTrue(it.substringAfterLast(' ') in bars, rows.toString()) }
        rows.drop(1).forEach { assertFalse(it.substringBefore(' ') in bars, rows.toString()) }
        assertEquals(tokens.joinToString(" ") { (it as? GridToken.Chord)?.name ?: (it as? GridToken.Bar)?.text ?: "." }, rows.joinToString(" "))
        assertTrue(rows(grid(listOf(GridToken.Text("Intro")))).first().startsWith("Intro"))
    }

    @Test fun aMultiLineDescriptionTakesARowPerLine() = runTest {
        val entry = song(lyrics(1)).copy(index = 1)
        val document = layout(PrintSource("Concert", "a\nb", isSetlist = true, songs = listOf(entry)), PrintSettings(setlistMode = PrintSettings.SetlistMode.RUNNING_ORDER))
        val texts = document.pages.flatMap { it.texts }
        assertEquals(texts.first { it.text == "a" }.y + 12 * 1.45f, texts.first { it.text == "b" }.y, 0.01f)
        assertFalse(texts.any { '\n' in it.text || '\r' in it.text })
    }

    @Test fun wrappingBreaksAtTheLastSpaceWhenTheWordAfterItFitsALine() {
        val word = "averyveryverylongwordthatalmostfits"
        assertEquals(listOf("Oh ", word), wrapPrintText("Oh $word", word.length + 1f) { it.length.toFloat() })
    }

    @Test fun wrappingNeverSplitsAGraphemeCluster() {
        listOf("o\u030B", "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67", "\uD83D\uDC4D\uD83C\uDFFD", "\uD83C\uDDED\uD83C\uDDFA").forEach { cluster ->
            val input = "ab" + cluster.repeat(3) + "cd"
            (1..input.length).forEach { width ->
                val parts = wrapPrintText(input, width.toFloat()) { it.length.toFloat() }
                assertEquals(input, parts.joinToString(""))
                val boundaries = parts.runningFold(0) { position, part -> position + part.length }
                // Every cut falls before "ab", between the two letters, at a cluster boundary or among "cd".
                val allowed = setOf(0, 1, 2) + (0..3).map { 2 + it * cluster.length } + setOf(input.length - 1, input.length)
                assertTrue(boundaries.all { it in allowed }, "$cluster at $width: $parts")
            }
        }
    }

    @Test fun wrappingEmojiIntoNarrowColumnsAlwaysAdvancesAndKeepsSurrogatesPaired() {
        val input = "\uD83D\uDE00".repeat(5)
        val parts = wrapPrintText(input, 1f) { it.length.toFloat() }
        assertEquals(input, parts.joinToString(""))
        assertTrue(parts.all { it.isNotEmpty() && !it.last().isHighSurrogate() && !it.first().isLowSurrogate() })
    }

    @Test fun aLineThatFitsIsMeasuredOnce() {
        var calls = 0
        assertEquals(listOf("Fits"), wrapPrintText("Fits", 100f) { calls++; it.length.toFloat() })
        assertEquals(1, calls)
    }

    @Test fun repeatedChordsAreMeasuredOnce() = runTest {
        val chords = listOf("G", "C", "D", "Em")
        val lines = (1..40).map { line ->
            ChordProLine.Lyrics("Line $line of a short song", chords.mapIndexed { index, chord -> ChordProLine.Lyrics.Chord(index * 5, chord, false) })
        }
        var calls = 0
        layoutPrintDocument(source(song(lines)), PrintSettings(), labels) { text, style -> calls++; measure(text, style) }
        assertTrue(calls <= 12 * 40, "$calls measurements")
    }

    @Test fun onlyTablatureAndGridsAreMonospaceAndTheTitleLeads() = runTest {
        val entry = song(listOf(
            ChordProLine.Lyrics("Sung words", listOf(ChordProLine.Lyrics.Chord(0, "D", false))),
            ChordProLine.Tab("e|--0--2--|"),
            ChordProLine.Grid(listOf(GridToken.Bar("|"), GridToken.Chord("G"), GridToken.Bar("|"))),
        ))
        val texts = layout(source(entry)).pages.single().texts
        assertTrue(texts.single { it.text == "e|--0--2--|" }.style.monospace)
        assertTrue(texts.single { it.text == "| G |" }.style.monospace)
        assertFalse(texts.single { it.text == "Sung words" }.style.monospace)
        assertFalse(texts.single { it.text == "D" }.style.monospace)
        assertEquals(PrintStyle(19, bold = true), texts.single { it.text == "A song" }.style)
        assertEquals(90, texts.single { it.text == "Verse" }.style.gray)
    }

    @Test fun anAnnotationIsItalicAndNotBold() = runTest {
        val entry = song(listOf(ChordProLine.Lyrics("Sing", listOf(ChordProLine.Lyrics.Chord(0, "Everybody", true), ChordProLine.Lyrics.Chord(0, "D7", false)))))
        val texts = layout(source(entry)).pages.single().texts
        val annotation = texts.single { it.text == "Everybody" }
        assertTrue(annotation.style.italic && !annotation.style.bold)
        assertTrue(texts.single { it.text == "D7" }.style.bold)
    }

    @Test fun aPlainCommentIsItalic() = runTest {
        val texts = layout(source(song(emptyList(), listOf(ChordProBlock.Comment("Repeat twice", CommentStyle.PLAIN))))).pages.single().texts
        assertTrue(texts.single { it.text == "Repeat twice" }.style.italic)
    }

    @Test fun aBoxedCommentIsFramedInsideItsColumn() = runTest {
        val settings = PrintSettings(columns = 2)
        val document = layout(source(song(emptyList(), listOf(ChordProBlock.Comment("Boxed", CommentStyle.BOX)))), settings)
        val page = document.pages.single()
        val text = page.texts.single { it.text == "Boxed" }
        val margin = settings.marginMm * 72f / 25.4f
        val columnWidth = (document.width - 2 * margin - 18) / 2
        assertEquals(4, page.rules.size)
        page.rules.forEach { assertTrue(it.x >= margin - 0.01f && it.x + it.width <= margin + columnWidth + 0.01f, it.toString()) }
        assertTrue(page.rules.minOf { it.x } < text.x && page.rules.maxOf { it.x + it.width } > text.x + measure(text.text, text.style))
        assertTrue(page.rules.minOf { it.y } < text.y && page.rules.maxOf { it.y + it.height } > text.y + text.style.size * 1.2f)
    }

    @Test fun aBoxedCommentSplitBetweenColumnsStaysAnOpenFrame() = runTest {
        val settings = PrintSettings(columns = 2)
        val document = layout(source(song(emptyList(), listOf(ChordProBlock.Comment("Boxed words ".repeat(120).trim(), CommentStyle.BOX)))), settings)
        val rules = document.pages.first().rules
        val margin = settings.marginMm * 72f / 25.4f
        val horizontal = rules.filter { it.width > 1f }
        val vertical = rules.filter { it.width < 1f }
        assertEquals(2, horizontal.size)
        assertTrue(vertical.any { it.x < document.width / 2 } && vertical.any { it.x > document.width / 2 })
        assertTrue(horizontal.minOf { it.x } < document.width / 2 && horizontal.maxOf { it.x } > document.width / 2)
        assertTrue(rules.all { it.y >= margin - 0.01f })
    }

    @Test fun aChorusIsIndentedBehindABarAndAVerseIsNot() = runTest {
        val settings = PrintSettings(columns = 2)
        val chorus = ChordProBlock.Section(SectionType.Chorus, null, listOf(ChordProLine.Lyrics("Chorus words " + "and more words ".repeat(6), emptyList())) + lyrics(3, "Sung"))
        val document = layout(source(song(emptyList(), listOf(chorus))), settings)
        val page = document.pages.single()
        val margin = settings.marginMm * 72f / 25.4f
        val columnWidth = (document.width - 2 * margin - 18) / 2
        val label = page.texts.single { it.text == "Chorus" }
        val chorusTexts = page.texts.filter { it.y >= label.y && it.style.size != 9 }
        assertTrue(chorusTexts.size > 5)
        chorusTexts.forEach { assertTrue(it.x >= margin + 8 - 0.01f && it.x + measure(it.text, it.style) <= margin + columnWidth + 0.01f, it.toString()) }
        // The label's row, then the wrapped first line and three more, each a lyric row tall.
        val lyricRows = chorusTexts.count { it.style == PrintStyle(12) }
        assertTrue(lyricRows > 4)
        assertEquals(11 * 1.45f + lyricRows * 12 * 1.45f, page.rules.sumOf { it.height.toDouble() }.toFloat(), 0.01f)
        assertTrue(layout(source(song(lyrics(3)))).pages.single().rules.isEmpty())
    }

    @Test fun aCommentInsideAChorusCarriesItsBar() = runTest {
        val document = layout(source(song(emptyList(), listOf(
            ChordProBlock.Section(SectionType.Chorus, null, lyrics(2, "Before")),
            ChordProBlock.Comment("Softly", CommentStyle.PLAIN, CommentPlacement.IN_SECTION),
            ChordProBlock.Section(SectionType.Chorus, null, lyrics(2, "After"), isContinuation = true),
        ))))
        val page = document.pages.single()
        val comment = page.texts.single { it.text == "Softly" }
        val margin = PrintSettings().marginMm * 72f / 25.4f
        assertTrue(comment.x >= margin + 8 - 0.01f)
        assertTrue(page.rules.any { it.y <= comment.y && it.y + it.height >= comment.y + comment.style.size * 1.45f })
        // One bar from the label to the last line, with no gap where the comment cuts the chorus.
        val bars = page.rules.sortedBy { it.y }
        bars.zipWithNext().forEach { (upper, lower) -> assertEquals(upper.y + upper.height, lower.y, 0.01f) }
    }

    private fun guitarChord(name: String) = PrintChord(name, geometry = ChordDiagramGeometry.Fretted(6, 1, 4, List(6) { null }, emptyList(), emptyList()))

    @Test fun chordDiagramsArePrintedUnderTheHeadingOnlyWhenAskedForAndWithTheChords() = runTest {
        val entry = song(lyrics(2)).copy(chords = listOf(guitarChord("D"), guitarChord("G")))
        assertTrue(layout(source(entry), PrintSettings(showChordDiagrams = false)).pages.single().diagrams.isEmpty())
        assertTrue(layout(source(entry), PrintSettings(showChordDiagrams = true, showChords = false)).pages.single().diagrams.isEmpty())
        val page = layout(source(entry), PrintSettings(showChordDiagrams = true)).pages.single()
        assertEquals(2, page.diagrams.size)
        val names = page.texts.filter { it.text == "D" || it.text == "G" }
        assertEquals(2, names.size)
        assertTrue(names.none { it.isSelectable })
        assertTrue(page.texts.single { it.text == "A song" }.y < names.first().y)
        assertTrue(page.diagrams.all { diagram -> diagram.y + diagram.height <= page.texts.first { it.text == "Line 1" }.y })
    }

    @Test fun chordDiagramsWrapIntoRowsInsideTheColumn() = runTest {
        val settings = PrintSettings(showChordDiagrams = true, columns = 4)
        val document = layout(source(song(lyrics(1)).copy(chords = (1..12).map { guitarChord("C$it") })), settings)
        val diagrams = document.pages.single().diagrams
        val margin = settings.marginMm * 72f / 25.4f
        val columnWidth = (document.width - 2 * margin - 18 * 3) / 4
        assertEquals(12, diagrams.size)
        assertTrue(diagrams.map { it.y }.distinct().size > 1)
        diagrams.forEach { assertTrue(it.x >= margin && it.x + it.width <= margin + columnWidth + 0.01f, it.toString()) }
    }

    @Test fun chordDiagramNamesStayInsideTheirColumn() = runTest {
        val settings = PrintSettings(showChordDiagrams = true, columns = 4, fontSize = 20, marginMm = 25)
        val chords = listOf(
            guitarChord("b7sus4").copy(secondaryName = "Bbsus4"),
            guitarChord("C#m7b5/G#"),
            guitarChord("1"),
            guitarChord("4").copy(secondaryName = "F"),
        )
        val document = layout(source(song(lyrics(1)).copy(chords = chords)), settings)
        val page = document.pages.single()
        val margin = settings.marginMm * 72f / 25.4f
        val columnWidth = (document.width - 2 * margin - 18 * 3) / 4
        val names = page.texts.filter { !it.isSelectable }
        assertTrue(names.any { it.text == "Bbsus4" } && names.none { it.text.endsWith("Bbsus4") && it.text != "Bbsus4" })
        // The rows flow on into the next column where the first is full, so each name is held to the one it starts in.
        fun columnOf(x: Float) = ((x - margin + 0.01f) / (columnWidth + 18)).toInt()
        names.forEach {
            val columnStart = margin + columnOf(it.x) * (columnWidth + 18)
            assertTrue(it.x >= columnStart - 0.01f && it.x + measure(it.text, it.style) <= columnStart + columnWidth + 0.01f, it.toString())
        }
        names.forEach { name ->
            // The diagrams of a name's row are the first line of them below it in its column.
            val rowDiagrams = page.diagrams.filter { it.y > name.y && columnOf(it.x) == columnOf(name.x) }.minOf { it.y }
            assertTrue(rowDiagrams >= name.y + name.style.size * 1.45f - 0.01f, name.toString())
        }
    }

    @Test fun aNameThatFitsKeepsItsSecondNameOnItsLine() = runTest {
        val document = layout(source(song(lyrics(1)).copy(chords = listOf(guitarChord("5").copy(secondaryName = "G")))), PrintSettings(showChordDiagrams = true))
        val texts = document.pages.single().texts
        assertEquals(texts.single { it.text == "5" }.y, texts.single { it.text == " G" }.y)
    }

    @Test fun aMissingSongPrintsNoDiagrams() = runTest {
        val entry = PrintSong("gone.cho", "Gone", null, song = null, chords = listOf(guitarChord("D")))
        assertTrue(layout(source(entry), PrintSettings(showChordDiagrams = true)).pages.single().diagrams.isEmpty())
    }

    @Test fun howASongIsPlayedIsARowOfItsOwnUnderTheHeadingWithItsTransposition() = runTest {
        val entry = PrintSong("song.cho", "A song", "Artist", transposition = -3, song = ChordProSong(
            ChordProMetadata(key = "B", capo = 2, tempo = "96", time = "3/4"), listOf(ChordProBlock.Section(SectionType.Verse, "Verse", lyrics(1)))))
        val texts = layout(source(entry)).pages.single().texts
        assertEquals("Key: B   Transposition: -3   Capo: 2   Tempo: 96 BPM   Time: 3/4", texts.single { it.text.startsWith("Key:") }.text)
        assertTrue(texts.any { it.text == "Artist" })
        val withoutDetails = layout(source(entry), PrintSettings(showMetadata = false)).pages.single().texts
        assertTrue(withoutDetails.none { it.text == "Artist" } && withoutDetails.any { it.text.startsWith("Key: B") })
        assertEquals("Tempo: 96 BPM   Time: 3/4", layout(source(entry), PrintSettings(showKey = false)).pages.single().texts.single { it.text.startsWith("Tempo") }.text)
        assertEquals("Key: B   Transposition: -3   Capo: 2", layout(source(entry), PrintSettings(showTempo = false)).pages.single().texts.single { it.text.startsWith("Key") }.text)
        assertTrue(layout(source(entry.copy(transposition = 0, song = entry.song!!.copy(metadata = ChordProMetadata(capo = 0))))).pages.single().texts.none { it.style == PrintStyle(11, gray = 90) })
    }

    @Test fun theFeatureSwitchesLeaveOutWhatTheyTakeAwayWhateverTheOptionsSay() {
        val chosen = PrintSettings(showChords = true, showChordDiagrams = true, showKey = true, showTempo = true)
        assertEquals(chosen.copy(showChords = false, showChordDiagrams = false, showKey = false), chosen.withinFeatures(areChordsEnabled = false, isMetronomeEnabled = true))
        assertEquals(chosen.copy(showTempo = false), chosen.withinFeatures(areChordsEnabled = true, isMetronomeEnabled = false))
        assertEquals(chosen, chosen.withinFeatures(areChordsEnabled = true, isMetronomeEnabled = true))
    }
}
