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

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.data.model.domain.PrintSettings
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Inflater
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/** Exercises real shaping, canvas rasterization and PDF encoding, including Hungarian and musical symbols. */
internal class PrintRendererTest {
    @Test fun selectableRectanglesComeFromTheSameShapingAsThePageImage() = runBlocking {
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        val renderer = PrintRenderer(measurer)
        val content = "Árvíztűrő e\u0301 \uD83D\uDE42 ♯ ♭ \u200Bpadding\u00A0"
        val page = PrintPage(listOf(PrintText(content, 23f, 31f, PrintStyle(14)),
            PrintText("Am/G", 78f, 10f, PrintStyle(14, bold = true))))
        val glyphs = renderer.selectableText(page)
        assertEquals(content.replace("\u200B", "").replace('\u00A0', ' ') + "Am/G", glyphs.joinToString("") { it.text })
        assertTrue(glyphs.all { it.width > 0 && it.height > 0 })
        assertTrue(glyphs.none { it.text.length == 1 && it.text.single().isSurrogate() })
        val textStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp,
            fontWeight = FontWeight.Normal, fontStyle = FontStyle.Normal, fontFeatureSettings = "liga=0")
        val expected = measurer.measure(content, textStyle,
            softWrap = false, density = Density(1f), layoutDirection = LayoutDirection.Ltr).getBoundingBox(0)
        // Fallback font metrics can put the rectangle above the text's origin, notably on Linux with emoji.
        // Selection must follow the actual shaping, including that offset, rather than assume the origin is a corner.
        val first = glyphs.first()
        assertEquals(23f + expected.left, first.x, "First glyph's horizontal placement")
        assertEquals(31f + expected.top, first.y, "First glyph's vertical placement")
        assertEquals(expected.width, first.width, "First glyph's width")
        assertEquals(expected.height, first.height, "First glyph's height")
        val expectedBold = measurer.measure("Am/G", textStyle.copy(fontWeight = FontWeight.Bold),
            softWrap = false, density = Density(1f), layoutDirection = LayoutDirection.Ltr).getBoundingBox(0)
        val bold = glyphs.first { it.style.bold }
        assertEquals(78f + expectedBold.left, bold.x, "Bold glyph's horizontal placement")
        assertEquals(10f + expectedBold.top, bold.y, "Bold glyph's vertical placement")
        assertEquals(expectedBold.width, bold.width, "Bold glyph's width")
        assertEquals(expectedBold.height, bold.height, "Bold glyph's height")
        System.getenv("CAMPFIRE_PRINT_QA_DIR")?.let { directory ->
            File(directory).mkdirs()
            File(directory, "unicode.pdf").writeBytes(renderer.pdf(PrintDocument(300f, 120f, listOf(page)), "Unicode"))
        }
        Unit
    }

    @Test fun exportsAPositionedSongForTheImporterRegressionFixture() = runBlocking {
        val renderer = PrintRenderer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr))
        val parsed = ChordProParser.parse("""
            {title: Árvíztűrő dal}
            {artist: Péter}
            {key: Am}
            {start_of_verse: Verse 1}
            [Am]Őrizzük a [F]dalt!
            [C]Words and [G/B]chords.
            {end_of_verse}
        """.trimIndent())
        val document = layoutPrintDocument(PrintSource("Árvíztűrő dal", songs = listOf(
            PrintSong("round-trip.cho", "Árvíztűrő dal", "Péter", song = parsed))),
            PrintSettings(columns = 2), PrintLabels("Key", "Transposition", "Capo", "Tempo", "$TEMPO_VALUE BPM", "Time", "Missing",
                DefaultSectionLabels(verse = "Verse", chorus = "Chorus", bridge = "Bridge", tab = "Tab", grid = "Grid",
                    intro = "Intro", preChorus = "Pre-chorus", solo = "Solo", outro = "Outro")), renderer::width)
        val glyphs = renderer.selectableText(document.pages.single())
        assertTrue(glyphs.any { it.style.bold })
        val lyric = assertNotNull(document.pages.single().texts.firstOrNull { it.text.startsWith("Őrizzük") })
        val chord = document.pages.single().texts.first { it.text == "Am" }
        assertEquals(lyric.x, chord.x)
        assertTrue(chord.y < lyric.y)
        val bytes = renderer.pdf(document, "Árvíztűrő dal")
        assertTrue(bytes.decodeToString().contains("/ToUnicode"))
        System.getenv("CAMPFIRE_PRINT_QA_DIR")?.let { directory ->
            File(directory).mkdirs()
            File(directory, "campfire.pdf").writeBytes(bytes)
        }
        Unit
    }

    @Test fun exportsBothColumnsInSongReadingOrder() = runBlocking {
        val renderer = PrintRenderer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr))
        val parsed = ChordProParser.parse("{title: Column song}\n{start_of_verse: Verse 1}\n" +
            (1..30).joinToString("\n") { "[Am]Line ${it.toString().padStart(2, '0')} singing [F]together." } + "\n{end_of_verse}")
        val document = layoutPrintDocument(PrintSource("Column song", songs = listOf(
            PrintSong("columns.cho", "Column song", null, song = parsed))), PrintSettings(columns = 2),
            PrintLabels("Key", "Transposition", "Capo", "Tempo", "$TEMPO_VALUE BPM", "Time", "Missing", DefaultSectionLabels(verse = "Verse", chorus = "Chorus",
                bridge = "Bridge", tab = "Tab", grid = "Grid", intro = "Intro", preChorus = "Pre-chorus", solo = "Solo", outro = "Outro")), renderer::width)
        val page = document.pages.single()
        assertTrue(page.texts.first { it.text.startsWith("Line 01") }.x < document.width / 2)
        assertTrue(page.texts.first { it.text.startsWith("Line 30") }.x > document.width / 2)
        val lines = renderer.selectableText(page).joinToString("") { it.text }
        assertTrue(lines.indexOf("Line 01") < lines.indexOf("Line 30"))
        val bytes = renderer.pdf(document, "Column song")
        val content = contentStreams(bytes).single().let(::inflate).decodeToString()
        assertEquals(63, Regex("] TJ").findAll(content).count(), "Each chord row and lyric row must be one text object, not isolated letters.")
        System.getenv("CAMPFIRE_PRINT_QA_DIR")?.let { directory ->
            File(directory).mkdirs()
            File(directory, "campfire-columns.pdf").writeBytes(bytes)
        }
        Unit
    }

    @Test fun exportsEveryColumnOfAFullPageInSongReadingOrder() = runBlocking {
        val renderer = PrintRenderer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr))
        // Short pairs that never wrap, as many as leave the last column with only a few of them (a single one in the
        // three columns), the way the page a song ends on does.
        listOf(Triple(3, true, 28), Triple(4, false, 68), Triple(4, true, 44)).forEach { (columns, isLandscape, lineCount) ->
            val name = "campfire-columns-$columns${if (isLandscape) "-landscape" else ""}"
            val parsed = ChordProParser.parse("{title: Column song}\n{start_of_verse: Verse 1}\n" +
                (1..lineCount).joinToString("\n") { "[Am]Line ${it.toString().padStart(3, '0')} [F]sung." } + "\n{end_of_verse}")
            val document = layoutPrintDocument(PrintSource("Column song", songs = listOf(
                PrintSong("$name.cho", "Column song", null, song = parsed))), PrintSettings(isLandscape = isLandscape, columns = columns),
                PrintLabels("Key", "Transposition", "Capo", "Tempo", "$TEMPO_VALUE BPM", "Time", "Missing", DefaultSectionLabels(verse = "Verse", chorus = "Chorus",
                    bridge = "Bridge", tab = "Tab", grid = "Grid", intro = "Intro", preChorus = "Pre-chorus", solo = "Solo", outro = "Outro")), renderer::width)
            val page = document.pages.single()
            assertEquals(columns, page.texts.filter { it.text.startsWith("Line ") }.map { it.x }.distinct().size, name)
            val bytes = renderer.pdf(document, "Column song")
            System.getenv("CAMPFIRE_PRINT_QA_DIR")?.let { directory ->
                File(directory).mkdirs()
                File(directory, "$name.pdf").writeBytes(bytes)
            }
        }
    }

    @Test fun exportsFourColumnsOfTheLargestTextFilledToTheirEdges() = runBlocking {
        val renderer = PrintRenderer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr))
        val parsed = ChordProParser.parse("{title: Dense}\n{start_of_verse: Verse 1}\n" +
            (1..40).joinToString("\n") { "[Am]Line ${it.toString().padStart(3, '0')} singing all the [F]words of a long line together" } + "\n{end_of_verse}")
        val document = layoutPrintDocument(PrintSource("Dense", songs = listOf(PrintSong("dense.cho", "Dense", null, song = parsed))),
            PrintSettings(columns = 4, fontSize = 20, marginMm = 10),
            PrintLabels("Key", "Transposition", "Capo", "Tempo", "$TEMPO_VALUE BPM", "Time", "Missing", DefaultSectionLabels(verse = "Verse", chorus = "Chorus",
                bridge = "Bridge", tab = "Tab", grid = "Grid", intro = "Intro", preChorus = "Pre-chorus", solo = "Solo", outro = "Outro")), renderer::width)
        assertTrue(document.pages.size > 1)
        val bytes = renderer.pdf(document, "Dense")
        System.getenv("CAMPFIRE_PRINT_QA_DIR")?.let { directory ->
            File(directory).mkdirs()
            File(directory, "campfire-columns-dense.pdf").writeBytes(bytes)
        }
        Unit
    }

    @Test fun rendersAllPagesOfAMultilingualSetlist() = runBlocking {
        val renderer = PrintRenderer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr))
        val parsed = ChordProParser.parse("""
            {title: Árvíztűrő tükörfúrógép}
            {artist: Péter}
            {key: D}
            {capo: 2}
            {tempo: 96}
            {time: 3/4}
            {start_of_verse: Verse 1}
            [D]Words and [A7]chords stay together, ♯ and ♭.
            [Bm]Őrizzük a [G]dalt, éljen a zene!
            {end_of_verse}
            {comment: Play gently}
            {start_of_chorus: Chorus}
            [*Everybody][G]Sing along [D]under the stars.
            {end_of_chorus}
            {chorus}
            {start_of_tab: Guitar}
            e|--0--2--3--2--0--|
            B|--3--3--3--3--3--|
            G|--2--2--2--2--2--|
            D|--0--0--0--0--0--|
            {end_of_tab}
            {start_of_grid}
            | D . | G . | A7 . | D . |
            {end_of_grid}
        """.trimIndent())
        val first = PrintSong("one.cho", "Árvíztűrő tükörfúrógép", "Péter", index = 1, song = parsed)
        val second = first.copy(fileName = "two.cho", title = "Under the stars", index = 2,
            song = parsed.copy(blocks = parsed.blocks + List(90) { ChordProParser.parse("[D]Another line of music [A7]to keep on the next page.").blocks.single() }))
        val document = layoutPrintDocument(PrintSource("Campfire concert", "Rehearsal with friends", "2026-10-01", true, listOf(first, second)),
            PrintSettings(columns = 2), PrintLabels("Key", "Transposition", "Capo", "Tempo", "$TEMPO_VALUE BPM", "Time", "Missing", DefaultSectionLabels(verse = "Verse", chorus = "Chorus", bridge = "Bridge", tab = "Tab", grid = "Grid", intro = "Intro", preChorus = "Pre-chorus", solo = "Solo", outro = "Outro")), renderer::width)
        assertTrue(document.pages.size >= 4)
        val bytes = renderer.pdf(document, "Campfire concert")
        val contents = bytes.decodeToString()
        assertEquals(document.pages.size, Regex("/Subtype /Image").findAll(contents).count())
        assertTrue(contents.contains("/Count ${document.pages.size}"))
        assertTrue(bytes.size < document.pages.size * 120_000, "A page of print should compress to well under 120 KB.")
        val width = ceil(document.width * 3).toInt()
        val height = ceil(document.height * 3).toInt()
        imageStreams(bytes).forEach { assertEquals((width + 1) / 2 * height, inflate(it).size) }
        // Opt-in output for manual visual QA. Ordinary test runs leave no files behind.
        System.getenv("CAMPFIRE_PRINT_QA_DIR")?.let { directory ->
            File(directory).mkdirs()
            File(directory, "setlist.pdf").writeBytes(bytes)
        }
        Unit
    }

    @Test fun convertsPixelsToGrayByLuminance() {
        assertEquals(76, printGray(0xFFFF0000.toInt()))
        assertEquals(149, printGray(0xFF00FF00.toInt()))
        assertEquals(255, printGray(0xFFFFFFFF.toInt()))
        assertEquals(0, printGray(0xFF000000.toInt()))
    }

    @Test fun reusingThePageBitmapLeaksNothingBetweenPages() = runBlocking {
        val renderer = PrintRenderer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr))
        val top = PrintPage(listOf(PrintText(text = "MMMMMMMM", x = 10f, y = 5f, style = PrintStyle(20))))
        val bottom = PrintPage(listOf(PrintText(text = "MMMMMMMM", x = 10f, y = 70f, style = PrintStyle(20))))
        val bytes = renderer.pdf(PrintDocument(width = 100f, height = 100f, pages = listOf(top, bottom, top)), "Pages")
        val width = 300
        val pages = imageStreams(bytes).map { stream -> unpack(inflate(stream), width) }
        assertEquals(3, pages.size)
        fun inked(page: IntArray, rows: IntRange) = rows.any { row -> (0 until width).any { page[row * width + it] != 15 } }
        assertTrue(inked(pages[0], 0 until 120))
        assertTrue(!inked(pages[1], 0 until 120), "The second page must not show the first page's text.")
        assertTrue(inked(pages[1], 200 until 300))
        assertContentEquals(pages[0], pages[2])
    }

    private fun imageStreams(bytes: ByteArray): List<ByteArray> {
        val text = String(bytes, Charsets.ISO_8859_1)
        return Regex("""/Subtype /Image [^>]*/Length (\d+) >>\nstream\n""").findAll(text).map { match ->
            bytes.copyOfRange(match.range.last + 1, match.range.last + 1 + match.groupValues[1].toInt())
        }.toList()
    }

    private fun contentStreams(bytes: ByteArray): List<ByteArray> {
        val text = String(bytes, Charsets.ISO_8859_1)
        return Regex("""<< /Filter /FlateDecode /Length (\d+) >>\nstream\n""").findAll(text).map { match ->
            bytes.copyOfRange(match.range.last + 1, match.range.last + 1 + match.groupValues[1].toInt())
        }.toList()
    }

    private fun inflate(stream: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(stream)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            check(count > 0 || !inflater.needsInput()) { "The image stream ended early." }
            output.write(buffer, 0, count)
        }
        inflater.end()
        return output.toByteArray()
    }

    /** One four-bit level per pixel, 15 being white. */
    private fun unpack(packed: ByteArray, width: Int): IntArray {
        val rowBytes = (width + 1) / 2
        val height = packed.size / rowBytes
        return IntArray(width * height) { index ->
            val byte = packed[index / width * rowBytes + index % width / 2].toInt() and 255
            if (index % width % 2 == 0) byte shr 4 else byte and 15
        }
    }

    @Test fun detailsArePrintedGrayAndLyricsBlack() = runBlocking {
        val renderer = PrintRenderer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr))
        val song = ChordProParser.parse("{title: Gray}\n{key: D}\n{tempo: 96}\nWords that are sung in black")
        val document = layoutPrintDocument(PrintSource("Gray", songs = listOf(PrintSong("gray.cho", "Gray", "Artist", song = song))),
            PrintSettings(), PrintLabels("Key", "Transposition", "Capo", "Tempo", "$TEMPO_VALUE BPM", "Time", "Missing", DefaultSectionLabels(verse = "Verse", chorus = "Chorus", bridge = "Bridge", tab = "Tab", grid = "Grid", intro = "Intro", preChorus = "Pre-chorus", solo = "Solo", outro = "Outro")), renderer::width)
        val page = document.pages.first()
        val scale = 3f
        val width = ceil(document.width * scale).toInt()
        val height = ceil(document.height * scale).toInt()
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            renderer.draw(this, page, scale)
        }
        fun darkest(text: PrintText): Int {
            val left = (text.x * scale).toInt()
            val top = (text.y * scale).toInt()
            val right = ((text.x + renderer.width(text.text, text.style)) * scale).toInt()
            val bottom = ((text.y + text.style.size * 1.2f) * scale).toInt()
            val pixels = IntArray((right - left) * (bottom - top))
            bitmap.readPixels(pixels, startX = left, startY = top, width = right - left, height = bottom - top)
            return pixels.minOf { it shr 16 and 255 }
        }
        assertTrue(darkest(page.texts.first { it.text.startsWith("Key:") }) >= 70)
        assertTrue(darkest(page.texts.first { it.text.startsWith("Words") }) <= 30)
    }

    @Test fun chordDiagramsAreDrawnInTheirBoxesAndLeftOutOfTheSelectableText() = runBlocking {
        val renderer = PrintRenderer(TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr))
        val song = ChordProParser.parse("{title: Shapes}\n{define: F base-fret 1 frets 1 3 3 2 1 1 fingers 1 3 4 2 1 1}\n[G]Words [C]sung [D]over [F]chords and [Bm]a barre")
        val labels = PrintLabels("Key", "Transposition", "Capo", "Tempo", "$TEMPO_VALUE BPM", "Time", "Missing", DefaultSectionLabels(verse = "Verse", chorus = "Chorus", bridge = "Bridge", tab = "Tab", grid = "Grid", intro = "Intro", preChorus = "Pre-chorus", solo = "Solo", outro = "Outro"))
        suspend fun page(chords: List<PrintChord>, settings: PrintSettings) = layoutPrintDocument(
            PrintSource("Shapes", songs = listOf(PrintSong("shapes.cho", "Shapes", "Artist", song = song, chords = chords))), settings, labels, renderer::width,
        ).pages.single()
        val withoutDiagrams = renderer.selectableText(page(emptyList(), PrintSettings())).joinToString("") { it.text }
        val pages = ChordInstrument.entries.map { instrument ->
            val chords = printChordsOf(song, ChordNotation.STANDARD, instrument, emptyMap())
            assertEquals(listOf("G", "C", "D", "F", "Bm"), chords.map { it.name })
            page(chords, PrintSettings(showChordDiagrams = true))
        }
        pages.forEach { page ->
            assertEquals(5, page.diagrams.size)
            assertEquals(withoutDiagrams, renderer.selectableText(page).joinToString("") { it.text })
            val scale = 3f
            val width = ceil(595.276f * scale).toInt()
            val height = ceil(841.89f * scale).toInt()
            val bitmap = ImageBitmap(width, height)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
                renderer.draw(this, page, scale)
            }
            page.diagrams.forEach { diagram ->
                val left = (diagram.x * scale).toInt()
                val top = (diagram.y * scale).toInt()
                val pixels = IntArray((diagram.width * scale).toInt() * (diagram.height * scale).toInt())
                bitmap.readPixels(pixels, startX = left, startY = top, width = (diagram.width * scale).toInt(), height = (diagram.height * scale).toInt())
                assertTrue(pixels.count { (it shr 16 and 255) < 60 } > pixels.size / 50, diagram.toString())
            }
        }
        System.getenv("CAMPFIRE_PRINT_QA_DIR")?.let { directory ->
            File(directory).mkdirs()
            File(directory, "chord-diagrams.pdf").writeBytes(renderer.pdf(PrintDocument(595.276f, 841.89f, pages), "Chord diagrams"))
        }
        Unit
    }
}
