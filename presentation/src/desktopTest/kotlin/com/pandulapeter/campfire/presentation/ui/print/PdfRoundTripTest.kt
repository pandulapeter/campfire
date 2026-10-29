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

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.convert.ChordSheet
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.formats.document.PdfTextExtractor
import com.pandulapeter.campfire.data.model.importing.ExtractedDocument
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.rendering.testSongRenderer
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every song of :chordpro's corpus printed as the export prints it, and its PDF read back as the import reads it: the
 * title, the sections, every lyric in reading order, and every chord over its syllable come back, in both directions.
 */
class PdfRoundTripTest {

    private val renderer = PrintRenderer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr))
    private val songRenderer = testSongRenderer()
    private val labels = PrintLabels(
        "Key", "Transposition", "Capo", "Tempo", "$TEMPO_VALUE BPM", "Time", "Missing",
        DefaultSectionLabels(
            verse = "Verse", chorus = "Chorus", bridge = "Bridge", tab = "Tab", grid = "Grid", intro = "Intro", preChorus = "Pre-chorus",
            solo = "Solo", outro = "Outro", abc = "ABC notation", lilyPond = "LilyPond", svg = "SVG",
        ),
    )

    private val corpus = File(requireNotNull(javaClass.getResource("/corpus")).toURI()).listFiles().orEmpty()
        .filter { it.extension == "cho" }.sortedBy { it.name }.associate { it.name to printable(it.readText().removePrefix("\uFEFF")) }

    /**
     * [text] without what the export prints but the import does not read back as it was: a text block, whose prose comes
     * back as lyrics, and the annotations set among the chords (`[*Riff]`, a bracket naming no chord), which a chord row
     * holding them is not taken for.
     */
    private fun printable(text: String) = text
        .replace(Regex("\\{start_of_textblock\\}.*?\\{end_of_textblock\\}\n", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("\\[(\\*[^\\]]*|x)]"), "")

    @Test
    fun `a song printed to a PDF and imported back keeps its title, sections, lyrics, and chords`() = runBlocking {
        val failures = corpus.flatMap { (name, text) ->
            listOf(PrintSettings(), PrintSettings(columns = 2, fontSize = 9)).mapNotNull { settings ->
                val original = songRenderer.transposedSong(text, transposition = 0, spelling = UserPreferences.ChordSpelling.Default)
                val expected = summary(original)
                val actual = summary(roundTrip(original, settings))
                if (expected == actual) null else "$name in ${settings.columns} columns:\n  expected $expected\n  actual   $actual"
            }
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n\n"))
    }

    private suspend fun roundTrip(song: ChordProSong, settings: PrintSettings): ChordProSong {
        val printed = songRenderer.notatedSong(song, UserPreferences.ChordSpelling.Default)
        val title = song.metadata.title.orEmpty()
        val document = layoutPrintDocument(
            PrintSource(title, songs = listOf(PrintSong("song.cho", title, song.metadata.artist, song = printed))),
            settings, labels, renderer::width, measureLyric = renderer::lyricLayout,
        )
        val extracted = PdfTextExtractor.extract(renderer.pdf(document, title))
        return ChordProParser.parse(ChordSheetConverter.convert(extracted.toChordSheet()).single())
    }

    /**
     * What the round trip promises to keep: the title, the artist, how the song is played, and each section's kind with its lyrics and their chords, written
     * inline. A section of no lyrics (a tab, a grid, a tune) is left out, and so is the spacing around a chord past a
     * line's end and after a chord, which the export's padding leaves undecided.
     */
    private fun summary(song: ChordProSong) = with(song.metadata) {
        listOf(title, artist, key, capo?.toString(), tempo, time).map { it?.replace('\u00A0', ' ') }
    } + song.blocks.flatMap { block ->
        when (block) {
            is ChordProBlock.Section -> listOf(block)
            is ChordProBlock.ChorusRecall -> block.blocks.filterIsInstance<ChordProBlock.Section>()
            else -> emptyList()
        }
    }.filter { section -> section.lines.any { it is ChordProLine.Lyrics } }.map { section ->
        section.type.toString().replace('_', '-') + ": " + section.lines.filterIsInstance<ChordProLine.Lyrics>().joinToString(" / ") { line ->
            buildString {
                var at = 0
                line.chords.forEach { chord ->
                    append(line.text, at.coerceAtMost(line.text.length), chord.position.coerceAtMost(line.text.length))
                    append('[').append(chord.name).append(']')
                    at = chord.position
                }
                append(line.text.substring(at.coerceAtMost(line.text.length)))
            }.replace('\u00A0', ' ').replace(Regex(" +"), " ").replace(Regex(" (\\[[^\\]]*\\])$"), "$1").replace("] ", "]").trim()
        }
    }

    private fun ExtractedDocument.toChordSheet() = ChordSheet(
        pages = pages.map { page -> ChordSheet.Page(page.lines.map { line -> ChordSheet.Line(line.spans.map { it.toChordSheetSpan() }, line.isHeading, line.baseline) }) },
        isCampfireExport = isCampfireExport,
    )

    private fun ExtractedDocument.Span.toChordSheetSpan(): ChordSheet.Span =
        ChordSheet.Span(text, start, end, size, isBold, isMonospace, isRaised, isItalic, pieces.map { it.toChordSheetSpan() })
}
