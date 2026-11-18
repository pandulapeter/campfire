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

import com.pandulapeter.campfire.data.formats.document.assertTakesTimeInProportion
import com.pandulapeter.campfire.data.formats.zip.RealWriterArchives.LARGE_AFTER
import com.pandulapeter.campfire.data.formats.zip.RealWriterArchives.LARGE_BEFORE
import com.pandulapeter.campfire.data.formats.zip.RealWriterArchives.LARGE_ENTRY_SIZE
import com.pandulapeter.campfire.data.formats.zip.Zip64Field.COMPRESSED_SIZE
import com.pandulapeter.campfire.data.formats.zip.Zip64Field.LOCAL_HEADER_OFFSET
import com.pandulapeter.campfire.data.formats.zip.Zip64Field.UNCOMPRESSED_SIZE
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * ZIP64 through the streaming reader: what real tools write ([RealWriterArchives]) reads as its twin without ZIP64,
 * archives past 4 GiB and 65,535 entries read from their headers, and every ZIP64 field set wrong is refused, or leaves
 * its entry out, never read past.
 */
internal class ZipReaderZip64Test {

    @Test
    fun `small archives with ZIP64 forced read as their twins without it`() = runTest {
        for ((zip64, twin) in listOf(
            RealWriterArchives.INFO_ZIP_ZIP64 to RealWriterArchives.INFO_ZIP,
            RealWriterArchives.PYTHON_ZIP64 to RealWriterArchives.PYTHON,
        )) {
            val expected = readArchive(twin.hexToByteArray())

            assertEquals(RealWriterArchives.SMALL_NAMES, expected.entries.map { it.name })
            assertAlike(Result.success(expected), runCatching { ZipReader.read(ByteArrayZipSource(zip64.hexToByteArray())) })
        }
    }

    @Test
    fun `an end record needing a ZIP64 record that is not there is refused like Info-ZIP's streamed one`() = runTest {
        assertEquals(RealWriterArchives.SMALL_NAMES, readArchive(RealWriterArchives.INFO_ZIP_STREAMED.hexToByteArray()).entries.map { it.name })

        assertFailsWith<ZipException> { ZipReader.open(ByteArrayZipSource(RealWriterArchives.INFO_ZIP_ZIP64_STREAMED.hexToByteArray())) }
    }

    @Test
    fun `archives past 4 GiB from real tools read from their headers alone`() = runTest {
        for (archive in listOf(
            RealWriterArchives.DITTO_LARGE,
            RealWriterArchives.INFO_ZIP_LARGE,
            RealWriterArchives.PYTHON_LARGE,
            RealWriterArchives.JAVA_LARGE,
        )) {
            val source = archive.source()
            val directory = ZipReader.open(source)
            val sizes = (0 until directory.entryCount).associate { directory.nameOf(it) to directory.uncompressedSizes[it] }
            val sinks = mutableMapOf<String, RecordingEntrySink>()

            val walk = directory.walk(limitOf = { _, size -> EntryPolicy.Read(ImportLimits.MAX_ATTACHMENT_FILE_SIZE.coerceAtMost(size)) }) { name, _ ->
                RecordingEntrySink().also { sinks[name] = it }
            }

            assertEquals(LARGE_ENTRY_SIZE, sizes["big.bin"], "${archive.size}")
            assertEquals(listOf("big.bin"), walk.unread.map { it.name })
            assertEquals(LARGE_BEFORE, sinks.getValue("a.cho").bytes.decodeToString())
            assertEquals(LARGE_AFTER, sinks.getValue("z.cho").bytes.decodeToString())
            // The search for the end record reads the last 64 KiB; nothing else reaches into the gigabytes before the tail.
            assertTrue(source.reads.sumOf { it.last - it.first + 1 } < 200_000, "Read ${source.reads} of ${archive.size}")
        }
    }

    @Test
    fun `a per-entry cap holds a ZIP64 entry to what it holds any entry to`() = runTest {
        val source = RealWriterArchives.PYTHON_LARGE.source()

        val walk = ZipReader.open(source).walk(limitOf = { name, _ -> EntryPolicy.Read(if (name == "big.bin") 50_000_000 else 100) }) { _, _ ->
            RecordingEntrySink()
        }

        assertEquals(listOf(UnreadZipEntry("big.bin", UnreadZipEntry.Reason.TOO_LARGE)), walk.unread)
    }

    @Test
    fun `an entry past 2 GiB streams into its sink but is left out of memory`() = runTest {
        val archive = Zip64TestArchive(
            listOf(Zip64TestEntry("recording.wav", gap = 3L shl 30, marked = setOf(UNCOMPRESSED_SIZE, COMPRESSED_SIZE))),
            endOffset = 0xFFFFFFFFL,
        )
        val source = archive.source()
        var written = 0L

        val stopped = assertFailsWith<IllegalStateException> {
            ZipReader.open(source).walk(maxTotalSize = Long.MAX_VALUE) { _, _ ->
                RecordingEntrySink { written += 1; check(written < 4) { "Enough." } }
            }
        }

        assertEquals("Enough.", stopped.message)
        assertEquals(
            listOf(UnreadZipEntry("recording.wav", UnreadZipEntry.Reason.UNREADABLE)),
            ZipReader.read(source, maxTotalSize = Long.MAX_VALUE).unread,
        )
    }

    @Test
    fun `local headers past 4 GiB are found through the central directory's ZIP64 fields`() = runTest {
        val entries = listOf(
            Zip64TestEntry("a.cho", "{title: A}".encodeToByteArray(), marked = setOf(LOCAL_HEADER_OFFSET), localZip64 = true),
            Zip64TestEntry("b.cho", "{title: B}".encodeToByteArray(), marked = setOf(LOCAL_HEADER_OFFSET)),
        )

        val read = ZipReader.read(Zip64TestArchive(entries, base = 5L shl 30, endOffset = 0xFFFFFFFFL).source())

        assertEquals(listOf("{title: A}", "{title: B}"), read.entries.map { it.bytes.decodeToString() })
    }

    @Test
    fun `more than 65535 entries are read through the ZIP64 record as every tool writes them`() = runTest {
        val entries = List(70_000) { Zip64TestEntry("m/$it.txt", it.toString().encodeToByteArray()) }

        val read = ZipReader.read(Zip64TestArchive(entries, endEntries = 0xFFFF).source(), maxTotalSize = Long.MAX_VALUE)

        assertEquals(70_000, read.entries.size)
        assertEquals("69999", read.entries.last().bytes.decodeToString())
        assertEquals("m/65535.txt", read.entries[65_535].name)
    }

    @Test
    fun `the ZIP64 record's values win over stale 32-bit ones`() = runTest {
        val entries = List(3) { Zip64TestEntry("$it.cho", "{title: $it}".encodeToByteArray()) }

        for (archive in listOf(
            Zip64TestArchive(entries, endEntries = 2),
            Zip64TestArchive(entries, endEntries = 0xFFFF, endSize = 5),
            Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL, endEntries = 7),
        )) {
            assertEquals(listOf("0.cho", "1.cho", "2.cho"), ZipReader.read(archive.source()).entries.map { it.name })
        }
    }

    @Test
    fun `wrong disk fields in the locator and the ZIP64 record are not checked - split archives are refused`() = runTest {
        val entries = listOf(Zip64TestEntry("a.cho", "{title: A}".encodeToByteArray()))

        val explorer = Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL, endEntries = 0xFFFF, locatorDisks = 0, recordDisks = 0x0000000100000001)
        assertEquals(listOf("a.cho"), ZipReader.read(explorer.source()).entries.map { it.name })
        assertFailsWith<ZipException> { ZipReader.open(Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL, endDisks = 1).source()) }
    }

    @Test
    fun `an Explorer archive past 4 GiB with zero disks and stale 32-bit fields reads`() = runTest {
        val entries = listOf(
            Zip64TestEntry("big.bin", gap = 5L shl 30, marked = setOf(UNCOMPRESSED_SIZE, COMPRESSED_SIZE)),
            Zip64TestEntry("z.cho", LARGE_AFTER.encodeToByteArray(), marked = setOf(LOCAL_HEADER_OFFSET)),
        )
        val archive = Zip64TestArchive(entries, endEntries = 0xFFFF, endSize = 0xFFFFFFFFL, endOffset = 0x1234, locatorDisks = 0)

        val walk = ZipReader.open(archive.source()).walk(limitOf = { _, _ -> EntryPolicy.Read(1_000) }) { _, _ -> RecordingEntrySink() }

        assertEquals(listOf("z.cho"), walk.read)
        assertEquals(listOf(UnreadZipEntry("big.bin", UnreadZipEntry.Reason.TOO_LARGE)), walk.unread)
    }

    @Test
    fun `an archive past 4 GiB without ZIP64 is refused as wrapped - not as damaged`() = runTest {
        val tail = ZipWriter.write(listOf(ZipEntry("a.cho", "a".encodeToByteArray())))
        val wrapped = SparseZipSource(size = (4L shl 30) + 100, pieces = listOf((4L shl 30) + 100 - tail.size to tail))

        assertFailsWith<WrappedArchiveException> { ZipReader.open(wrapped) }
        val exactly4Gib = SparseZipSource(size = 0xFFFFFFFFL, pieces = listOf(0xFFFFFFFFL - tail.size to tail))
        assertTrue(assertFailsWith<ZipException> { ZipReader.open(exactly4Gib) } !is WrappedArchiveException)
    }

    @Test
    fun `a central header's marker without its value leaves the entry out`() = runTest {
        val data = "{title: A}".encodeToByteArray()
        val cases = listOf(
            // A marker and no field at all.
            Zip64TestEntry("none.cho", data, marked = setOf(COMPRESSED_SIZE), centralExtra = ByteArray(0)),
            // A field too short for its two markers.
            Zip64TestEntry("short.cho", data, marked = setOf(UNCOMPRESSED_SIZE, COMPRESSED_SIZE), centralExtra = zip64Field(10L)),
            // A value with its top bit set.
            Zip64TestEntry("negative.cho", data, marked = setOf(LOCAL_HEADER_OFFSET), centralExtra = zip64Field(Long.MIN_VALUE)),
            // A block whose length runs past the extra field.
            Zip64TestEntry("overrun.cho", data, marked = setOf(LOCAL_HEADER_OFFSET), centralExtra = byteArrayOf(1, 0, 9, 0) + ByteArray(8)),
        )

        val read = ZipReader.read(Zip64TestArchive(cases + Zip64TestEntry("good.cho", data), endOffset = 0xFFFFFFFFL).source())

        assertEquals(listOf("good.cho"), read.entries.map { it.name })
        assertEquals(cases.map { UnreadZipEntry(it.name, UnreadZipEntry.Reason.UNREADABLE) }, read.unread)
    }

    @Test
    fun `values in the ZIP64 field for fields not at their markers are ignored`() = runTest {
        val data = "{title: A}".encodeToByteArray()
        // The uncompressed size is marked; the compressed size and the offset after it are not, and their values lie.
        val extra = zip64Field(data.size.toLong(), 1L shl 40, 1L shl 50)
        val entries = listOf(Zip64TestEntry("a.cho", data, marked = setOf(UNCOMPRESSED_SIZE), centralExtra = byteArrayOf(0x55, 0x54, 1, 0, 0) + extra))

        assertContentEquals(data, ZipReader.read(Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL).source()).entries.single().bytes)
    }

    @Test
    fun `entry data reaching into the central directory of a ZIP64 archive is left out`() = runTest {
        val data = "{title: A}".encodeToByteArray()
        // The second entry claims to be longer than it is, so that its data runs into the central directory.
        val extra = zip64Field(data.size.toLong() + 30, data.size.toLong() + 30)
        val entries = listOf(
            Zip64TestEntry("a.cho", data),
            Zip64TestEntry("b.cho", data, marked = setOf(UNCOMPRESSED_SIZE, COMPRESSED_SIZE), centralExtra = extra),
        )

        val peeked = mutableListOf<String>()

        val read = ZipReader.read(
            Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL).source(),
            limitOf = { _, _ -> EntryPolicy.Sniff },
            sniffedLimitOf = { name, _ -> 1_000L.also { peeked += name } },
        )

        assertEquals(listOf("a.cho"), read.entries.map { it.name })
        assertEquals(listOf("a.cho"), peeked)
    }

    @Test
    fun `8-byte data descriptors with and without their signature are read past`() = runTest {
        val entries = listOf(
            Zip64TestEntry("signed.cho", "{title: A}".encodeToByteArray(), descriptor = Zip64TestEntry.Descriptor.SIGNED),
            Zip64TestEntry("unsigned.cho", "{title: B}".encodeToByteArray(), descriptor = Zip64TestEntry.Descriptor.UNSIGNED),
            Zip64TestEntry("after.cho", "{title: C}".encodeToByteArray(), marked = setOf(UNCOMPRESSED_SIZE)),
        )

        val read = ZipReader.read(Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL).source())

        assertEquals(listOf("{title: A}", "{title: B}", "{title: C}"), read.entries.map { it.bytes.decodeToString() })
    }

    @Test
    fun `a locator or ZIP64 record pointing anywhere wrong is refused`() = runTest {
        val entries = listOf(Zip64TestEntry("a.cho", "{title: A}".encodeToByteArray()))
        val broken = listOf(
            // The locator pointing past the file, at itself, into the central directory, and with its top bit set.
            Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL, locatorRecordOffset = { it + 1_000 }),
            Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL, locatorRecordOffset = { it + 56 }),
            Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL, locatorRecordOffset = { it - 20 }),
            Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL, locatorRecordOffset = { it or Long.MIN_VALUE }),
            // A record whose declared size runs into the locator, or is shorter than its fixed fields.
            Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL, recordSize = { it + 1 }),
            Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL, recordSize = { it - 1 }),
            Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL, recordSize = { -1 }),
            // Entry counts of 2^63 and one past the cap.
            Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL, recordEntries = { Long.MIN_VALUE }),
            Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL, recordEntries = { ImportLimits.MAX_ARCHIVE_ENTRIES + 1L }),
            // Offsets and sizes with their top bit set, or running into the record.
            Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL, recordDirectoryOffset = { it or Long.MIN_VALUE }),
            Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL, recordDirectorySize = { it or Long.MIN_VALUE }),
            Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL, recordDirectorySize = { it + 1 }),
            Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL, recordDirectoryOffset = { Long.MAX_VALUE - 10 }),
            // A marker and no ZIP64 record at all.
            Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL, writesZip64 = false),
            Zip64TestArchive(entries, endEntries = 0xFFFF, writesZip64 = false),
        )

        broken.forEachIndexed { index, archive ->
            val source = archive.source()
            assertFailsWith<ZipException>("Case $index") { ZipReader.open(source) }
            assertTrue(source.reads.all { it.first >= 0 && it.last < source.size }, "Case $index read ${source.reads}")
        }
    }

    @Test
    fun `an end record needing no ZIP64 reads as it always has wherever its ZIP64 record is broken`() = runTest {
        val entries = listOf(Zip64TestEntry("a.cho", "{title: A}".encodeToByteArray()))

        for (archive in listOf(
            Zip64TestArchive(entries, locatorRecordOffset = { it + 1_000 }),
            Zip64TestArchive(entries, recordSize = { it + 1 }),
            Zip64TestArchive(entries, recordDirectorySize = { it or Long.MIN_VALUE }),
        )) {
            assertEquals(listOf("a.cho"), ZipReader.read(archive.source()).entries.map { it.name })
        }
    }

    @Test
    fun `a declared ZIP64 count past the cap is refused before the directory is read`() = runTest {
        val entries = List(11) { Zip64TestEntry("$it.cho", "a".encodeToByteArray()) }
        val source = Zip64TestArchive(entries, endEntries = 0xFFFF).source()

        assertEquals(11, ZipReader.open(source, maxEntries = 11).entryCount)
        val tooMany = Zip64TestArchive(entries, endEntries = 0xFFFF).source()
        assertFailsWith<ZipException> { ZipReader.open(tooMany, maxEntries = 10) }
        // The tail with the end record and the locator, then the ZIP64 record, and nothing of the directory.
        assertEquals(2, tooMany.reads.size, "Read ${tooMany.reads}")
    }

    @Test
    fun `a directory of ZIP64 records is walked in time in proportion to its size`() = runTest {
        val all = setOf(UNCOMPRESSED_SIZE, COMPRESSED_SIZE, LOCAL_HEADER_OFFSET)
        assertTakesTimeInProportion(
            wholeSize = 64_000,
            inputOf = { count ->
                Zip64TestArchive(List(count) { Zip64TestEntry("$it.cho", byteArrayOf(1), marked = all, localZip64 = true) }, endEntries = 0xFFFF).bytes()
            },
            read = { archive ->
                ZipReader.read(
                    ByteArrayZipSource(archive),
                    maxTotalSize = Long.MAX_VALUE,
                    limitOf = { _, _ -> EntryPolicy.Sniff },
                    sniffedLimitOf = { _, _ -> Long.MAX_VALUE },
                )
            },
        )
    }

    @Test
    fun `the longest extra fields are walked in time in proportion to their size`() = runTest {
        // Each record's extra field is as long as the format allows, its ZIP64 block behind thousands of empty ones.
        val padding = ByteArrayBuilder().apply { repeat(16_380) { u16(0x5455); u16(0) } }.build()
        assertTakesTimeInProportion(
            wholeSize = 448,
            inputOf = { count ->
                val entries = List(count) {
                    Zip64TestEntry("$it.cho", byteArrayOf(1), marked = setOf(LOCAL_HEADER_OFFSET), centralExtra = padding + zip64Field(0))
                }
                Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL).bytes()
            },
            read = { archive -> ZipReader.open(ByteArrayZipSource(archive)) },
        )
    }

    /** A `0x0001` extra field holding [values]. */
    private fun zip64Field(vararg values: Long) = ByteArrayBuilder().apply {
        u16(1)
        u16(8 * values.size)
        values.forEach { u64(it) }
    }.build()
}
