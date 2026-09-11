/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.zip

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ZipRoundTripTest {

    @Test
    fun writesAndReadsBackEveryEntryInOrder() {
        val entries = listOf(
            ZipEntry("empty.cho", ByteArray(0)),
            ZipEntry("songs/Plain ASCII.cho", "{title: Plain}\n[Am]Hello [C]world\n".encodeToByteArray()),
            ZipEntry("dalok/Árvíztűrő tükörfúrógép.cho", "{title: Árvíztűrő}\n{artist: Tükörfúrógép}\nÁÉÍÓŐÚŰ öüó\n".encodeToByteArray()),
            ZipEntry("random.bin", Random(1234).nextBytes(100 * 1024)),
        )

        val read = ZipReader.read(ZipWriter.write(entries))

        assertEquals(entries.map { it.name }, read.map { it.name })
        entries.forEachIndexed { index, entry ->
            assertContentEquals(entry.bytes, read[index].bytes, "Entry \"${entry.name}\" did not round-trip.")
        }
    }

    @Test
    fun writesAndReadsBackAnEmptyArchive() {
        val archive = ZipWriter.write(emptyList())

        assertEquals(22, archive.size)
        assertTrue(ZipReader.read(archive).isEmpty())
    }

    @Test
    fun writesAndReadsBackASingleEmptyEntry() {
        val read = ZipReader.read(ZipWriter.write(listOf(ZipEntry("nothing.cho", ByteArray(0)))))

        assertEquals(1, read.size)
        assertEquals("nothing.cho", read[0].name)
        assertEquals(0, read[0].bytes.size)
    }
}
