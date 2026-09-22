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

import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.source.local.implementation.zip.ZipEntry
import com.pandulapeter.campfire.data.source.local.implementation.zip.ZipWriter
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

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

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

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

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("a.cho", "b.cho"), files.map { it.name })
    }

    @Test
    fun `reports what it did not read instead of failing`() = runBlocking {
        val archive = ZipWriter.write(
            listOf(
                ZipEntry("a.cho", "{title: A}".encodeToByteArray()),
                ZipEntry("notes.pdf", byteArrayOf(37, 80, 68, 70)),
                ZipEntry("b.cho", "{title: B}".encodeToByteArray()),
            ),
        )

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("a.cho", "b.cho", "notes.pdf"), files.map { it.name })
        assertEquals(0, files.last().bytes.size)
        assertFalse(files.last().isTooLarge)
    }

    @Test
    fun `stops at the size it was given`() = runBlocking {
        val archive = ZipWriter.write(
            listOf(
                ZipEntry("a.cho", ByteArray(600) { 'a'.code.toByte() }),
                ZipEntry("b.cho", ByteArray(600) { 'b'.code.toByte() }),
            ),
        )

        val files = archiveLocalSource.unpack(archive = archive, maxSize = 1000)

        assertEquals(listOf("a.cho", "b.cho"), files.map { it.name })
        assertEquals(600, files[0].bytes.size)
        assertTrue(files[1].isTooLarge)
    }

    @Test
    fun `counts the damaged entries of a nested archive against the whole import`() = runBlocking {
        val damagedSize = 100
        val damaged = ZipWriter.write(listOf(ZipEntry("x.cho", ByteArray(damagedSize) { 'x'.code.toByte() })))
        // The first byte of the stored content, after the 30 byte local header and the name: the checksum now fails.
        damaged[30 + "x.cho".length] = 'y'.code.toByte()
        val good = ZipWriter.write(listOf(ZipEntry("song.cho", ByteArray(20) { 's'.code.toByte() })))
        val archive = ZipWriter.write(listOf(ZipEntry("a.zip", damaged), ZipEntry("b.zip", good)))

        val files = archiveLocalSource.unpack(archive = archive, maxSize = damaged.size + good.size + damagedSize + 10L)

        assertTrue(files.single { it.name == "song.cho" }.isTooLarge)
    }

    @Test
    fun `reports an archive inside it that cannot be read`() = runBlocking {
        val archive = ZipWriter.write(
            listOf(
                ZipEntry("a.cho", "{title: A}".encodeToByteArray()),
                ZipEntry("more.zip", byteArrayOf(1, 2, 3)),
            ),
        )

        val files = archiveLocalSource.unpack(archive = archive, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertEquals(listOf("a.cho", "more.zip"), files.map { it.name })
        assertEquals(ImportedFile.unread("more.zip"), files.last())
    }
}
