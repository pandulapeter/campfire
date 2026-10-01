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
import com.pandulapeter.campfire.data.model.domain.PrintSettings
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Inflater
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises real shaping, canvas rasterization and PDF encoding, including Hungarian and musical symbols. */
internal class PrintRendererTest {
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
            PrintSettings(columns = 2), PrintLabels("Key", "Capo", "Tempo", "Time", "Missing"), renderer::width)
        assertTrue(document.pages.size >= 4)
        val bytes = renderer.pdf(document)
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
        val top = PrintPage(listOf(PrintText(text = "MMMMMMMM", x = 10f, y = 5f, size = 20)))
        val bottom = PrintPage(listOf(PrintText(text = "MMMMMMMM", x = 10f, y = 70f, size = 20)))
        val bytes = renderer.pdf(PrintDocument(width = 100f, height = 100f, pages = listOf(top, bottom, top)))
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
}
