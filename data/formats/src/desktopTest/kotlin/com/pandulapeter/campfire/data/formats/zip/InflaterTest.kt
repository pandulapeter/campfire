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
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.util.zip.Inflater as ZlibInflater

/**
 * Cross-checks the hand written inflater against the JVM's own deflater. `java.util.zip` is only available here, in a
 * JVM-only test source set; the production code stays pure common Kotlin.
 */
internal class InflaterTest {

    private val fixtures = mapOf(
        "empty" to ByteArray(0),
        "single byte" to "a".toByteArray(),
        "repeated abc" to "abc".repeat(300).toByteArray(),
        "random 2 MB" to Random(42).nextBytes(2 * 1024 * 1024),
        // Long repeats separated by more than 32 KB exercise the maximum back reference distance.
        "long repeats" to buildString {
            repeat(40) { round ->
                append("Campfire ChordPro fixture line $round: [Am]lorem ipsum dolor sit amet [C]consectetur.\n")
                append("x".repeat(1000))
                append('\n')
            }
        }.toByteArray(),
        "incompressible then compressible" to (Random(7).nextBytes(64 * 1024) + ByteArray(64 * 1024)),
    )

    @Test
    fun `inflates raw deflate at every level`() {
        for ((name, fixture) in fixtures) {
            for (level in 0..9) {
                val compressed = deflate(fixture, level, nowrap = true)
                assertContentEquals(fixture, Inflater.inflate(compressed), "$name at raw level $level")
                assertContentEquals(fixture, Inflater.inflate(compressed, 0, compressed.size, fixture.size), "$name at raw level $level with a known size")
            }
        }
    }

    @Test
    fun `inflates the deflate stream inside a zlib wrapper`() {
        for ((name, fixture) in fixtures) {
            for (level in 0..9) {
                // Skip the 2 byte zlib header and the 4 byte Adler-32 trailer: the inflater only handles raw deflate.
                val compressed = deflate(fixture, level, nowrap = false)
                val inflated = Inflater.inflate(compressed, 2, compressed.size - 6)
                assertContentEquals(fixture, inflated, "$name at wrapped level $level")
            }
        }
    }

    @Test
    fun `inflates at an offset inside a larger buffer`() {
        val fixture = fixtures.getValue("repeated abc")
        val compressed = deflate(fixture, 6, nowrap = true)
        val padded = ByteArray(17) { 0x7F } + compressed + ByteArray(23) { 0x2A }

        assertContentEquals(fixture, Inflater.inflate(padded, 17, compressed.size, fixture.size))
    }

    @Test
    fun `rejects garbage`() {
        assertFailsWith<ZipException> { Inflater.inflate(byteArrayOf(0x07)) } // Block type 3.
        assertFailsWith<ZipException> { Inflater.inflate(ByteArray(0)) } // Ends before the first block header.
        val truncated = deflate(fixtures.getValue("random 2 MB"), 9, nowrap = true)
        assertFailsWith<ZipException> { Inflater.inflate(truncated, 0, truncated.size / 2) }
    }

    @Test
    fun `rejects an unexpected output size`() {
        val fixture = fixtures.getValue("single byte")
        val compressed = deflate(fixture, 6, nowrap = true)

        assertFailsWith<ZipException> { Inflater.inflate(compressed, 0, compressed.size, 2) }
    }

    @Test
    fun `a prefix peek of a longer stream returns exactly the prefix`() {
        val cases = listOf(
            Triple("repeated abc", 0, STORED_BLOCK),
            Triple("repeated abc", 6, FIXED_BLOCK),
            Triple("long repeats", 6, DYNAMIC_BLOCK),
        )
        for ((name, level, blockType) in cases) {
            val fixture = fixtures.getValue(name)
            val compressed = deflate(fixture, level, nowrap = true)
            assertEquals(blockType, (compressed[0].toInt() shr 1) and 3, "the block type of $name at level $level")

            assertContentEquals(fixture.copyOf(512), Inflater.inflate(compressed, maxOutput = 512), "$name at level $level")
        }
    }

    @Test
    fun `a prefix peek of a shorter stream returns the whole stream`() {
        for (name in listOf("empty", "single byte", "repeated abc")) {
            val fixture = fixtures.getValue(name)
            val compressed = deflate(fixture, 6, nowrap = true)

            assertContentEquals(fixture, Inflater.inflate(compressed, maxOutput = 4096), name)
        }
    }

    @Test
    fun `a back reference across the prefix's end is cut, not refused`() {
        // After its first three literals the stream is back references of up to 258 bytes each.
        val fixture = fixtures.getValue("repeated abc")
        val compressed = deflate(fixture, 6, nowrap = true)

        assertContentEquals(fixture.copyOf(100), Inflater.inflate(compressed, maxOutput = 100))
    }

    @Test
    fun `generated data deflated by zlib at any level and strategy inflates back to it`() {
        repeat(ROUND_TRIPS) { seed ->
            val random = Random(seed)
            val data = generatedData(random)
            val level = random.nextInt(0, 10)
            val strategy = STRATEGIES[random.nextInt(STRATEGIES.size)]
            val compressed = deflate(data, level, nowrap = true, strategy = strategy)

            assertContentEquals(data, Inflater.inflate(compressed, expectedSize = data.size), "Seed $seed, level $level, strategy $strategy")
        }
    }

    /**
     * Which of the two refuses a damaged stream is not compared: this inflater takes a distance code of one symbol at any
     * length where zlib takes it only at length 1, and refuses an incomplete literal/length code zlib reads as a single
     * length 1 code, so a damaged stream may be read by one of them alone.
     */
    @Test
    fun `a damaged stream that zlib inflates too inflates to the same bytes`() = runTest {
        val streams = deflateStreamsToDamage(maxSize = 100_000)
        var compared = 0
        repeat(MUTATIONS) { seed ->
            val stream = Mutator(seed).mutate(streams[seed % streams.size])
            val inflated = try {
                Inflater.inflate(stream)
            } catch (_: ZipException) {
                null
            }
            val expected = zlibInflate(stream)
            if (inflated != null && expected != null) {
                assertContentEquals(expected, inflated, "Seed $seed")
                compared++
            }
        }
        assertTrue(compared >= MUTATIONS / 10, "Only $compared streams were read by both")
    }

    /** What zlib inflates [stream] to, or null where it refuses it, runs out of input, or passes [Inflater.MAX_ENTRY_SIZE]. */
    private fun zlibInflate(stream: ByteArray): ByteArray? {
        val inflater = ZlibInflater(true)
        try {
            inflater.setInput(stream)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
                output.write(buffer, 0, count)
                if (output.size() > Inflater.MAX_ENTRY_SIZE) return null
            }
            return output.toByteArray()
        } catch (_: DataFormatException) {
            return null
        } finally {
            inflater.end()
        }
    }

    private fun deflate(input: ByteArray, level: Int, nowrap: Boolean, strategy: Int = Deflater.DEFAULT_STRATEGY): ByteArray {
        val deflater = Deflater(level, nowrap)
        deflater.setStrategy(strategy)
        try {
            deflater.setInput(input)
            deflater.finish()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            // A strategy set before the first call takes effect with a call that returns nothing, so a call that writes no
            // bytes does not mean the stream is done.
            while (!deflater.finished()) {
                output.write(buffer, 0, deflater.deflate(buffer))
            }
            return output.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private companion object {
        const val STORED_BLOCK = 0
        const val FIXED_BLOCK = 1
        const val DYNAMIC_BLOCK = 2
        const val ROUND_TRIPS = 60
        const val MUTATIONS = 3_000
        val STRATEGIES = listOf(Deflater.DEFAULT_STRATEGY, Deflater.FILTERED, Deflater.HUFFMAN_ONLY)
    }
}
