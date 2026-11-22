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
import java.util.zip.Deflater
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

/** The streaming inflater against zlib's streams at every level: stored, fixed, and dynamic blocks alike. */
internal class StreamingInflaterJvmTest {

    private val fixtures = mapOf(
        "empty" to ByteArray(0),
        "single byte" to "a".toByteArray(),
        "repeated abc" to "abc".repeat(300).toByteArray(),
        "random 2 MB" to Random(42).nextBytes(2 * 1024 * 1024),
        "long repeats" to buildString {
            repeat(400) { round ->
                append("Campfire ChordPro fixture line $round: [Am]lorem ipsum dolor sit amet [C]consectetur.\n")
                append("x".repeat(1000))
                append('\n')
            }
        }.toByteArray(),
        "incompressible then compressible" to (Random(7).nextBytes(64 * 1024) + ByteArray(256 * 1024)),
        "a block a window apart" to Random(9).nextBytes(32 * 1024).let { it + ByteArray(100) + it + it },
    )

    @Test
    fun `inflates zlib's raw deflate at every level to what was deflated`() = runTest {
        for ((name, fixture) in fixtures) {
            for (level in 0..9) {
                val compressed = deflate(fixture, level)
                val sink = RecordingEntrySink()

                Inflater.inflate(ByteArrayZipSource(compressed), 0, compressed.size.toLong(), fixture.size.toLong(), Crc32.of(fixture), sink)

                assertContentEquals(fixture, sink.bytes, "$name at level $level")
            }
        }
    }

    @Test
    fun `refuses a truncated stream`() = runTest {
        val fixture = fixtures.getValue("long repeats")
        val compressed = deflate(fixture, 9)
        for (length in listOf(0, 1, compressed.size / 2, compressed.size - 1)) {
            assertFailsWith<ZipException> {
                Inflater.inflate(ByteArrayZipSource(compressed), 0, length.toLong(), fixture.size.toLong(), Crc32.of(fixture), RecordingEntrySink())
            }
        }
    }

    private fun deflate(input: ByteArray, level: Int): ByteArray {
        val deflater = Deflater(level, true)
        try {
            deflater.setInput(input)
            deflater.finish()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (!deflater.finished()) {
                output.write(buffer, 0, deflater.deflate(buffer))
            }
            return output.toByteArray()
        } finally {
            deflater.end()
        }
    }
}
