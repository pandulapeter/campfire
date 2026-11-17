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
import kotlin.test.assertTrue

internal class ZipRoundTripTest {

    @Test
    fun `writes and reads back every entry in order`() = runTest {
        val entries = listOf(
            ZipEntry("empty.cho", ByteArray(0)),
            ZipEntry("songs/Plain ASCII.cho", "{title: Plain}\n[Am]Hello [C]world\n".encodeToByteArray()),
            ZipEntry("dalok/Árvíztűrő tükörfúrógép.cho", "{title: Árvíztűrő}\n{artist: Tükörfúrógép}\nÁÉÍÓŐÚŰ öüó\n".encodeToByteArray()),
            ZipEntry("random.bin", Random(1234).nextBytes(100 * 1024)),
        )

        val archive = ZipWriter.write(entries)
        val read = ZipReader.read(archive).entries

        assertEquals("0187036773f239de017f1d5acb9a21439b74db25602c047f89dda5a1d17285eb", Sha256.hashToHex(archive))
        assertEquals(entries.map { it.name }, read.map { it.name })
        entries.forEachIndexed { index, entry ->
            assertContentEquals(entry.bytes, read[index].bytes, "Entry \"${entry.name}\" did not round-trip.")
        }
    }

    @Test
    fun `writes and reads back an empty archive`() = runTest {
        val archive = ZipWriter.write(emptyList())

        assertEquals(22, archive.size)
        assertEquals("8739c76e681f900923b900c9df0ef75cf421d39cabb54650c4b9ad19b6a76d85", Sha256.hashToHex(archive))
        assertTrue(ZipReader.read(archive).entries.isEmpty())
    }

    @Test
    fun `writes and reads back a single empty entry`() = runTest {
        val archive = ZipWriter.write(listOf(ZipEntry("nothing.cho", ByteArray(0))))
        val read = ZipReader.read(archive).entries

        assertEquals("cf96c15e587dd733af62b92d8c23374c02ec77da80139bb1294b8c18dde47dfc", Sha256.hashToHex(archive))
        assertEquals(1, read.size)
        assertEquals("nothing.cho", read[0].name)
        assertEquals(0, read[0].bytes.size)
    }

    @Test
    fun `a song is deflated to well under its size`() = runTest {
        val song = buildString {
            appendLine("{title: Riverlight}")
            repeat(40) { appendLine("[Am]Down by the [C]river where the [G]light comes [E]home") }
        }.encodeToByteArray()

        val archive = ZipWriter.write(listOf(ZipEntry("riverlight.cho", song)))

        assertEquals("812e1ab726537af5813bb10564bb20bb21b4498af140188133aae59398bfb43b", Sha256.hashToHex(archive))
        assertTrue(archive.size < song.size / 3, "${archive.size} bytes for a ${song.size} byte song.")
        assertContentEquals(song, ZipReader.read(archive).entries.single().bytes)
    }

    @Test
    fun `an entry deflating does not shrink costs no more than its headers`() = runTest {
        val noise = Random(99).nextBytes(10_000)

        val archive = ZipWriter.write(listOf(ZipEntry("cover.jpg", noise)))

        assertEquals("e3a90a47a0974b059cefd94d85ca19f0694d31c0c4445c347cb4459c30db8076", Sha256.hashToHex(archive))
        assertTrue(archive.size <= noise.size + 22 + 2 * (46 + "cover.jpg".length), "${archive.size} bytes for ${noise.size}.")
        assertContentEquals(noise, ZipReader.read(archive).entries.single().bytes)
    }

    @Test
    fun `the same entries make the same bytes`() = runTest {
        val entries = { listOf(ZipEntry("a.cho", "{title: A}\n[C]La la la la".encodeToByteArray()), ZipEntry("b.bin", Random(4).nextBytes(500))) }

        val archive = ZipWriter.write(entries())

        assertEquals("e5eda331542dd1bbfc408ccb67a94e80019c8a9bb01a7cb00343bd5c7e8ac10e", Sha256.hashToHex(archive))
        assertContentEquals(archive, ZipWriter.write(entries()))
    }
}
