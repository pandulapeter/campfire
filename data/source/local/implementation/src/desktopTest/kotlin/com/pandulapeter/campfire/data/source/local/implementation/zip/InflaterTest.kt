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

import java.util.zip.Deflater
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

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
        "incompressible then compressible" to (Random(7).nextBytes(64 * 1024) + ByteArray(64 * 1024))
    )

    @Test
    fun inflatesRawDeflateAtEveryLevel() {
        for ((name, fixture) in fixtures) {
            for (level in 0..9) {
                val compressed = deflate(fixture, level, nowrap = true)
                assertContentEquals(fixture, Inflater.inflate(compressed), "$name at raw level $level")
                assertContentEquals(fixture, Inflater.inflate(compressed, 0, compressed.size, fixture.size), "$name at raw level $level with a known size")
            }
        }
    }

    @Test
    fun inflatesTheDeflateStreamInsideAZlibWrapper() {
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
    fun inflatesAtAnOffsetInsideALargerBuffer() {
        val fixture = fixtures.getValue("repeated abc")
        val compressed = deflate(fixture, 6, nowrap = true)
        val padded = ByteArray(17) { 0x7F } + compressed + ByteArray(23) { 0x2A }

        assertContentEquals(fixture, Inflater.inflate(padded, 17, compressed.size, fixture.size))
    }

    @Test
    fun rejectsGarbage() {
        assertFailsWith<ZipException> { Inflater.inflate(byteArrayOf(0x07)) } // Block type 3.
        assertFailsWith<ZipException> { Inflater.inflate(ByteArray(0)) } // Ends before the first block header.
        val truncated = deflate(fixtures.getValue("random 2 MB"), 9, nowrap = true)
        assertFailsWith<ZipException> { Inflater.inflate(truncated, 0, truncated.size / 2) }
    }

    @Test
    fun rejectsAnUnexpectedOutputSize() {
        val fixture = fixtures.getValue("single byte")
        val compressed = deflate(fixture, 6, nowrap = true)

        assertFailsWith<ZipException> { Inflater.inflate(compressed, 0, compressed.size, 2) }
    }

    private fun deflate(input: ByteArray, level: Int, nowrap: Boolean): ByteArray {
        val deflater = Deflater(level, nowrap)
        try {
            deflater.setInput(input)
            deflater.finish()
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                if (count == 0 && !deflater.finished()) {
                    break
                }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        } finally {
            deflater.end()
        }
    }
}
