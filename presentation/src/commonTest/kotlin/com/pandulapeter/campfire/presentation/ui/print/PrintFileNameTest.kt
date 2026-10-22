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

import kotlin.test.Test
import kotlin.test.assertEquals

internal class PrintFileNameTest {

    @Test
    fun `song is named by its header rather than its library file`() = assertEquals(
        "tukorfurogep-arviz.pdf",
        pdfFileName(songSource(fileName = "Foo Bar (2).cho", title = "Árvíz", artist = "Tükörfúrógép")),
    )

    @Test
    fun `song with blank artist is named by its title alone`() = assertEquals(
        "hallelujah.pdf",
        pdfFileName(songSource(fileName = "hallelujah_2.cho", title = "Hallelujah", artist = " ")),
    )

    @Test
    fun `setlist song sheets are named by the setlist title`() = assertEquals(
        "summer_set_2026.pdf",
        pdfFileName(setlistSource()),
    )

    @Test
    fun `the library's own files are named as the export names them, a song keeping its stored extension`() {
        assertEquals("tukorfurogep-arviz.chopro", filesFileName(songSource(fileName = "Foo Bar (2).chopro", title = "Árvíz", artist = "Tükörfúrógép")))
        assertEquals("hallelujah.cho", filesFileName(songSource(fileName = "hallelujah", title = "Hallelujah", artist = "")))
        assertEquals("summer_set_2026.zip", filesFileName(setlistSource()))
    }

    @Test
    fun `a link's qr code is named after the song beside its pdf`() = assertEquals(
        "tukorfurogep-arviz-qr_code.png",
        qrCodeFileName(songSource(fileName = "Foo Bar (2).cho", title = "Árvíz", artist = "Tükörfúrógép")),
    )

    @Test
    fun `a setlist's qr code is named after the setlist whatever the pdf's content`() = assertEquals(
        "summer_set_2026-qr_code.png",
        qrCodeFileName(setlistSource()),
    )

    @Test
    fun `several songs are named the way their zip is`() = assertEquals(
        "campfire_songs.pdf",
        pdfFileName(
            PrintSource(
                title = "2 songs",
                kind = PrintSource.Kind.SONGS,
                songs = listOf(
                    PrintSong(fileName = "a.cho", title = "A", artist = null, song = null),
                    PrintSong(fileName = "b.cho", title = "B", artist = null, song = null),
                ),
            ),
        ),
    )

    private fun songSource(fileName: String, title: String, artist: String) = PrintSource(
        title = title,
        songs = listOf(PrintSong(fileName = fileName, title = title, artist = artist, song = null)),
    )

    private fun setlistSource() = PrintSource(
        title = "Summer set 2026",
        kind = PrintSource.Kind.SETLIST,
        songs = listOf(
            PrintSong(fileName = "a.cho", title = "A", artist = null, index = 1, song = null),
            PrintSong(fileName = "b.cho", title = "B", artist = null, index = 2, song = null),
        ),
    )
}
