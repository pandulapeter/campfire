/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.source.local.implementation.zip.ZipEntry
import com.pandulapeter.campfire.data.source.local.implementation.zip.ZipWriter
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** What comes out of an archive, against the archives the desktops actually produce. */
class ArchiveLocalSourceTest {

    private val archiveLocalSource = ArchiveLocalSourceImpl()

    @Test
    fun `unpacks every file with its path stripped`() = runBlocking {
        val archive = ZipWriter.write(
            listOf(
                ZipEntry("songs/a.cho", "{title: A}".encodeToByteArray()),
                ZipEntry("setlists/s.setlist.json", "{}".encodeToByteArray()),
            ),
        )

        val files = archiveLocalSource.unpack(archive)

        assertEquals(listOf("a.cho", "s.setlist.json"), files.map { it.name })
        assertContentEquals("{title: A}".encodeToByteArray(), files.first().bytes)
    }

    @Test
    fun `leaves out what the archiving tool wrote for itself`() = runBlocking {
        val archive = ZipWriter.write(
            listOf(
                ZipEntry("a.cho", "{title: A}".encodeToByteArray()),
                // Every entry the macOS Finder adds to an archive of two songs: an AppleDouble carrying the
                // extended attributes of each file, under the extension of the file it belongs to, and the
                // directory's own listing state.
                ZipEntry("__MACOSX/._a.cho", byteArrayOf(0, 5, 22, 7, 0, 2)),
                ZipEntry("b.cho", "{title: B}".encodeToByteArray()),
                ZipEntry("__MACOSX/._b.cho", byteArrayOf(0, 5, 22, 7, 0, 2)),
                ZipEntry(".DS_Store", byteArrayOf(0, 0, 0, 1)),
            ),
        )

        val files = archiveLocalSource.unpack(archive)

        assertEquals(listOf("a.cho", "b.cho"), files.map { it.name })
    }

    @Test
    fun `unpacks the archives inside an archive`() = runBlocking {
        val inner = ZipWriter.write(listOf(ZipEntry("songs/b.cho", "{title: B}".encodeToByteArray())))
        val archive = ZipWriter.write(
            listOf(
                ZipEntry("a.cho", "{title: A}".encodeToByteArray()),
                ZipEntry("more.zip", inner),
            ),
        )

        val files = archiveLocalSource.unpack(archive)

        assertEquals(listOf("a.cho", "b.cho"), files.map { it.name })
    }
}
