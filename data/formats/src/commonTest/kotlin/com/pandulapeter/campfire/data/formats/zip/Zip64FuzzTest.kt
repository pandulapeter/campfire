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

import com.pandulapeter.campfire.data.formats.zip.Zip64Field.COMPRESSED_SIZE
import com.pandulapeter.campfire.data.formats.zip.Zip64Field.LOCAL_HEADER_OFFSET
import com.pandulapeter.campfire.data.formats.zip.Zip64Field.UNCOMPRESSED_SIZE
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every ZIP64 fixture damaged, its ZIP64 structures above all: the streaming reader reads the right entries within the
 * budget or refuses the archive with a [ZipException], never failing in another way (a read outside the file included,
 * which [SparseZipSource] refuses with an [IllegalArgumentException]).
 */
internal class Zip64FuzzTest {

    @Test
    fun `damaged ZIP64 archives are read in part or refused`() = runTest {
        val archives = listOf(
            RealWriterArchives.INFO_ZIP_ZIP64.hexToByteArray(),
            RealWriterArchives.PYTHON_ZIP64.hexToByteArray(),
            RealWriterArchives.INFO_ZIP_ZIP64_STREAMED.hexToByteArray(),
        ) + builtArchives().map { it.bytes() }
        repeat(MUTATIONS) { seed ->
            val mutator = Mutator(seed)
            val original = archives[seed % archives.size]
            val archive = if (seed % 3 == 0) mutator.mutate(original) else damageZip64(mutator, original)
            assertReadInPartOrRefused(ByteArrayZipSource(archive), mutator, "Seed $seed")
        }
    }

    @Test
    fun `damaged ZIP64 archives past 4 GiB are read in part or refused`() = runTest {
        val archives = listOf(
            RealWriterArchives.DITTO_LARGE,
            RealWriterArchives.INFO_ZIP_LARGE,
            RealWriterArchives.PYTHON_LARGE,
            RealWriterArchives.JAVA_LARGE,
        )
        repeat(MUTATIONS / 3) { seed ->
            val mutator = Mutator(seed)
            val original = archives[seed % archives.size]
            val (head, tail) = original.pieces.map { (offset, hex) -> offset to hex.hexToByteArray() }
            val damagedTail = if (seed % 3 == 0) mutator.mutate(tail.second) else damageZip64(mutator, tail.second)
            val source = SparseZipSource(tail.first + damagedTail.size, listOf(head, tail.first to damagedTail))
            assertReadInPartOrRefused(source, mutator, "Seed $seed")
        }
    }

    private suspend fun assertReadInPartOrRefused(source: ZipSource, mutator: Mutator, message: String) {
        val budget = mutator.random.nextLong(0, 20_000)
        val limits = mutableMapOf<String, Long>()
        val content = try {
            ZipReader.read(
                source = source,
                maxTotalSize = budget,
                limitOf = { name, _ ->
                    when (mutator.random.nextInt(4)) {
                        0 -> EntryPolicy.Skip
                        1 -> EntryPolicy.Sniff
                        else -> EntryPolicy.Read(mutator.random.nextLong(0, 10_000).also { limits[name] = maxOf(limits[name] ?: 0, it) })
                    }
                },
                sniffedLimitOf = { name, prefix ->
                    assertTrue(prefix.size <= 512, "$message peeked at ${prefix.size} bytes")
                    if (mutator.random.nextBoolean()) null else mutator.random.nextLong(0, 10_000).also { limits[name] = maxOf(limits[name] ?: 0, it) }
                },
            )
        } catch (_: ZipException) {
            null
        } catch (exception: Throwable) {
            fail("$message failed with $exception", exception)
        }
        if (content != null) {
            assertTrue(content.chargedSize <= budget && content.entries.sumOf { it.bytes.size.toLong() } <= budget, "$message read past its budget")
            content.entries.forEach { assertTrue(it.bytes.size <= (limits[it.name] ?: -1), "$message read ${it.name} past its limit") }
        }
    }

    /**
     * Overwrites the size, offset, count, and disk fields of the end record, the ZIP64 record and locator, the central
     * and local headers (their `0x0001` fields' values and lengths too), and the data descriptors, 64-bit ones with
     * 64-bit values.
     */
    private fun damageZip64(mutator: Mutator, archive: ByteArray): ByteArray {
        val bytes = archive.copyOf()
        val fields = mutableListOf<Pair<Int, Int>>()
        for (offset in 0..bytes.size - 4) {
            when (bytes.u32(offset)) {
                0x06054B50L -> listOf(4 to 2, 6 to 2, 8 to 2, 10 to 2, 12 to 4, 16 to 4)
                0x06064B50L -> listOf(4 to 8, 16 to 4, 20 to 4, 24 to 8, 32 to 8, 40 to 8, 48 to 8)
                0x07064B50L -> listOf(4 to 4, 8 to 8, 16 to 4)
                0x08074B50L -> listOf(8 to 8, 16 to 8)
                0x02014B50L -> listOf(20 to 4, 24 to 4, 28 to 2, 30 to 2, 42 to 4) + zip64FieldsOf(bytes, offset, 46, 28)
                0x04034B50L -> listOf(18 to 4, 22 to 4, 26 to 2, 28 to 2) + zip64FieldsOf(bytes, offset, 30, 26)
                else -> emptyList()
            }.forEach { (at, width) -> fields += offset + at to width }
        }
        repeat(1 + mutator.random.nextInt(3)) {
            val (offset, width) = fields[mutator.random.nextInt(fields.size)]
            if (width == 8) mutator.overwriteLongField(bytes, offset) else mutator.overwriteField(bytes, offset, width)
        }
        return bytes
    }

    /** The length and values of the `0x0001` block in the extra field of the header at [header], relative to it. */
    private fun zip64FieldsOf(bytes: ByteArray, header: Int, fixedSize: Int, nameLengthAt: Int): List<Pair<Int, Int>> {
        if (header + fixedSize > bytes.size) return emptyList()
        val extraStart = fixedSize + bytes.u16(header + nameLengthAt)
        val extraEnd = minOf(extraStart + bytes.u16(header + nameLengthAt + 2), bytes.size - header)
        var position = extraStart
        while (position + 4 <= extraEnd) {
            val length = bytes.u16(header + position + 2)
            if (bytes.u16(header + position) == 1) {
                return listOf(position + 2 to 2) + (0 until length / 8).map { position + 4 + 8 * it to 8 }
            }
            position += 4 + length
        }
        return emptyList()
    }

    /** ZIP64 archives as the real writers lay them out, built in the test, every field ZIP64 can move moved. */
    private fun builtArchives(): List<Zip64TestArchive> {
        val all = setOf(UNCOMPRESSED_SIZE, COMPRESSED_SIZE, LOCAL_HEADER_OFFSET)
        val entries = List(5) {
            Zip64TestEntry(
                name = "song $it.cho",
                data = "{title: Song $it}\n".repeat(it * 3).encodeToByteArray(),
                marked = if (it % 2 == 0) all else setOf(UNCOMPRESSED_SIZE),
                localZip64 = it == 1,
                descriptor = Zip64TestEntry.Descriptor.entries.getOrNull(it - 3),
            )
        }
        return listOf(
            Zip64TestArchive(entries, endOffset = 0xFFFFFFFFL),
            Zip64TestArchive(entries, endEntries = 0xFFFF, locatorDisks = 0),
            Zip64TestArchive(entries.map { Zip64TestEntry(it.name, it.data) }, endEntries = 0xFFFF),
        )
    }

    private companion object {
        const val MUTATIONS = 3_000
    }
}

/** The whole of an archive built at offset 0 and without gaps, as one array. */
internal suspend fun Zip64TestArchive.bytes(): ByteArray {
    val source = source()
    return ByteArray(source.size.toInt()).also { source.read(0, it, 0, it.size) }
}
