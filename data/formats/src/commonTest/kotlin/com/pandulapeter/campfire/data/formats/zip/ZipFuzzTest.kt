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
            val read = ZipReader.read(ZipWriter.write(entries), maxTotalSize = Long.MAX_VALUE)
            assertEquals(emptyList(), read.unread, "Seed $seed")
            assertEquals(entries.map { it.name }, read.entries.map { it.name }, "Seed $seed")
            entries.forEachIndexed { index, entry -> assertContentEquals(entry.bytes, read.entries[index].bytes, "Seed $seed, entry ${entry.name}") }
        }
    }

    @Test
    fun `generated data inflates back to what was deflated`() = runTest {
        repeat(ROUND_TRIPS) { seed ->
            val data = data(Random(seed))
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
                ZipReader.read(
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
        val streams = listOf(DYNAMIC_STREAM.hexToByteArray()) + List(4) { seed ->
            ByteArrayBuilder().also { Deflater().deflate(data(Random(seed), maxSize = 4_000), it::u8) }.build()
        }
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
        return ZipEntry(name, data(random, maxSize))
    }

    private fun data(random: Random, maxSize: Int = 100_000): ByteArray = when (random.nextInt(5)) {
        0 -> ByteArray(0)
        1 -> random.nextBytes(random.nextInt(maxSize))
        2 -> ByteArray(random.nextInt(maxSize)) { (it % (1 + random.nextInt(3))).toByte() }
        3 -> buildString { while (length < maxSize / 2 && random.nextInt(200) != 0) append(WORDS[random.nextInt(WORDS.size)]) }.encodeToByteArray()
        else -> {
            // A block repeated across the whole window and beyond, with noise between, reaches back as far as deflate can.
            val block = random.nextBytes(random.nextInt(1, 2_000))
            val builder = ByteArrayBuilder()
            while (builder.size < maxSize - block.size - 64) {
                builder.bytes(block)
                builder.bytes(random.nextBytes(random.nextInt(64)))
                if (random.nextInt(20) == 0) builder.bytes(ByteArray(minOf(maxSize - builder.size, random.nextInt(40_000))))
            }
            builder.build().copyOf(minOf(builder.size, maxSize))
        }
    }

    private companion object {
        const val ROUND_TRIPS = 60
        const val MUTATIONS = 3_000
        val NAME_CHARACTERS = listOf("a", "Z", "0", " ", "-", "_", ".", "é", "ő", "Ü", "ß", "日本", "😀", "א", "#", "&", "'")
        val WORDS = listOf("[Am]", "[C]", "[G/B]", "the ", "river ", "light ", "{chorus}\n", "\n", "árvíztűrő ", "שלום ")

        /** A raw DEFLATE stream with a dynamic Huffman block, which [Deflater] never writes: zlib's at level 9. */
        const val DYNAMIC_STREAM =
            "5d51c10d83300cfc7b8aac52f1600894178d1aa4b648819647d5213246e688b2571d5f4c050f08e73bdfc566f5ce5ce7ed69fcfc7066e8" +
                "ed7dbaf91525dabc0b8d1e3a8bf3f2b0255298de2ea067f57846068b21a99528077d463f87d7f2855189ec5fa5800c9a4e65b0a81150e4" +
                "c4b12ccb69cf463013728f12814978b41fcd72527cae63c47de29ac49e8ca95e11beb88676e6a47380d57a1b59a118c35418c107b20640" +
                "d03621c3c0540428b775f4161f259e4df5cf8047d37fbf250a0f5bbceb909da512a12006f0944dff00"
    }
}
