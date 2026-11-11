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
import java.time.LocalDateTime
import java.util.zip.ZipInputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Reads what [ZipWriter] writes back through `java.util.zip`, the way any other unzip tool sees it. */
internal class ZipWriterJvmTest {

    @Test
    fun `an entry is dated to the even second it was written in`() {
        assertEquals(LocalDateTime.of(2026, 10, 8, 14, 30, 58), modifiedAt(DosTimestamp.of(2026, 10, 8, 14, 30, 59)))
        assertEquals(LocalDateTime.of(1980, 1, 1, 0, 0, 0), modifiedAt(DosTimestamp.of(1980, 1, 1, 0, 0, 0)))
    }

    @Test
    fun `a moment before 1980 is dated to the first one a zip archive can hold`() {
        assertEquals(LocalDateTime.of(1980, 1, 1, 0, 0, 0), modifiedAt(DosTimestamp.of(1970, 6, 15, 12, 0, 0)))
    }

    @Test
    fun `a moment after 2107 is dated to the last one a zip archive can hold`() {
        assertEquals(LocalDateTime.of(2107, 12, 31, 23, 59, 58), modifiedAt(DosTimestamp.of(2200, 1, 1, 0, 0, 0)))
    }

    @Test
    fun `deflated and stored entries both read back`() {
        val song = "{title: Riverlight}\n".repeat(200).encodeToByteArray()
        val noise = Random(8).nextBytes(5_000)
        val entries = listOf(ZipEntry("songs/riverlight.cho", song), ZipEntry("cover.jpg", noise), ZipEntry("empty.cho", ByteArray(0)))

        val read = ZipInputStream(runBlocking { ZipWriter.write(entries) }.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.map { Triple(it.name, it.method, zip.readBytes()) }.toList()
        }

        assertEquals(entries.map { it.name }, read.map { it.first })
        assertEquals(listOf(java.util.zip.ZipEntry.DEFLATED, java.util.zip.ZipEntry.STORED, java.util.zip.ZipEntry.STORED), read.map { it.second })
        entries.forEachIndexed { index, entry -> assertContentEquals(entry.bytes, read[index].third) }
    }

    private fun modifiedAt(timestamp: DosTimestamp) =
        ZipInputStream(runBlocking { ZipWriter.write(listOf(ZipEntry("song.cho", byteArrayOf(1))), modifiedAt = timestamp) }.inputStream()).use { zip ->
            zip.nextEntry!!.timeLocal
        }
}
