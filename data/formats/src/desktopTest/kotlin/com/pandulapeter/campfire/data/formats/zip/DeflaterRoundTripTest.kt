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

import kotlinx.coroutines.runBlocking
import java.util.zip.Inflater
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/** Round trips through the JVM's zlib, which is the decoder every PDF viewer's Flate implementation and unzip tool agrees with. */
internal class DeflaterRoundTripTest {
    @Test fun `random bytes round trip`() = runBlocking {
        val input = Random(7).nextBytes(300_000)
        assertContentEquals(input, inflate(deflate(input), input.size))
    }

    @Test fun `white paper round trips and shrinks`() = runBlocking {
        val input = ByteArray(2 * 1024 * 1024) { -1 }
        val deflated = deflate(input)
        assertContentEquals(input, inflate(deflated, input.size))
        assertTrue(deflated.size < 20_000)
    }

    @Test fun `a match reaching back the whole window round trips`() = runBlocking {
        val block = Random(11).nextBytes(32_768)
        val input = block + block + Random(13).nextBytes(10)
        val deflated = deflate(input)
        assertContentEquals(input, inflate(deflated, input.size))
        assertTrue(deflated.size < 36_000, "The second copy should be found 32 768 bytes back.")
    }

    @Test fun `inked rows round trip`() = runBlocking {
        val random = Random(17)
        val input = ByteArray(500_000) { if (random.nextInt(10) == 0) random.nextInt(16).toByte() else -1 }
        assertContentEquals(input, inflate(deflate(input), input.size))
    }

    private suspend fun deflate(input: ByteArray) = ByteArrayBuilder().also { Deflater().deflate(input, it::u8) }.build()

    private fun inflate(stream: ByteArray, size: Int): ByteArray {
        // Raw deflate, as a zip entry holds it, with no zlib header or checksum around it.
        val inflater = Inflater(true)
        inflater.setInput(stream)
        val output = ByteArray(size + 1)
        var count = 0
        while (!inflater.finished() && count < output.size) count += inflater.inflate(output, count, output.size - count)
        assertTrue(inflater.finished() && inflater.remaining == 0)
        inflater.end()
        return output.copyOf(count)
    }
}
