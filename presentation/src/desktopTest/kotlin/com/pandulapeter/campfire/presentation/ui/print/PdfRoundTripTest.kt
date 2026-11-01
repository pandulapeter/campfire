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
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.chordpro.model.SectionType
import com.pandulapeter.campfire.chordpro.scales.SongKeys
import com.pandulapeter.campfire.chordpro.syntax.ChordProMarkup
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

    /** Sizes across the export's range, where a rounding or a fallback font's taller line box may cross a threshold. */
    private val fontSizes = listOf(8, 11, 15, 20)

    /** Every column count at each of [fontSizes]. */
    private val sizedPrints = (1..PrintSettings.MAX_COLUMNS).flatMap { columns -> fontSizes.map { PrintSettings(columns = columns, fontSize = it) } }

    /**
     * [text] without what the export prints but the import does not read back as it was: a text block, whose prose comes
     * back as lyrics, and a bracket naming no chord (`[x]`), which is printed in bold like a chord but not taken for one.
     */
    private fun printable(text: String) = text
        .replace(Regex("\\{start_of_textblock\\}.*?\\{end_of_textblock\\}\n", RegexOption.DOT_MATCHES_ALL), "")
        .replace("[x]", "")

    @Test
    fun `a song printed to a PDF and imported back keeps its title, sections, lyrics, and chords in every column count`() = runBlocking {
        val prints = (1..PrintSettings.MAX_COLUMNS).flatMap { columns -> listOf(PrintSettings(columns = columns), PrintSettings(columns = columns, isLandscape = true)) }
        val failures = corpus.flatMap { (name, text) ->
            prints.mapNotNull { settings ->
                val original = songRenderer.transposedSong(text, transposition = 0, spelling = UserPreferences.ChordSpelling.Default)
                val expected = summary(original) + keyChangesOf(original)
                val imported = roundTripText(original, settings)
                val actual = summary(ChordProParser.parse(imported)) + keyChangesOf(imported)
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
        fontSizes.forEach { fontSize -> assertEquals(summary(song), summary(roundTrip(song, PrintSettings(fontSize = fontSize))), "$fontSize points") }
    }

    @Test
    fun `chords a narrow column overflows onto rows of their own come back on their line in every column count`() = runBlocking {
        val songs = listOf(
            "I [Cmaj7(#11)] [Bbmaj9/D] [Gm7b5]",
            "[Cmaj7(#11)] [Bbmaj9/D] [Gm7b5] [Am7add11]\nHello world",
        ).map { ChordProParser.parse("{title: Song}\n{start_of_verse}\n$it\n{end_of_verse}\n") }
        val failures = songs.flatMap { song ->
            sizedPrints.mapNotNull { settings ->
                val expected = summary(song)
                val actual = summary(roundTrip(song, settings))
                if (expected == actual) null else "${settings.columns} columns at ${settings.fontSize} points:\n  expected $expected\n  actual   $actual"
            }
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n"))
    }

    @Test
    fun `a line of chords alone over a line of no chords comes back as the two lines in every column count`() = runBlocking {
        val songs = listOf(
            "{title: Song}\n{start_of_verse}\n[G] [C]\nHello world\n{end_of_verse}\n",
            "{title: Song}\n{start_of_verse}\n[G]\nHello world\n[C]Sing [G]along\n{end_of_verse}\n",
            "{title: \u05e9\u05d9\u05e8}\n{start_of_verse}\n[G] [C]\n\u05e9\u05dc\u05d5\u05dd \u05e2\u05d5\u05dc\u05dd\n{end_of_verse}\n",
        ).map(ChordProParser::parse)
        val failures = songs.flatMap { song ->
            sizedPrints.mapNotNull { settings ->
                val expected = summary(song)
                val actual = summary(roundTrip(song, settings))
                if (expected == actual) null else "${settings.columns} columns at ${settings.fontSize} points:\n  expected $expected\n  actual   $actual"
            }
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n"))
    }

    @Test
    fun `an artist comes back whole however long and however wrapped, and a first lyric is never taken for one`() = runBlocking {
        val songs = listOf(
            "{title: Wagon Wheel}\n{artist: Old Crow Medicine Show}\n{start_of_verse}\n[A]Headed down [E]south\n{end_of_verse}\n",
            "{title: Song}\n{artist: The Chords}\n{start_of_verse}\n[C]Hello [G]world\n{end_of_verse}\n",
            "{title: A rather long title}\nHi there\nAnd [C]more of it here\n",
        ).map(ChordProParser::parse)
        val failures = songs.flatMap { song ->
            sizedPrints.mapNotNull { settings ->
                val expected = summary(song)
                val actual = summary(roundTrip(song, settings))
                if (expected == actual) null else "${settings.columns} columns at ${settings.fontSize} points:\n  expected $expected\n  actual   $actual"
            }
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n"))
    }

    @Test
    fun `a chord row opening with an annotation comes back over its lyric in every column count`() = runBlocking {
        val song = ChordProParser.parse("{title: Song}\n{start_of_chorus}\n[*Everybody] [D7]Home, [G]home on the [C]range\n{end_of_chorus}\n")
        val failures = sizedPrints.flatMap { listOf(it, it.copy(isLandscape = true)) }
            .mapNotNull { settings ->
                val expected = summary(song)
                val actual = summary(roundTrip(song, settings))
                if (expected == actual) null else "${settings.columns} columns${if (settings.isLandscape) " landscape" else ""} at ${settings.fontSize} points: $actual"
            }
        assertEquals(emptyList(), failures, failures.joinToString("\n"))
    }

    @Test
    fun `a right-to-left song wrapped by a narrow column comes back with each chord on its letter and each line whole`() = runBlocking {
        val failures = listOf("60-generated-rtl-10.cho", "60-generated-rtl-15.cho").flatMap { name ->
            val original = songRenderer.transposedSong(corpus.getValue(name), transposition = 0, spelling = UserPreferences.ChordSpelling.Default)
            fontSizes.mapNotNull { fontSize ->
                val expected = summary(original)
                val actual = summary(roundTrip(original, PrintSettings(columns = PrintSettings.MAX_COLUMNS, fontSize = fontSize)))
                if (expected == actual) null else "$name at $fontSize points:\n  expected $expected\n  actual   $actual"
            }
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n\n"))
    }

    @Test
    fun `a right-to-left song's chords a narrow column overflows onto rows of their own come back on their line`() = runBlocking {
        val song = ChordProParser.parse(
            "{title: \u05e9\u05d9\u05e8}\n{start_of_verse}\n[Cmaj7(#11)] [Bbmaj9/D] [Gm7b5] [Am7add11]\n\u05e9\u05dc\u05d5\u05dd \u05e2\u05d5\u05dc\u05dd\n{end_of_verse}\n",
        )
        val failures = sizedPrints.mapNotNull { settings ->
            val expected = summary(song)
            val actual = summary(roundTrip(song, settings))
            if (expected == actual) null else "${settings.columns} columns at ${settings.fontSize} points:\n  expected $expected\n  actual   $actual"
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n"))
    }

    @Test
    fun `chords after a right-to-left lyric's end come back after it, in their order, in every column count`() = runBlocking {
        val songs = listOf(
            "\u05e9\u05dc\u05d5\u05dd \u05e2\u05d5\u05dc\u05dd [C]",
            "\u05e9\u05dc\u05d5\u05dd [C] [G] [Am]",
            "\u05e9\u05dc\u05d5\u05dd [Cmaj7(#11)] [Bbmaj9/D] [Gm7b5]",
        ).map { ChordProParser.parse("{title: \u05e9\u05d9\u05e8}\n{start_of_verse}\n$it\n{end_of_verse}\n") }
        val failures = songs.flatMap { song ->
            (1..PrintSettings.MAX_COLUMNS).mapNotNull { columns ->
                val expected = summary(song)
                val actual = summary(roundTrip(song, PrintSettings(columns = columns)))
                if (expected == actual) null else "$columns columns:\n  expected $expected\n  actual   $actual"
            }
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n"))
    }

    @Test
    fun `a right-to-left song's row of chords alone comes back in its order`() = runBlocking {
        val song = ChordProParser.parse(
            "{title: \u05e9\u05d9\u05e8}\n{start_of_verse}\n[C]\u05e9\u05dc\u05d5\u05dd [G]\u05e2\u05d5\u05dc\u05dd\n{end_of_verse}\n" +
                "{start_of_verse}\n[C] [G] [Am]\n{end_of_verse}\n",
        )
        fontSizes.forEach { fontSize -> assertEquals(summary(song), summary(roundTrip(song, PrintSettings(fontSize = fontSize))), "$fontSize points") }
    }

    @Test
    fun `a boxed comment and a comment come back as they were in either direction at every text size`() = runBlocking {
        val songs = listOf(
            "{title: Song}\n{start_of_verse}\n[C]Hello world\n{comment_box: Slowly}\n[G]Sing along\n{comment: x2}\n{end_of_verse}\n",
            "{title: \u05e9\u05d9\u05e8}\n{start_of_verse}\n[Am]\u05e9\u05dc\u05d5\u05dd \u05e2\u05d5\u05dc\u05dd\n{comment_box: \u05dc\u05d0\u05d8}\n" +
                "[G]\u05d0\u05d1\u05d2\n{comment: x2}\n{end_of_verse}\n",
        ).map(ChordProParser::parse)
        val failures = songs.flatMap { song ->
            (8..20).mapNotNull { fontSize ->
                val expected = summary(song)
                val actual = summary(roundTrip(song, PrintSettings(fontSize = fontSize)))
                if (expected == actual) null else "$fontSize points:\n  expected $expected\n  actual   $actual"
            }
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n"))
    }

    @Test
    fun `a song's side is told by the edge most of its lines share`() = runBlocking {
        val choruses = ChordProParser.parse(
            "{title: Truck Driver Change}\n{key: C}\n{start_of_chorus}\n[C]Love is [F]all a[G]round\n{end_of_chorus}\n" +
                "{start_of_chorus}\n[D]Love is [G]all a[A]round\n{end_of_chorus}\n",
        )
        // The details row alone stands at the margin, beside lyrics set behind the chorus bar under the Italian label.
        val italian = printLabelsOf(File("src/commonMain/composeResources/values-it")).let { it.copy(sections = it.sections.copy(chorus = "Ritornello")) }
        val hebrew = songRenderer.transposedSong(corpus.getValue("17-hebrew-mixed.cho"), transposition = 0, spelling = UserPreferences.ChordSpelling.Default)
        val romanian = printLabelsOf(File("src/commonMain/composeResources/values-ro"))
        val prints = (1..PrintSettings.MAX_COLUMNS).map { columns -> Triple("Truck Driver Change in $columns columns", choruses, roundTrip(choruses, PrintSettings(columns = columns), italian)) } +
            Triple("17-hebrew-mixed", hebrew, roundTrip(hebrew, PrintSettings(columns = PrintSettings.MAX_COLUMNS), romanian))
        val failures = prints.mapNotNull { (name, song, imported) ->
            val expected = summary(song)
            val actual = summary(imported)
            if (expected == actual) null else "$name:\n  expected $expected\n  actual   $actual"
        }
        assertEquals(emptyList(), failures, failures.joinToString("\n\n"))
    }

    @Test
    fun `a sounding capo chart changing key comes back with each part spelled by its own key`() = runBlocking {
        val text = corpus.getValue("09-capo-transpose.cho")
        val sounding = songRenderer.transposedSong(text, transposition = 5, spelling = UserPreferences.ChordSpelling.Default)
        val expected = summary(songRenderer.transposedSong(text, transposition = 0, spelling = UserPreferences.ChordSpelling.Default))
        assertEquals(expected, summary(roundTrip(sounding, PrintSettings(), areChordsSounding = true)))
    }

    @Test
    fun `a key change comes back where it stands in every language, wrapped or not`() = runBlocking {
        val text = "{title: Song}\n{key: C}\n{start_of_verse}\n[C]Hello [G]world\n{end_of_verse}\n{transpose: 1}\n{start_of_chorus}\n[C]Sing [G]along\n{end_of_chorus}\n"
        val song = songRenderer.transposedSong(text, transposition = 0, spelling = UserPreferences.ChordSpelling.Default)
        val settings = PrintSettings(columns = PrintSettings.MAX_COLUMNS, fontSize = 20, marginMm = 25)
        val folders = File("src/commonMain/composeResources").listFiles().orEmpty().filter { it.name.startsWith("values") }.sortedBy { it.name }
        val failures = folders.mapNotNull { folder ->
            val imported = roundTripText(song, settings, printLabelsOf(folder))
            val lyrics = ChordProParser.parse(imported).blocks.filterIsInstance<ChordProBlock.Section>()
                .flatMap { section -> section.lines.filterIsInstance<ChordProLine.Lyrics>() }
            val words = lyrics.map { it.text.replace(Regex("\\s+"), " ").trim() }
            val isInPlace = Regex("\\{key: Db\\}\\n+\\{start_of_chorus").containsMatchIn(imported)
            if (isInPlace && words == listOf("Hello world", "Sing along")) null else "${folder.name}: $imported"
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
    fun `a comment cut inside a word by a narrow column comes back whole`() = runBlocking {
        val song = ChordProParser.parse("{title: Song}\n{start_of_verse}\n[C]Hello\n{comment: Play incomprehensibilities extraordinarily}\n[G]World\n{end_of_verse}\n")
        val imported = roundTripText(song, PrintSettings(columns = PrintSettings.MAX_COLUMNS))
        assertTrue("{comment: Play incomprehensibilities extraordinarily}\n[G]World" in imported, imported)
    }

    @Test
    fun `a playing details row cut inside a label by a narrow column comes back in every language`() = runBlocking {
        val song = ChordProParser.parse("{title: Song}\n{key: F#m}\n{capo: 2}\n{start_of_verse}\n[Em]Hello [Am]world [Em]and [D]more [Em]here\n{end_of_verse}\n")
        val settings = PrintSettings(columns = PrintSettings.MAX_COLUMNS, fontSize = 20, marginMm = 25)
        val folders = File("src/commonMain/composeResources").listFiles().orEmpty().filter { it.name.startsWith("values") }.sortedBy { it.name }
        val failures = folders.mapNotNull { folder ->
            val imported = roundTrip(song, settings, printLabelsOf(folder), transposition = 2, shapesKey = "Em")
            val lyrics = imported.blocks.filterIsInstance<ChordProBlock.Section>().flatMap { section -> section.lines.filterIsInstance<ChordProLine.Lyrics>() }
            val words = lyrics.map { it.text.replace(Regex("\\s+"), " ").trim() }
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
    ): ChordProSong = ChordProParser.parse(roundTripText(song, settings, labels, transposition, shapesKey, areChordsSounding, spelling))

    /** [song] printed as the export prints it and read back as the import reads it, as the file it imports. */
    private suspend fun roundTripText(
        song: ChordProSong,
        settings: PrintSettings,
        labels: PrintLabels = this.labels,
        transposition: Int = 0,
        shapesKey: String? = null,
        areChordsSounding: Boolean = false,
        spelling: UserPreferences.ChordSpelling = UserPreferences.ChordSpelling.Default,
    ): String {
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
        return ChordSheetConverter.convert(extracted.toChordSheet()).single()
    }

    /**
     * What the round trip promises to keep: the title, the artist, how the song is played, and in their order each
     * section's kind with its lyrics and their chords, written inline, whether it holds a tab or a grid, and each comment
     * with whether it is boxed (a plain and an italic comment print alike). Comments of one kind printed one under another
     * are one text, since the export does not mark the rows of a comment wrapped at a space, which the import joins. A section of no lyrics, tab, or grid (a tune)
     * is left out, and so is the spacing around a chord past a line's end and after a chord, which the export's padding
     * leaves undecided. Key changes are [keyChangesOf]'s, since the import writes them as `{key}` lines, which the parser
     * reads past.
     */
    private fun summary(song: ChordProSong) = with(song.metadata) {
        listOf(title, artist, key, capo?.toString(), tempo, time).map { it?.replace('\u00A0', ' ') }
    } + song.blocks.flatMap(::summaryOf).fold(emptyList<String>()) { summary, item ->
        val last = summary.lastOrNull()
        val kind = item.substringBefore(": ", "")
        if (kind in commentKinds && last?.substringBefore(": ", "") == kind) summary.dropLast(1) + "$last ${item.substringAfter(": ")}" else summary + item
    }

    private val commentKinds = setOf("comment", "box")

    private fun summaryOf(block: ChordProBlock): List<String> = when (block) {
        is ChordProBlock.Section -> listOfNotNull(
            lyricsOf(block),
            "tab".takeIf { block.lines.any { it is ChordProLine.Tab } },
            "grid".takeIf { block.lines.any { it is ChordProLine.Grid } },
        )
        is ChordProBlock.ChorusRecall -> block.blocks.flatMap(::summaryOf)
        is ChordProBlock.Comment -> listOf((if (block.style == CommentStyle.BOX) "box: " else "comment: ") + ChordProMarkup.strip(block.text).text.trim())
        else -> emptyList()
    }

    private fun lyricsOf(section: ChordProBlock.Section) = section.lines.filterIsInstance<ChordProLine.Lyrics>().takeIf { it.isNotEmpty() }?.let { lines ->
        section.type.toString().replace('_', '-') + ": " + lines.joinToString(" / ") { line ->
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

    /** The keys [song] changes to further down, the key in force named again left out, as the export prints them. */
    private fun keyChangesOf(song: ChordProSong): List<String> {
        var inForce = song.metadata.key
        return song.blocks.filterIsInstance<ChordProBlock.Transpose>().mapNotNull { change ->
            change.key?.takeIf { it.isNotBlank() && it != inForce }?.also { inForce = it }
        }
    }

    /** The keys an imported [text] changes to further down: its `{key}` lines after the header's. */
    private fun keyChangesOf(text: String): List<String> {
        val body = text.lines().dropWhile { !it.startsWith("{start_of_") && !it.startsWith("{chorus") && !it.startsWith("{comment") }
        return body.mapNotNull { line -> keyLine.matchEntire(line)?.groupValues?.get(1) }
    }

    private val keyLine = Regex("\\{key: (.*)\\}")

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
