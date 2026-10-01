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
import java.io.File
import kotlin.test.Test
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
        assertTrue(bytes.size < 3_000_000, "Lossless paper compression should keep ordinary setlists reasonably small.")
        // Opt-in output for manual visual QA. Ordinary test runs leave no files behind.
        System.getenv("CAMPFIRE_PRINT_QA_DIR")?.let { directory ->
            File(directory).mkdirs()
            File(directory, "setlist.pdf").writeBytes(bytes)
        }
        Unit
    }
}
