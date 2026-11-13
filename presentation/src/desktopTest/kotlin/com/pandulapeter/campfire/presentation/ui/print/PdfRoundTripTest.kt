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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.formats.document.PdfTextExtractor
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.allFontResources
import com.pandulapeter.campfire.presentation.ui.rendering.testSongRenderer
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import com.pandulapeter.campfire.presentation.ui.theme.scriptFaces
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every song of :libs:chordpro's corpus printed as the export prints it in one column at the default size, and its PDF read
 * back as the import reads any PDF: the title, every lyric in reading order, and every chord over its syllable come back.
 * The export's other layouts are measured by the import corpus's Campfire documents rather than pinned here.
 */
class PdfRoundTripTest {

    private val renderer = PrintRenderer(
        measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr),
        monospaceFontFamily = bundledFontFamily("jetbrains_mono_regular" to FontWeight.Normal, "jetbrains_mono_bold" to FontWeight.Bold),
        textFontFamily = bundledFontFamily("inter_regular" to FontWeight.Normal, "inter_medium" to FontWeight.Medium, "inter_bold" to FontWeight.Bold),
        scriptFontFamilies = scriptFaces.map { face ->
            face to bundledFontFamily(Res.allFontResources.entries.single { it.value == face.font }.key to FontWeight.Normal)
        },
    )
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
     * back as lyrics, and a bracket naming no chord (`[x]`), which is printed in bold like a chord but not taken for one.
     */
    private fun printable(text: String) = text
        .replace(Regex("\\{start_of_textblock\\}.*?\\{end_of_textblock\\}\n", RegexOption.DOT_MATCHES_ALL), "")
        .replace("[x]", "")

    @Test
    fun `a song printed to a PDF in one column and imported back keeps its title, its lyrics in order, and every chord on its word`() = runBlocking {
        val failures = corpus.mapNotNull { (name, text) ->
            val original = songRenderer.transposedSong(text, transposition = 0, spelling = UserPreferences.ChordSpelling.Default)
            val imported = roundTrip(original, PrintSettings())
            val expected = lyricsOf(original)
            val actual = lyricsOf(imported)
            if (titleOf(original) == titleOf(imported) && isInOrder(expected, actual)) null else "$name:\n  expected ${titleOf(original)} $expected\n  actual   ${titleOf(imported)} $actual"
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n\n"))
    }

    @Test
    fun `a drawn grid printed to a PDF and imported back is its grid lines again`() = runBlocking {
        val text = "{title: Grid song}\n\n{start_of_grid}\nIntro |: Am . . . | C . G . :| x2\n| F . . . |1 % :|2 E7 . . . |.\n{end_of_grid}\n"
        val original = songRenderer.transposedSong(text, transposition = 0, spelling = UserPreferences.ChordSpelling.Default)
        val imported = roundTrip(original, PrintSettings())
        assertEquals(gridLinesOf(original), gridLinesOf(imported))
    }

    private fun gridLinesOf(song: ChordProSong) = song.blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }
        .filterIsInstance<ChordProLine.Grid>().map { it.tokens }

    /** [song] printed as the export prints it and read back as the import reads it. */
    private suspend fun roundTrip(song: ChordProSong, settings: PrintSettings): ChordProSong {
        val printed = songRenderer.notatedSong(song, UserPreferences.ChordSpelling.Default)
        val title = song.metadata.title.orEmpty()
        val entry = PrintSong("song.cho", title, song.metadata.artist, song = printed)
        val document = layoutPrintDocument(PrintSource(title, songs = listOf(entry)), settings, labels, renderer::width, measureLyric = renderer::lyricLayout)
        val extracted = PdfTextExtractor.extract(renderer.pdf(document, title))
        return ChordProParser.parse(ChordSheetConverter.convert(extracted).single())
    }

    /** Whether every line of [expected] is one of [actual], in their order, whatever else [actual] holds between them. */
    private fun isInOrder(expected: List<String>, actual: List<String>): Boolean {
        val rest = actual.iterator()
        return expected.all { line -> rest.asSequence().any { it == line } }
    }

    private fun titleOf(song: ChordProSong) = song.metadata.title?.replace('\u00A0', ' ')

    /**
     * Every lyric line of [song] in order, each word with the chords set in it before it, and the chords after the last
     * word after the line. What a chord stands on within its word is left out, and so are comments and section labels,
     * which a reader of any PDF may take for lyrics: this test pins what every good reader gets, and what a reader of
     * Campfire's exports in particular gets is measured by the import corpus.
     */
    private fun lyricsOf(song: ChordProSong): List<String> = song.blocks.flatMap(::lyricsOf)

    private fun lyricsOf(block: ChordProBlock): List<String> = when (block) {
        is ChordProBlock.Section -> block.lines.filterIsInstance<ChordProLine.Lyrics>().map(::byWord)
        is ChordProBlock.ChorusRecall -> block.blocks.flatMap(::lyricsOf)
        else -> emptyList()
    }

    private fun byWord(line: ChordProLine.Lyrics): String {
        val words = Regex("[^\\s\u00A0]+").findAll(line.text).toList()
        val chords = line.chords.groupBy { chord -> words.indexOfFirst { chord.position <= it.range.last }.takeIf { it >= 0 } ?: words.size }
        return (words.mapIndexed { index, word -> chords[index].orEmpty().joinToString("") { "[${it.name}]" } + word.value } +
            chords[words.size].orEmpty().joinToString("") { "[${it.name}]" }).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * The faces the export prints in, read from the files the app bundles: the system's own faces, which a renderer
     * built without them falls back on, differ from one operating system to the next, and so would what is tested.
     */
    private fun bundledFontFamily(vararg faces: Pair<String, FontWeight>) = FontFamily(
        faces.map { (name, weight) -> Font(identity = name, data = File("src/commonMain/composeResources/font/$name.ttf").readBytes(), weight = weight) },
    )
}
