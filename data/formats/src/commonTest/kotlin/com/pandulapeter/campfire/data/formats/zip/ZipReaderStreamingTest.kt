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
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The reader over a [ZipSource]: alike with the one in memory, and what only it does (its caps, its sinks). */
internal class ZipReaderStreamingTest {

    @Test
    fun `reads UTF-8 and code page 437 names and backslash separators alike`() = runTest {
        val archive = rawArchive(
            listOf(
                RawEntry("dalok/Árvíztűrő.cho".encodeToByteArray(), flags = 0x0800),
                RawEntry("macOS/Tükörfúrógép.cho".encodeToByteArray()),
                RawEntry(byteArrayOf('c'.code.toByte(), 'a'.code.toByte(), 'f'.code.toByte(), 0x82.toByte())),
                RawEntry("songs\\".encodeToByteArray()),
                RawEntry("songs\\windows.cho".encodeToByteArray()),
                RawEntry("songs/".encodeToByteArray()),
            ),
        )

        val read = readAlike(archive)

        assertEquals(
            listOf("dalok/Árvíztűrő.cho", "macOS/Tükörfúrógép.cho", "café", "songs\\windows.cho"),
            read.entries.map { it.name },
        )
    }

    @Test
    fun `refuses a ZIP64 archive alike`() = runTest {
        val archive = ZipWriter.write(listOf(ZipEntry("a.cho", "a".encodeToByteArray())))
        val endRecord = archive.size - 22
        for ((field, width) in listOf(10 to 2, 12 to 4, 16 to 4)) {
            val zip64 = archive.copyOf().also { for (index in 0 until width) it[endRecord + field + index] = 0xFF.toByte() }

            assertFailsWith<ZipException> { readAlike(zip64) }
        }
    }

    @Test
    fun `reads or refuses an archive cut at every byte alike`() = runTest {
        val archive = ZipWriter.write(
            listOf(
                ZipEntry("a.cho", "{title: A}\n".repeat(20).encodeToByteArray()),
                ZipEntry("b/c.cho", Random(1).nextBytes(100)),
            ),
        )
        for (cut in 0..archive.size) {
            assertReadsAlike(archive.copyOf(cut), "Cut to $cut bytes")
            assertReadsAlike(archive.copyOfRange(cut, archive.size), "Cut from $cut bytes")
        }
    }

    @Test
    fun `finds the end record behind the longest comment and no further`() = runTest {
        val archive = ZipWriter.write(listOf(ZipEntry("a.cho", "a".encodeToByteArray())))
        val withComment = { length: Int ->
            archive.copyOf().also { it[archive.size - 2] = 0xFF.toByte(); it[archive.size - 1] = 0xFF.toByte() } + ByteArray(length) { 'c'.code.toByte() }
        }

        assertEquals(listOf("a.cho"), readAlike(withComment(65_535)).entries.map { it.name })
        assertFailsWith<ZipException> { readAlike(withComment(65_536)) }
    }

    @Test
    fun `refuses a central directory declared past the cap before reading it`() = runTest {
        val source = TailSource(size = 40L shl 20, tail = endRecord(entries = 1, directorySize = 33L shl 20, directoryOffset = 1L shl 20))

        assertFailsWith<ZipException> { ZipReader.open(source) }
        assertTrue(source.reads.all { it.first >= source.size - 65_557 }, "Read ${source.reads}")
        assertTrue(33L shl 20 > ImportLimits.MAX_CENTRAL_DIRECTORY_SIZE)
    }

    @Test
    fun `refuses records adding up to more than the cap however small the directory says it is`() = runTest {
        val archive = ZipWriter.write(List(3) { ZipEntry("song $it.cho", "a".encodeToByteArray()) })
        val endRecord = archive.size - 22
        val directorySize = archive.u32(endRecord + 12)
        val understated = archive.copyOf().also { it[endRecord + 12] = 46; for (index in 13..15) it[endRecord + index] = 0 }

        assertEquals(3, ZipReader.open(ByteArrayZipSource(archive), maxCentralDirectorySize = directorySize).entryCount)
        assertFailsWith<ZipException> { ZipReader.open(ByteArrayZipSource(archive), maxCentralDirectorySize = directorySize - 1) }
        assertFailsWith<ZipException> { ZipReader.open(ByteArrayZipSource(understated), maxCentralDirectorySize = 46) }
    }

    @Test
    fun `refuses a declared entry count past the cap before reading the directory`() = runTest {
        val archive = ZipWriter.write(List(11) { ZipEntry("song $it.cho", "a".encodeToByteArray()) })
        val source = TailSource(size = archive.size.toLong(), tail = archive)

        assertEquals(11, ZipReader.open(source, maxEntries = 11).entryCount)
        val tooMany = TailSource(size = archive.size.toLong(), tail = archive)
        assertFailsWith<ZipException> { ZipReader.open(tooMany, maxEntries = 10) }
        assertEquals(1, tooMany.reads.size, "Read ${tooMany.reads}")
        assertTrue(ImportLimits.MAX_ARCHIVE_ENTRIES > 0xFFFF)
    }

    @Test
    fun `ends every sink exactly once - closed for what was read and abandoned for what was not`() = runTest {
        val good = "{title: Good}\n".repeat(100).encodeToByteArray()
        val archive = ZipWriter.write(listOf(ZipEntry("good.cho", good), ZipEntry("bad.cho", good), ZipEntry("also.cho", good)))
        // The second entry's checksum no longer matches.
        val badRecord = (0 until archive.size - 4).filter { archive.u32(it) == 0x02014B50L }[1]
        archive[badRecord + 16] = (archive[badRecord + 16].toInt() xor 1).toByte()
        val sinks = mutableMapOf<String, RecordingEntrySink>()

        val walk = ZipReader.open(ByteArrayZipSource(archive)).walk { name, _ -> RecordingEntrySink().also { sinks[name] = it } }

        assertEquals(listOf("good.cho", "also.cho"), walk.read)
        assertEquals(listOf(UnreadZipEntry("bad.cho", UnreadZipEntry.Reason.UNREADABLE)), walk.unread)
        sinks.forEach { (name, sink) -> assertTrue(sink.isAbandoned != (sink.sha256Hex != null), name) }
        assertTrue(sinks.getValue("bad.cho").isAbandoned)
        assertEquals(Sha256.hashToHex(good), sinks.getValue("good.cho").sha256Hex)
    }

    @Test
    fun `copies a stored entry and inflates a deflated one of any declared size through its sink`() = runTest {
        val stored = Random(2).nextBytes(3 shl 20)
        val deflated = ByteArray(60 shl 20) { (it % 7).toByte() }
        val archive = ZipWriter.write(listOf(ZipEntry("recording.mp3", stored), ZipEntry("nested.zip", deflated)))
        val sinks = mutableMapOf<String, RecordingEntrySink>()

        val walk = ZipReader.open(ByteArrayZipSource(archive)).walk(maxTotalSize = Long.MAX_VALUE) { name, _ ->
            RecordingEntrySink().also { sinks[name] = it }
        }

        assertEquals(listOf("recording.mp3", "nested.zip"), walk.read)
        assertContentEquals(stored, sinks.getValue("recording.mp3").bytes)
        assertEquals(Sha256.hashToHex(deflated), sinks.getValue("nested.zip").sha256Hex)
        // Past what an entry may be in memory, which only the in-memory reader is held to.
        assertEquals(
            UnreadZipEntry("nested.zip", UnreadZipEntry.Reason.UNREADABLE),
            ZipReader.read(ByteArrayZipSource(archive), maxTotalSize = Long.MAX_VALUE).unread.single(),
        )
    }

    private class RawEntry(val name: ByteArray, val flags: Int = 0)

    /** Stored entries of one byte under names written exactly as given, with the flags given. */
    private fun rawArchive(entries: List<RawEntry>): ByteArray {
        val data = byteArrayOf('x'.code.toByte())
        val crc = Crc32.of(data)
        val builder = ByteArrayBuilder()
        val offsets = entries.map { entry ->
            val offset = builder.size
            builder.u32(0x04034B50L)
            builder.u16(10)
            builder.u16(entry.flags)
            builder.u16(0) // Stored.
            repeat(2) { builder.u16(0) } // Modification time and date.
            builder.u32(crc)
            repeat(2) { builder.u32(data.size.toLong()) }
            builder.u16(entry.name.size)
            builder.u16(0) // Extra field length.
            builder.bytes(entry.name)
            builder.bytes(data)
            offset
        }
        val directoryOffset = builder.size
        entries.forEachIndexed { index, entry ->
            builder.u32(0x02014B50L)
            builder.u16(20) // Version made by.
            builder.u16(10) // Version needed to extract.
            builder.u16(entry.flags)
            builder.u16(0) // Stored.
            repeat(2) { builder.u16(0) } // Modification time and date.
            builder.u32(crc)
            repeat(2) { builder.u32(data.size.toLong()) }
            builder.u16(entry.name.size)
            repeat(4) { builder.u16(0) } // Extra field and comment lengths, disk number, internal attributes.
            builder.u32(0) // External attributes.
            builder.u32(offsets[index].toLong())
            builder.bytes(entry.name)
        }
        builder.bytes(endRecord(entries.size, (builder.size - directoryOffset).toLong(), directoryOffset.toLong()))
        return builder.build()
    }

    private fun endRecord(entries: Int, directorySize: Long, directoryOffset: Long): ByteArray {
        val builder = ByteArrayBuilder()
        builder.u32(0x06054B50L)
        repeat(2) { builder.u16(0) } // Disk numbers.
        repeat(2) { builder.u16(entries) } // Entries on this disk and in total.
        builder.u32(directorySize)
        builder.u32(directoryOffset)
        builder.u16(0) // Comment length.
        return builder.build()
    }

    /** A file of [size] bytes ending in [tail], zeroes before it, that keeps the ranges it was asked for. */
    private class TailSource(override val size: Long, private val tail: ByteArray) : ZipSource {

        val reads = mutableListOf<LongRange>()

        override suspend fun read(position: Long, buffer: ByteArray, offset: Int, length: Int) {
            reads += position until position + length
            val tailStart = size - tail.size
            for (index in 0 until length) {
                val at = position + index
                buffer[offset + index] = if (at >= tailStart) tail[(at - tailStart).toInt()] else 0
            }
        }
    }
}
