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
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Generated archives and deflate streams: written ones read back as they were written, and damaged ones are read,
 * partly read, or refused with a [ZipException], never failing in any other way and never reading more than the budget.
 */
internal class ZipFuzzTest {

    @Test
    fun `generated entries read back as they were written`() = runTest {
        repeat(ROUND_TRIPS) { seed ->
            val random = Random(seed)
            val isMany = seed % 10 == 0
            val entries = List(if (isMany) random.nextInt(500, 2_000) else random.nextInt(0, 40)) { entry(random, it, maxSize = if (isMany) 2_000 else 100_000) }
            val read = readArchive(ZipWriter.write(entries), maxTotalSize = Long.MAX_VALUE)
            assertEquals(emptyList(), read.unread, "Seed $seed")
            assertEquals(entries.map { it.name }, read.entries.map { it.name }, "Seed $seed")
            entries.forEachIndexed { index, entry -> assertContentEquals(entry.bytes, read.entries[index].bytes, "Seed $seed, entry ${entry.name}") }
        }
    }

    @Test
    fun `generated data inflates back to what was deflated`() = runTest {
        repeat(ROUND_TRIPS) { seed ->
            val data = generatedData(Random(seed))
            val deflated = ByteArrayBuilder().also { Deflater().deflate(data, it::u8) }.build()
            assertContentEquals(data, Inflater.inflate(deflated, expectedSize = data.size), "Seed $seed")
        }
    }

    @Test
    fun `damaged archives are read in part or refused`() = runTest {
        val archives = List(8) { seed ->
            val random = Random(seed)
            val entries = List(random.nextInt(1, 8)) { entry(random, it, maxSize = 4_000) }
            ZipWriter.write(if (seed % 4 == 3) entries + ZipEntry("nested.zip", ZipWriter.write(entries)) else entries)
        }
        repeat(MUTATIONS) { seed ->
            val mutator = Mutator(seed)
            val original = archives[seed % archives.size]
            val archive = if (seed % 3 == 0) damageHeaders(mutator, original) else mutator.mutate(original)
            val budget = mutator.random.nextLong(0, 20_000)
            val limits = mutableMapOf<String, Long>()
            val content = try {
                readArchive(
                    archive = archive,
                    maxTotalSize = budget,
                    limitOf = { name, _ ->
                        when (mutator.random.nextInt(4)) {
                            0 -> EntryPolicy.Skip
                            1 -> EntryPolicy.Sniff
                            else -> EntryPolicy.Read(mutator.random.nextLong(0, 10_000).also { limits[name] = maxOf(limits[name] ?: 0, it) })
                        }
                    },
                    sniffedLimitOf = { name, prefix ->
                        assertTrue(prefix.size <= 512, "Seed $seed peeked at ${prefix.size} bytes")
                        if (mutator.random.nextBoolean()) null else mutator.random.nextLong(0, 10_000).also { limits[name] = maxOf(limits[name] ?: 0, it) }
                    },
                )
            } catch (_: ZipException) {
                null
            } catch (exception: Throwable) {
                fail("Seed $seed failed with $exception", exception)
            }
            if (content != null) {
                assertTrue(content.chargedSize <= budget && content.entries.sumOf { it.bytes.size.toLong() } <= budget, "Seed $seed read past its budget")
                content.entries.forEach { assertTrue(it.bytes.size <= (limits[it.name] ?: -1), "Seed $seed read ${it.name} past its limit") }
            }
        }
    }

    @Test
    fun `damaged deflate streams inflate or are refused`() = runTest {
        val streams = deflateStreamsToDamage(maxSize = 4_000)
        repeat(MUTATIONS) { seed ->
            val mutator = Mutator(seed)
            val stream = mutator.mutate(streams[seed % streams.size])
            val expectedSize = when (mutator.random.nextInt(3)) {
                0 -> -1
                1 -> mutator.random.nextInt(0, 10_000)
                else -> mutator.random.nextInt(0, Int.MAX_VALUE)
            }
            val maxOutput = if (mutator.random.nextInt(4) == 0) mutator.random.nextInt(0, 1_000) else -1
            val output = try {
                Inflater.inflate(stream, expectedSize = expectedSize, maxOutput = maxOutput)
            } catch (_: ZipException) {
                null
            } catch (exception: Throwable) {
                fail("Seed $seed failed with $exception", exception)
            }
            if (output != null) when {
                maxOutput >= 0 -> assertTrue(output.size <= maxOutput, "Seed $seed")
                expectedSize >= 0 -> assertEquals(expectedSize, output.size, "Seed $seed")
                else -> assertTrue(output.size <= Inflater.MAX_ENTRY_SIZE, "Seed $seed")
            }
        }
    }

    @Test
    fun `damaged deflate streams inflate to one output however they are read`() = runTest {
        val streams = deflateStreamsToDamage(maxSize = 100_000)
        repeat(MUTATIONS) { seed ->
            val mutator = Mutator(seed)
            val stream = mutator.mutate(streams[seed % streams.size])
            val maxOutput = mutator.random.nextInt(0, 1_000)
            val prefix = inflateOrRefuse("Seed $seed, a prefix of $maxOutput") { Inflater.inflate(stream, maxOutput = maxOutput) }
            val whole = inflateOrRefuse("Seed $seed") { Inflater.inflate(stream) }
            if (prefix != null && whole != null) {
                assertContentEquals(whole.copyOf(minOf(maxOutput, whole.size)), prefix, "Seed $seed, a prefix of $maxOutput")
            }
            // The size the stream really inflates to, where it does, so that the whole of it is read into a sink as often as not.
            val declaredSize = if (whole != null && mutator.random.nextBoolean()) whole.size else mutator.random.nextInt(0, 200_000)
            val sink = RecordingEntrySink()
            val streamed = inflateOrRefuse("Seed $seed, $declaredSize bytes declared") {
                Inflater.inflate(ByteArrayZipSource(stream), 0, stream.size.toLong(), declaredSize.toLong(), whole?.let(Crc32::of) ?: 0, sink)
                sink.bytes
            }
            if (whole != null && declaredSize == whole.size) assertContentEquals(whole, streamed, "Seed $seed, $declaredSize bytes declared")
            assertTrue(sink.isAbandoned != (sink.sha256Hex != null), "Seed $seed ended its sink ${if (sink.isAbandoned) "twice" else "never"}")
        }
    }

    /** What [inflate] returns, or null where it refuses the stream with a [ZipException], the only way it may fail. */
    private inline fun inflateOrRefuse(message: String, inflate: () -> ByteArray): ByteArray? = try {
        inflate()
    } catch (_: ZipException) {
        null
    } catch (exception: Throwable) {
        fail("$message failed with $exception", exception)
    }

    /** Overwrites the size, offset, and count fields of the end record, the central directory, and the local headers. */
    private fun damageHeaders(mutator: Mutator, archive: ByteArray): ByteArray {
        val bytes = archive.copyOf()
        val fields = mutableListOf<Pair<Int, Int>>()
        for (offset in 0..bytes.size - 4) {
            when (bytes.u32(offset)) {
                0x06054B50L -> listOf(8 to 2, 10 to 2, 12 to 4, 16 to 4, 20 to 2)
                0x02014B50L -> listOf(8 to 2, 10 to 2, 20 to 4, 24 to 4, 28 to 2, 30 to 2, 32 to 2, 42 to 4)
                0x04034B50L -> listOf(6 to 2, 8 to 2, 18 to 4, 22 to 4, 26 to 2, 28 to 2)
                else -> emptyList()
            }.forEach { (at, width) -> fields += offset + at to width }
        }
        repeat(1 + mutator.random.nextInt(3)) {
            val (offset, width) = fields[mutator.random.nextInt(fields.size)]
            mutator.overwriteField(bytes, offset, width)
        }
        return bytes
    }

    private fun entry(random: Random, index: Int, maxSize: Int = 100_000): ZipEntry {
        val name = buildString {
            repeat(random.nextInt(1, 4)) { part ->
                if (part > 0) append('/')
                repeat(random.nextInt(1, 12)) { append(NAME_CHARACTERS[random.nextInt(NAME_CHARACTERS.size)]) }
            }
            append(index)
            append(".cho")
        }
        return ZipEntry(name, generatedData(random, maxSize))
    }

    private companion object {
        const val ROUND_TRIPS = 60
        const val MUTATIONS = 3_000
        val NAME_CHARACTERS = listOf("a", "Z", "0", " ", "-", "_", ".", "é", "ő", "Ü", "ß", "日本", "😀", "א", "#", "&", "'")
    }
}
