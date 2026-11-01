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
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.chordpro.convert.ChordSheet
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.SectionType
import com.pandulapeter.campfire.chordpro.scales.SongKeys
import com.pandulapeter.campfire.data.formats.document.PdfTextExtractor
import com.pandulapeter.campfire.data.model.importing.ExtractedDocument
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.rendering.testSongRenderer
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every song of :chordpro's corpus printed as the export prints it, and its PDF read back as the import reads it: the
 * title, the sections, every lyric in reading order, and every chord over its syllable come back, in both directions.
 */
class PdfRoundTripTest {

    private val renderer = PrintRenderer(
        measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr),
        monospaceFontFamily = bundledFontFamily("jetbrains_mono_regular" to FontWeight.Normal, "jetbrains_mono_bold" to FontWeight.Bold),
        textFontFamily = bundledFontFamily("inter_regular" to FontWeight.Normal, "inter_medium" to FontWeight.Medium, "inter_bold" to FontWeight.Bold),
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
    fun `a song printed to a PDF and imported back keeps its title, sections, lyrics, and chords`() = runBlocking {
        val failures = corpus.flatMap { (name, text) ->
            listOf(PrintSettings(), PrintSettings(columns = 2), PrintSettings(columns = 2, isLandscape = true)).mapNotNull { settings ->
                val original = songRenderer.transposedSong(text, transposition = 0, spelling = UserPreferences.ChordSpelling.Default)
                val expected = summary(original)
                val actual = summary(roundTrip(original, settings))
                if (expected == actual) null else "$name in ${settings.columns} columns${if (settings.isLandscape) " landscape" else ""}:\n  expected $expected\n  actual   $actual"
            }
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n\n"))
    }

    @Test
    fun `a grid's label comes back as its label under a details row longer than the lyrics`() = runBlocking {
        val original = songRenderer.transposedSong(corpus.getValue("05-grid.cho"), transposition = 0, spelling = UserPreferences.ChordSpelling.Default)
        val imported = roundTrip(original, PrintSettings(), printLabelsOf(File("src/commonMain/composeResources/values-de")))
        val sections = imported.blocks.filterIsInstance<ChordProBlock.Section>()
        val text = sections.flatMap { section -> section.lines.filterIsInstance<ChordProLine.Lyrics>().map { it.text } }
        assertEquals(emptyList(), text.filter { "4x4" in it }, imported.toString())
        assertTrue(sections.none { it.type == SectionType.Paragraph }, imported.toString())
    }

    @Test
    fun `a title and a lyric whose words are chord names by their text alone come back as they were`() = runBlocking {
        val song = ChordProParser.parse("{title: A}\n{start_of_verse}\n[C]Hello a b c d\na b c d\n{end_of_verse}\n")
        assertEquals(summary(song), summary(roundTrip(song, PrintSettings())))
    }

    @Test
    fun `chords a narrow column overflows onto rows of their own come back on their line in every column count`() = runBlocking {
        // Where the chord-only line fits a row of its own (one or two columns), it is a chord row over an unmarked lyric,
        // which the import merges into that lyric: nothing tells the two apart, so only the overflowing prints are compared.
        val songs = listOf(
            "I [Cmaj7(#11)] [Bbmaj9/D] [Gm7b5]" to 1..PrintSettings.MAX_COLUMNS,
            "[Cmaj7(#11)] [Bbmaj9/D] [Gm7b5] [Am7add11]\nHello world" to 3..PrintSettings.MAX_COLUMNS,
        ).map { (text, columns) -> ChordProParser.parse("{title: Song}\n{start_of_verse}\n$text\n{end_of_verse}\n") to columns }
        val failures = songs.flatMap { (song, columnCounts) ->
            columnCounts.mapNotNull { columns ->
                val expected = summary(song)
                val actual = summary(roundTrip(song, PrintSettings(columns = columns)))
                if (expected == actual) null else "$columns columns:\n  expected $expected\n  actual   $actual"
            }
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n"))
    }

    @Test
    fun `the playing details row comes back in every language the app prints it in`() = runBlocking {
        val song = ChordProParser.parse(
            "{title: Song}\n{artist: Someone}\n{key: F#m}\n{capo: 2}\n{tempo: 96}\n{time: 3/4}\n{start_of_verse}\n[Em]Hello [C]world\n{end_of_verse}\n",
        )
        val failures = File("src/commonMain/composeResources").listFiles().orEmpty().filter { it.name.startsWith("values") }.sortedBy { it.name }
            .mapNotNull { folder ->
                val metadata = roundTrip(song, PrintSettings(), printLabelsOf(folder), transposition = 2).metadata
                val actual = listOf(metadata.key, metadata.capo?.toString(), metadata.tempo, metadata.time)
                if (actual == listOf("F#m", "2", "96", "3/4")) null else "${folder.name}: $actual"
            }
        assertEquals(emptyList(), failures, failures.joinToString("\n"))
    }

    @Test
    fun `a capo song printed with either chord names comes back sounding as printed in every language and column count`() = runBlocking {
        val verse = "{start_of_verse}\n%s\n{end_of_verse}\n"
        val shapes = ChordProParser.parse("{title: Song}\n{key: F#m}\n{capo: 2}\n" + verse.format("[Em]Hello [Am]world [Em]and [D]more [Em]here"))
        val sounding = ChordProParser.parse("{title: Song}\n{key: F#m}\n{capo: 2}\n" + verse.format("[F#m]Hello [Bm]world [F#m]and [E]more [F#m]here"))
        val german = UserPreferences.ChordSpelling.Default.copy(notation = UserPreferences.Notation.GERMAN)
        val latin = UserPreferences.ChordSpelling.Default.copy(notation = UserPreferences.Notation.LATIN)
        val folders = File("src/commonMain/composeResources").listFiles().orEmpty().filter { it.name.startsWith("values") }.sortedBy { it.name }
        // A narrow column wraps the capo's value, the sounding marker of every language in four of them, and the verse's
        // line, whose chord row leaves a gap wider than a gutter beside the lyric and the capo's value crossing it.
        val prints = folders.flatMap { folder ->
            val labels = printLabelsOf(folder)
            (1..PrintSettings.MAX_COLUMNS).flatMap { columns ->
                val settings = PrintSettings(columns = columns)
                val name = "${folder.name} in $columns columns"
                listOf(
                    "$name shapes" to roundTrip(shapes, settings, labels, shapesKey = "Em"),
                    "$name sounding" to roundTrip(sounding, settings, labels, areChordsSounding = true),
                ) + when (folder.name) {
                    "values-es" -> listOf("$name sounding in Latin" to roundTrip(sounding, settings, labels, areChordsSounding = true, spelling = latin))
                    "values-de" -> listOf("$name sounding in German" to roundTrip(sounding, settings, labels, areChordsSounding = true, spelling = german))
                    else -> emptyList()
                }
            }
        }
        // What sounds is told by the chords and the capo alone: the shapes print brings the shapes' key back and the
        // sounding print the key it moves its chords to, so the chords' first one, moved up by the capo, is what is
        // compared. The lyrics are the verse's line alone, with nothing of the details row read as one.
        val failures = prints.mapNotNull { (name, imported) ->
            val capo = imported.metadata.capo ?: 0
            val moved = ChordProTransposer.transpose(imported, capo, preferFlats = false)
            val lyrics = moved.blocks.filterIsInstance<ChordProBlock.Section>().flatMap { section -> section.lines.filterIsInstance<ChordProLine.Lyrics>() }
            val heard = lyrics.firstNotNullOfOrNull { it.chords.firstOrNull()?.name }
            val words = lyrics.map { it.text.replace(Regex("\\s+"), " ").trim() }
            if (capo == 2 && heard == "F#m" && words == listOf("Hello world and more here")) null else "$name: capo $capo, sounds $heard, lyrics $words"
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n"))
    }

    @Test
    fun `a capo song printed with its shapes' key comes back keyed by its shapes in every language and column count`() = runBlocking {
        val song = ChordProParser.parse("{title: Song}\n{key: F#m}\n{capo: 2}\n{start_of_verse}\n[C]Hello [G]world [D]and [Em]more\n{end_of_verse}\n")
        val folders = File("src/commonMain/composeResources").listFiles().orEmpty().filter { it.name.startsWith("values") }.sortedBy { it.name }
        val failures = folders.flatMap { folder ->
            val labels = printLabelsOf(folder)
            (1..PrintSettings.MAX_COLUMNS).mapNotNull { columns ->
                val imported = roundTrip(song, PrintSettings(columns = columns), labels, shapesKey = "Em")
                val actual = listOf(imported.metadata.key, imported.metadata.capo?.toString(), SongKeys.ofChords(imported))
                if (actual == listOf("Em", "2", "Em")) null else "${folder.name} in $columns columns: $actual"
            }
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n"))
    }

    @Test
    fun `a title and a lyric cut inside a word by a narrow column come back whole`() = runBlocking {
        val lyric = "[F]Another rather lengthy line, with [C]commas, and words like extraordinarily [G]and incomprehensibilities, to [C]end"
        val failures = listOf("\u00c1rv\u00edzt\u0171r\u0151 t\u00fck\u00f6rf\u00far\u00f3g\u00e9p", "Pasted\u00a0Spaces").mapNotNull { title ->
            val song = ChordProParser.parse("{title: $title}\n{start_of_verse}\n$lyric\n{end_of_verse}\n")
            val expected = summary(song)
            val actual = summary(roundTrip(song, PrintSettings(columns = PrintSettings.MAX_COLUMNS)))
            if (expected == actual) null else "expected $expected\n  actual   $actual"
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n"))
    }

    @Test
    fun `a playing details row cut inside a label by a narrow column comes back in every language`() = runBlocking {
        val song = ChordProParser.parse("{title: Song}\n{key: F#m}\n{capo: 2}\n{start_of_verse}\n[Em]Hello [Am]world [Em]and [D]more [Em]here\n{end_of_verse}\n")
        val settings = PrintSettings(columns = PrintSettings.MAX_COLUMNS, fontSize = 20, marginMm = 25)
        val folders = File("src/commonMain/composeResources").listFiles().orEmpty().filter { it.name.startsWith("values") }.sortedBy { it.name }
        val failures = folders.mapNotNull { folder ->
            val imported = roundTrip(song, settings, printLabelsOf(folder), transposition = 2, shapesKey = "Em")
            val lyrics = imported.blocks.filterIsInstance<ChordProBlock.Section>().flatMap { section -> section.lines.filterIsInstance<ChordProLine.Lyrics>() }
            // The page count is left out of what is compared: whether a wide margin keeps it out of the lyrics is the PDF
            // text extraction's to tell, and this is about the details row alone.
            val words = lyrics.map { it.text.replace(Regex("\\s+"), " ").trim() }.filterNot { Regex("[0-9]+ / [0-9]+").matches(it) }
            val actual = listOf(imported.metadata.key, imported.metadata.capo?.toString()) + words
            if (actual == listOf("Em", "2", "Hello world and more here")) null else "${folder.name}: $actual"
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n"))
    }

    /** The labels the export prints in the language of the `values*` [folder], read from its `strings.xml` as the app reads them. */
    private fun printLabelsOf(folder: File): PrintLabels {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(folder, "strings.xml"))
        val strings = document.getElementsByTagName("string").let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }
            .associate { it.getAttribute("name") to it.textContent.replace("\\'", "'") }
        return labels.copy(
            key = strings.getValue("song_editor_insert_key"),
            transposition = strings.getValue("song_details_transposition"),
            capo = strings.getValue("song_editor_insert_capo"),
            tempo = strings.getValue("song_editor_insert_tempo"),
            tempoValue = strings.getValue("song_details_tempo").replace("%1\$s", TEMPO_VALUE),
            time = strings.getValue("print_time"),
            labelledValue = strings.getValue("print_label_value").replace("%1\$s", LABELLED_LABEL).replace("%2\$s", LABELLED_VALUE),
            capoWithShapes = strings.getValue("song_details_capo_shapes").replace("%1\$s", LABELLED_VALUE).replace("%2\$s", SHAPES_KEY),
            capoSounding = strings.getValue("print_capo_sounding").replace("%1\$s", LABELLED_VALUE),
        )
    }

    private suspend fun roundTrip(
        song: ChordProSong,
        settings: PrintSettings,
        labels: PrintLabels = this.labels,
        transposition: Int = 0,
        shapesKey: String? = null,
        areChordsSounding: Boolean = false,
        spelling: UserPreferences.ChordSpelling = UserPreferences.ChordSpelling.Default,
    ): ChordProSong {
        val printed = songRenderer.notatedSong(song, spelling)
        val title = song.metadata.title.orEmpty()
        val entry = PrintSong(
            "song.cho", title, song.metadata.artist, transposition = transposition, shapesKey = shapesKey, areChordsSounding = areChordsSounding,
            song = printed,
        )
        val document = layoutPrintDocument(
            PrintSource(title, songs = listOf(entry)),
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

    /**
     * The faces the export prints in, read from the files the app bundles: the system's own faces, which a renderer
     * built without them falls back on, differ from one operating system to the next, and so would what is tested.
     */
    private fun bundledFontFamily(vararg faces: Pair<String, FontWeight>) = FontFamily(
        faces.map { (name, weight) -> Font(identity = name, data = File("src/commonMain/composeResources/font/$name.ttf").readBytes(), weight = weight) },
    )
}
