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

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ZIP64 as [ZipWriter] writes it: only past what the 32-bit format holds, read back by the streaming reader. Archives
 * past 4 GiB are written into a [HeaderKeepingZipSink], which keeps their headers and only counts their gigabytes.
 */
internal class ZipWriterZip64Test {

    @Test
    fun `65534 entries write no ZIP64 record and 65535 do`() = runTest {
        val within = ZipWriter.write(List(0xFFFE) { ZipEntry("$it.cho", byteArrayOf(it.toByte())) })
        val past = ZipWriter.write(List(0xFFFF) { ZipEntry("$it.cho", byteArrayOf(it.toByte())) })

        assertFalse(hasZip64Records(ByteArrayZipSource(within)))
        assertEquals(0xFFFE, within.endRecordEntryCount())
        assertEquals(0xFFFE, ZipReader.read(within, maxTotalSize = Long.MAX_VALUE).entries.size)
        assertTrue(hasZip64Records(ByteArrayZipSource(past)))
        assertEquals(0xFFFF, past.endRecordEntryCount())
        val read = ZipReader.read(ByteArrayZipSource(past), maxTotalSize = Long.MAX_VALUE).entries
        assertEquals(0xFFFF, read.size)
        assertEquals("65534.cho", read.last().name)
        assertContentEquals(byteArrayOf(0xFFFE.toByte()), read.last().bytes)
    }

    @Test
    fun `only an entry starting at or past 0xFFFFFFFF gets its offset in a ZIP64 field`() = runTest {
        val sink = HeaderKeepingZipSink(start = 0xFFFFFFFEL)

        ZipWriter.write(entries = listOf(entry("a.cho", ""), entry("b.cho", "{title: B}")).map { entry -> { entry } }, sink = sink)

        val source = sink.source()
        val headers = centralHeadersOf(source)
        assertEquals(listOf(20, 45), headers.map { it.versionNeeded })
        assertEquals(listOf(0, 12), headers.map { it.extraLength })
        assertEquals(listOf(0xFFFFFFFEL, 0xFFFFFFFFL), headers.map { it.offsetField })
        assertEquals(listOf("", "{title: B}"), ZipReader.read(source).entries.map { it.bytes.decodeToString() })
    }

    @Test
    fun `a central directory starting at 0xFFFFFFFF is found through the ZIP64 record`() = runTest {
        // The offset itself is 0xFFFFFFFF, which in a 32-bit field means "look in the ZIP64 record" to every reader.
        val sink = HeaderKeepingZipSink(start = 0xFFFFFFFFL)

        ZipWriter.write(entries = emptyList(), sink = sink)

        val source = sink.source()
        assertTrue(hasZip64Records(source))
        assertEquals(0, ZipReader.open(source).entryCount)
    }

    @Test
    fun `an archive ending at 0xFFFFFFFF needs no ZIP64 and one a byte longer has its records`() = runTest {
        val endingAtLimit = HeaderKeepingZipSink(start = 0xFFFFFFFFL - 22)
        val byteLonger = HeaderKeepingZipSink(start = 0xFFFFFFFFL - 21)
        val directoryCrossing = HeaderKeepingZipSink(start = 0xFFFFFFFFL - 60)

        ZipWriter.write(entries = emptyList(), sink = endingAtLimit)
        ZipWriter.write(entries = emptyList(), sink = byteLonger)
        ZipWriter.write(entries = listOf(entry("a", "")).map { entry -> { entry } }, sink = directoryCrossing)

        assertEquals(0xFFFFFFFFL, endingAtLimit.position)
        assertFalse(hasZip64Records(endingAtLimit.source()))
        assertEquals(0, ZipReader.open(endingAtLimit.source()).entryCount)
        // Longer than 0xFFFFFFFF bytes with no locator would read as an archive some tool wrapped past 4 GiB.
        assertTrue(hasZip64Records(byteLonger.source()))
        assertEquals(0, ZipReader.open(byteLonger.source()).entryCount)
        assertTrue(hasZip64Records(directoryCrossing.source()))
        assertEquals(listOf("a"), ZipReader.read(directoryCrossing.source()).entries.map { it.name })
    }

    @Test
    fun `an export past 4 GiB reads back from its headers through the streaming reader`() = runTest {
        // The sink starts 96 MiB short of 4 GiB, as after earlier entries, so CI pays the CRC of 192 MiB rather than of
        // 4 GiB; one array stands for every large entry, and its random bytes are stored.
        val chunk = Random(64).nextBytes(64 shl 20)
        val entries = listOf(entry("a.cho", "{title: A}")) + List(3) { ZipEntry("files/$it.bin", chunk) } + entry("z.cho", "{title: Z}")
        val sink = HeaderKeepingZipSink(start = (4L shl 30) - (96L shl 20)) { it === chunk }

        val summary = ZipWriter.write(entries = entries.map { entry -> { entry } }, sink = sink)

        val source = sink.source()
        assertTrue(source.size > (4L shl 30) + (96L shl 20))
        assertEquals(3L * (64 shl 20) + 20, summary.unpackedSize)
        assertTrue(hasZip64Records(source))
        assertEquals(listOf(20, 20, 20, 45, 45), centralHeadersOf(source).map { it.versionNeeded })
        val directory = ZipReader.open(source)
        assertEquals(entries.map { it.name }, (0 until directory.entryCount).map { directory.nameOf(it) })
        assertEquals(List(3) { 64L shl 20 }, directory.uncompressedSizes.toList().subList(1, 4))
        val read = ZipReader.read(source, limitOf = { name, _ -> if (name.endsWith(".cho")) EntryPolicy.Read(100) else EntryPolicy.Skip })
        assertEquals(listOf("{title: A}", "{title: Z}"), read.entries.map { it.bytes.decodeToString() })
        // Only the headers were read: the 192 MiB between them read as zeroes, and would fail their CRC.
        assertTrue(source.reads.sumOf { it.last - it.first + 1 } < 1_000_000, "Read ${source.reads}")
    }

    @Test
    fun `a name longer than 65535 bytes is still refused`() = runTest {
        assertFailsWith<ZipException> { ZipWriter.write(listOf(ZipEntry("a".repeat(0x10000), ByteArray(0)))) }
    }

    private fun entry(name: String, text: String) = ZipEntry(name, text.encodeToByteArray())

    private fun ByteArray.endRecordEntryCount() = u16(size - 22 + 10)

    private suspend fun hasZip64Records(source: ZipSource): Boolean {
        if (source.size < 22 + 20) return false
        return source.bytes(source.size - 22 - 20, 4).u32(0) == 0x07064B50L
    }

    /** The central headers' fields ZIP64 changes, found through the ZIP64 record where there is one. */
    private suspend fun centralHeadersOf(source: ZipSource): List<CentralHeader> {
        val end = source.bytes(source.size - 22, 22)
        val (offset, size) = if (hasZip64Records(source)) {
            val record = source.bytes(source.bytes(source.size - 22 - 20, 20).u64(8), 56)
            record.u64(48) to record.u64(40)
        } else {
            end.u32(16) to end.u32(12)
        }
        val directory = source.bytes(offset, size.toInt())
        val headers = mutableListOf<CentralHeader>()
        var position = 0
        while (position < directory.size) {
            headers += CentralHeader(directory.u16(position + 6), directory.u16(position + 30), directory.u32(position + 42))
            position += 46 + directory.u16(position + 28) + directory.u16(position + 30) + directory.u16(position + 32)
        }
        return headers
    }

    private suspend fun ZipSource.bytes(position: Long, length: Int) = ByteArray(length).also { read(position, it, 0, length) }

    private data class CentralHeader(val versionNeeded: Int, val extraLength: Int, val offsetField: Long)
}
