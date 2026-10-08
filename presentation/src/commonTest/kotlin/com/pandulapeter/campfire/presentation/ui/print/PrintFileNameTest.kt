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

import com.pandulapeter.campfire.data.model.domain.PrintSettings
import kotlin.test.Test
import kotlin.test.assertEquals

internal class PrintFileNameTest {

    @Test
    fun `song is named by its header rather than its library file`() = assertEquals(
        "tukorfurogep-arviz.pdf",
        pdfFileName(songSource(fileName = "Foo Bar (2).cho", title = "Árvíz", artist = "Tükörfúrógép"), PrintSettings()),
    )

    @Test
    fun `song with blank artist is named by its title alone`() = assertEquals(
        "hallelujah.pdf",
        pdfFileName(songSource(fileName = "hallelujah_2.cho", title = "Hallelujah", artist = " "), PrintSettings()),
    )

    @Test
    fun `setlist song sheets are named by the setlist title`() = assertEquals(
        "summer_set_2026.pdf",
        pdfFileName(setlistSource(), PrintSettings(setlistMode = PrintSettings.SetlistMode.SONG_SHEETS)),
    )

    @Test
    fun `setlist running order is told apart from its song sheets`() = assertEquals(
        "summer_set_2026-running_order.pdf",
        pdfFileName(setlistSource(), PrintSettings(setlistMode = PrintSettings.SetlistMode.RUNNING_ORDER)),
    )

    private fun songSource(fileName: String, title: String, artist: String) = PrintSource(
        title = title,
        songs = listOf(PrintSong(fileName = fileName, title = title, artist = artist, song = null)),
    )

    private fun setlistSource() = PrintSource(
        title = "Summer set 2026",
        isSetlist = true,
        songs = listOf(
            PrintSong(fileName = "a.cho", title = "A", artist = null, index = 1, song = null),
            PrintSong(fileName = "b.cho", title = "B", artist = null, index = 2, song = null),
        ),
    )
}
