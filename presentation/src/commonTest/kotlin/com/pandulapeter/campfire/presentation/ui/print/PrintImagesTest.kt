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

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.presentation.ui.pictures.SongImageSize
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class PrintImagesTest {
    private val labels = PrintLabels(
        "Key", "Transposition", "Capo", "Tempo", "$TEMPO_VALUE BPM", "Time", "Missing",
        DefaultSectionLabels(verse = "Verse", chorus = "Chorus", bridge = "Bridge", tab = "Tab", grid = "Grid", intro = "Intro", preChorus = "Pre-chorus", solo = "Solo", outro = "Outro", abc = "ABC notation", lilyPond = "LilyPond", svg = "SVG"),
        imageNotFound = "Image not found: $LABELLED_VALUE",
    )

    private fun measure(text: String, style: PrintStyle) = text.length * style.size * 0.6f

    private suspend fun layout(text: String, imageSizes: Map<String, SongImageSize>, settings: PrintSettings = PrintSettings()) = layoutPrintDocument(
        source = PrintSource("Pictures", songs = listOf(PrintSong("pictures.cho", "Pictures", "Artist", song = ChordProParser.parse(text), text = text))),
        settings = settings,
        labels = labels,
        measureText = ::measure,
        imageSizes = imageSizes,
    )

    @Test fun `a photo prints fitted to its column and to a page never cut - between the lines around it`() = runTest {
        val settings = PrintSettings()
        val document = layout("{title: Pictures}\nAbove\n\n{image: src=tall.jpg}\n\nUnder", mapOf("tall.jpg" to SongImageSize.Known(1200, 3000)), settings)
        val (pageIndex, image) = document.pages.withIndex().firstNotNullOf { (index, page) -> page.images.singleOrNull()?.let { index to it } }
        val margin = settings.marginMm * 72f / 25.4f
        assertTrue(image.x >= margin && image.x + image.width <= document.width - margin, "The picture is $image, outside the column.")
        assertTrue(image.y >= margin && image.y + image.height <= document.height - margin, "The picture is $image, cut by the page.")
        assertEquals(1200f / 3000f, image.width / image.height, 0.01f)
        val above = document.pages.withIndex().first { (_, page) -> page.texts.any { it.text == "Above" } }
        val under = document.pages.withIndex().first { (_, page) -> page.texts.any { it.text == "Under" } }
        assertTrue(above.index < pageIndex || above.value.texts.first { it.text == "Above" }.y < image.y)
        assertTrue(under.index > pageIndex || under.value.texts.first { it.text == "Under" }.y > image.y + image.height)
    }

    @Test fun `a missing picture prints its sentence which stays out of the text layer`() = runTest {
        val document = layout("{title: Pictures}\n{image: src=gone.jpg}", emptyMap())
        val sentence = document.pages.flatMap { it.texts }.single { it.text == "Image not found: gone.jpg" }
        assertFalse(sentence.isSelectable)
        assertTrue(document.pages.all { it.images.isEmpty() })
    }

    @Test fun `a song with no picture prints no picture`() = runTest {
        assertTrue(layout("{title: Pictures}\nWords", emptyMap()).pages.all { it.images.isEmpty() })
    }
}
