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

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater as JavaDeflater
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The most compressible data real writers produce stays within [ZipDirectory.MAX_DEFLATE_RATIO], so no honest entry is
 * ever taken for one declaring more than its data can hold: zeros, deflated by zlib at its best and by the app's own
 * writer, read back whole through the streaming walk.
 */
internal class DeflateRatioJvmTest {

    private val zeros = ByteArray(40 shl 20)

    @Test
    fun `zlib at its best over zeros stays within the ratio and is read whole`() = runBlocking {
        val archive = ByteArrayOutputStream().also { stream ->
            ZipOutputStream(stream).use { zip ->
                zip.setLevel(JavaDeflater.BEST_COMPRESSION)
                zip.putNextEntry(java.util.zip.ZipEntry("zeros.bin"))
                zip.write(zeros)
                zip.closeEntry()
            }
        }.toByteArray()

        assertReadWhole(archive)
    }

    @Test
    fun `the app's own writer over zeros stays within the ratio and is read whole`() = runBlocking {
        assertReadWhole(ZipWriter.write(listOf(ZipEntry("zeros.bin", zeros))))
    }

    private suspend fun assertReadWhole(archive: ByteArray) {
        val directory = ZipReader.open(ByteArrayZipSource(archive))
        val compressedSize = archive.compressedSizeOf("zeros.bin")
        assertTrue(zeros.size <= compressedSize * ZipDirectory.MAX_DEFLATE_RATIO, "${zeros.size} bytes from $compressedSize")
        assertTrue(directory.canProduceDeclaredSize(0))
        val sink = RecordingEntrySink()
        val walk = directory.walk(maxTotalSize = Long.MAX_VALUE) { _, _ -> sink }
        assertEquals(emptyList(), walk.unread)
        assertEquals(zeros.size, sink.bytes.size)
        assertTrue(sink.sha256Hex != null, "The entry was not read whole")
    }
}
