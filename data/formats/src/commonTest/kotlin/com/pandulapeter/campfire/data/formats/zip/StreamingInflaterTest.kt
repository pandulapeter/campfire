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
import com.pandulapeter.campfire.data.model.hashing.Sha256
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class StreamingInflaterTest {

    @Test
    fun `stored blocks inflate to the same bytes at every buffer boundary`() = runTest {
        // Sizes on both sides of the output window's halves and the input buffer's end (a 65,531 byte block is a 65,536
        // byte stream), so a stream ends exactly on a boundary of each.
        val sizes = listOf(0, 1, 32_767, 32_768, 32_769, 65_530, 65_531, 65_532, 65_535, 65_536, 65_537, 131_072, 200_003)
        for (size in sizes) {
            val data = Random(size).nextBytes(size)
            val stream = storedStream(data, final = true)

            assertContentEquals(Inflater.inflate(stream, expectedSize = size), inflateStreaming(stream, data), "$size bytes")
        }
    }

    @Test
    fun `fixed blocks written by the deflater inflate to the same bytes`() = runTest {
        val inputs = listOf(
            ByteArray(0),
            "a".encodeToByteArray(),
            "abc".repeat(30_000).encodeToByteArray(),
            ByteArray(1 shl 20),
            Random(3).nextBytes(100_000),
            // A block repeated exactly a window apart, which the deflater reaches back the whole 32,768 bytes for.
            Random(4).nextBytes(32_768).let { it + it + it },
        )
        for (data in inputs) {
            val stream = deflate(data)

            assertContentEquals(Inflater.inflate(stream, expectedSize = data.size), inflateStreaming(stream, data), "${data.size} bytes")
        }
    }

    @Test
    fun `back references of 32,768 bytes reach across the window's slide`() = runTest {
        val literals = Random(5).nextBytes(70_000)
        val stream = storedStream(literals, final = false) + farBackReferences(count = 300)
        val expected = Inflater.inflate(stream)
        assertEquals(literals.size + 300 * 258, expected.size)
        for (index in literals.size until expected.size) {
            assertEquals(expected[index - 32_768], expected[index])
        }

        assertContentEquals(expected, inflateStreaming(stream, expected))
    }

    @Test
    fun `a closed entry names the SHA-256 of exactly its bytes`() = runTest {
        val data = "{title: Hello}\n".repeat(10_000).encodeToByteArray()
        val stream = deflate(data)
        val sink = RecordingEntrySink()

        Inflater.inflate(ByteArrayZipSource(stream), 0, stream.size.toLong(), data.size.toLong(), Crc32.of(data), sink)

        assertEquals(Sha256.hashToHex(data), sink.sha256Hex)
        assertFalse(sink.isAbandoned)
    }

    @Test
    fun `a stream producing more or fewer bytes than declared, or another checksum, abandons its sink`() = runTest {
        val data = Random(6).nextBytes(100_000)
        val stream = deflate(data)
        val cases = listOf(
            data.size - 1L to Crc32.of(data),
            data.size + 1L to Crc32.of(data),
            0L to Crc32.of(data),
            data.size.toLong() to (Crc32.of(data) xor 1),
        )
        for ((declaredSize, crc) in cases) {
            val sink = RecordingEntrySink()

            assertFailsWith<ZipException> {
                Inflater.inflate(ByteArrayZipSource(stream), 0, stream.size.toLong(), declaredSize, crc, sink)
            }
            assertNull(sink.sha256Hex, "Declared $declaredSize")
            assertTrue(sink.isAbandoned, "Declared $declaredSize")
            assertTrue(sink.bytes.size <= declaredSize, "Declared $declaredSize")
        }
    }

    @Test
    fun `a range outside the source abandons its sink`() = runTest {
        val stream = deflate("hello".encodeToByteArray())
        for ((offset, length) in listOf(1L to stream.size.toLong(), -1L to 2L, 0L to -1L, Long.MAX_VALUE to 1L)) {
            val sink = RecordingEntrySink()

            assertFailsWith<ZipException> { Inflater.inflate(ByteArrayZipSource(stream), offset, length, 5, 0, sink) }
            assertTrue(sink.isAbandoned)
        }
    }

    @Test
    fun `cancelling mid-entry abandons the sink`() = runTest {
        val data = Random(7).nextBytes(300_000)
        val stream = deflate(data)
        val written = CompletableDeferred<Unit>()
        val sink = RecordingEntrySink {
            written.complete(Unit)
            awaitCancellation()
        }
        val job = launch { Inflater.inflate(ByteArrayZipSource(stream), 0, stream.size.toLong(), data.size.toLong(), Crc32.of(data), sink) }

        written.await()
        job.cancelAndJoin()

        assertTrue(sink.isAbandoned)
        assertNull(sink.sha256Hex)
    }

    @Test
    fun `a large entry gives way to other work while it inflates`() = runTest {
        val data = ByteArray(4 shl 20) { (it % 251).toByte() }
        val stream = deflate(data)
        var isDone = false
        var turns = 0

        joinAll(
            launch {
                inflateStreaming(stream, data)
                isDone = true
            },
            launch {
                while (!isDone) {
                    turns++
                    yield()
                }
            },
        )

        assertTrue(turns >= 8, "The other work ran $turns times")
    }

    @Test
    fun `a prefix is what the in-memory inflater peeks`() = runTest {
        val inputs = listOf(ByteArray(0), "abc".repeat(1_000).encodeToByteArray(), Random(8).nextBytes(100_000))
        for (data in inputs) {
            for (stream in listOf(deflate(data), storedStream(data, final = true))) {
                for (maxOutput in listOf(0, 1, 100, 512, 70_000, 200_000)) {
                    assertContentEquals(
                        Inflater.inflate(stream, maxOutput = maxOutput),
                        Inflater.inflatePrefix(ByteArrayZipSource(stream), 0, stream.size.toLong(), maxOutput),
                        "${data.size} bytes, $maxOutput wanted",
                    )
                }
            }
        }
    }

    @Test
    fun `a highly repetitive stream inflates in time proportional to its output`() = runTest {
        assertTakesTimeInProportion(
            wholeSize = 16 shl 20,
            inputOf = { size -> ByteArray(size).let { it to deflate(it) } },
            read = { (data, stream) -> inflateStreaming(stream, data) },
        )
    }

    private suspend fun inflateStreaming(stream: ByteArray, expected: ByteArray): ByteArray {
        val sink = RecordingEntrySink()
        Inflater.inflate(ByteArrayZipSource(stream), 0, stream.size.toLong(), expected.size.toLong(), Crc32.of(expected), sink)
        assertTrue(sink.sha256Hex != null && !sink.isAbandoned)
        return sink.bytes
    }

    private suspend fun deflate(data: ByteArray) = ByteArrayBuilder().also { Deflater().deflate(data, it::u8) }.build()

    /** [data] as stored blocks of at most 65,535 bytes, the last one marked final where [final] is set. */
    private fun storedStream(data: ByteArray, final: Boolean): ByteArray {
        val builder = ByteArrayBuilder()
        var offset = 0
        do {
            val length = minOf(65_535, data.size - offset)
            builder.u8(if (final && offset + length == data.size) 1 else 0)
            builder.u16(length)
            builder.u16(length.inv() and 0xFFFF)
            builder.bytes(data, offset, length)
            offset += length
        } while (offset < data.size)
        return builder.build()
    }

    /** A final fixed block of [count] back references of 258 bytes from 32,768 bytes back, the farthest there is. */
    private fun farBackReferences(count: Int): ByteArray {
        val bits = BitWriter()
        bits.write(1, 1)
        bits.write(1, 2)
        repeat(count) {
            bits.writeCode(0xC5, 8) // Length symbol 285: 258 bytes.
            bits.writeCode(29, 5) // Distance symbol 29: 24,577 and 13 extra bits.
            bits.write(8_191, 13)
        }
        bits.writeCode(0, 7) // End of block.
        return bits.build()
    }

    /** Packs bits least significant first, and Huffman codes most significant bit first, as DEFLATE does. */
    private class BitWriter {
        private val builder = ByteArrayBuilder()
        private var buffer = 0
        private var count = 0

        fun write(value: Int, length: Int) {
            buffer = buffer or (value shl count)
            count += length
            while (count >= 8) {
                builder.u8(buffer)
                buffer = buffer ushr 8
                count -= 8
            }
        }

        fun writeCode(code: Int, length: Int) {
            var reversed = 0
            for (bit in 0 until length) {
                reversed = reversed or (((code ushr bit) and 1) shl (length - 1 - bit))
            }
            write(reversed, length)
        }

        fun build(): ByteArray {
            if (count > 0) builder.u8(buffer)
            return builder.build()
        }
    }
}
