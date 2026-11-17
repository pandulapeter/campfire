/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.zip

import com.pandulapeter.campfire.data.model.hashing.Sha256
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class ZipWriterLargeEntryTest {

    /**
     * Entries of up to 1 MiB are written exactly as they always were, whatever the writer does with larger ones: shared
     * copies hash their zip, and an archive that changed by a bit would be uploaded again for nothing. The digest is of
     * the archive the writer made before it treated large entries apart.
     */
    @Test
    fun `an archive of entries up to 1 MiB is byte for byte what it always was`() = runTest {
        val random = Random(11)
        val entries = listOf(
            ZipEntry("songs/riverlight.cho", "{title: Riverlight}\n[Am]Down by the [C]river\n".repeat(30).encodeToByteArray()),
            ZipEntry("covers/cover.jpg", random.nextBytes(20_000)),
            ZipEntry("empty.cho", ByteArray(0)),
            ZipEntry("setlists/Árvíztűrő.setlist.json", "{\"title\": \"Árvíztűrő\"}".encodeToByteArray()),
            ZipEntry("files/recording.txt", ByteArray(LARGE_ENTRY_SIZE) { "la la la, ".encodeToByteArray()[it % 10] }),
            ZipEntry("files/noise.bin", random.nextBytes(LARGE_ENTRY_SIZE)),
        )

        val archive = ZipWriter.write(entries, modifiedAt = DosTimestamp.of(2026, 11, 17, 12, 0, 0))

        assertEquals("e4ba3ae3cb4dcb8171f3f51509640f98aa1ef7bb79264317209657d9eaae7e79", Sha256.hashToHex(archive))
    }

    @Test
    fun `a large entry that does not compress is stored and reads back`() = runTest {
        val noise = Random(12).nextBytes(2 * LARGE_ENTRY_SIZE)

        val archive = ZipWriter.write(listOf(ZipEntry("files/recording.mp3", noise)))

        assertEquals(METHOD_STORED, firstMethodOf(archive))
        assertContentEquals(noise, readBack(archive))
    }

    @Test
    fun `a large entry that compresses is deflated and reads back`() = runTest {
        val text = ByteArray(2 * LARGE_ENTRY_SIZE) { "[Am]Down by the [C]river\n".encodeToByteArray()[it % 25] }

        val archive = ZipWriter.write(listOf(ZipEntry("files/songbook.txt", text)))

        assertEquals(METHOD_DEFLATED, firstMethodOf(archive))
        assertTrue(archive.size < text.size / 2)
        assertContentEquals(text, readBack(archive))
    }

    @Test
    fun `a large entry whose head compresses but whose whole does not is stored`() = runTest {
        val bytes = ByteArray(64 * 1024) + Random(13).nextBytes(2 * LARGE_ENTRY_SIZE)

        val archive = ZipWriter.write(listOf(ZipEntry("files/mixed.bin", bytes)))

        assertEquals(METHOD_STORED, firstMethodOf(archive))
        assertContentEquals(bytes, readBack(archive))
    }

    @Test
    fun `an archive whose central directory would start at the 32-bit marker's offset is refused`() = runTest {
        // The offset itself is 0xFFFFFFFF, which in a 32-bit field means "look in the ZIP64 record" to every reader.
        val sink = object : ZipSink {
            override var position = 0xFFFFFFFFL
                private set

            override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
                position += length
            }
        }

        assertFailsWith<ZipException> { ZipWriter.write(entries = emptyList(), sink = sink) }
    }

    private fun readBack(archive: ByteArray) = ZipReader.read(archive, maxTotalSize = Long.MAX_VALUE).entries.single().bytes

    private fun firstMethodOf(archive: ByteArray) = (archive[8].toInt() and 0xFF) or (archive[9].toInt() and 0xFF shl 8)

    private companion object {
        const val LARGE_ENTRY_SIZE = 1024 * 1024
        const val METHOD_STORED = 0
        const val METHOD_DEFLATED = 8
    }
}
