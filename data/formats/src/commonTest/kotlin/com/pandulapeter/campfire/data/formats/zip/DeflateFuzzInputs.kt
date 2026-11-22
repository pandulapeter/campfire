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

import kotlin.random.Random

/**
 * Up to [maxSize] bytes of what an archive's entry may hold, picked by [random]: nothing, noise, short runs, lyrics with
 * chords in two scripts, or blocks repeated as far back as deflate reaches.
 */
internal fun generatedData(random: Random, maxSize: Int = 100_000): ByteArray = when (random.nextInt(5)) {
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

/** The deflate streams the fuzz tests damage: zlib's dynamic block, and [Deflater]'s of four generated inputs. */
internal suspend fun deflateStreamsToDamage(maxSize: Int): List<ByteArray> = listOf(DYNAMIC_STREAM.hexToByteArray()) + List(4) { seed ->
    ByteArrayBuilder().also { Deflater().deflate(generatedData(Random(seed), maxSize), it::u8) }.build()
}

private val WORDS = listOf("[Am]", "[C]", "[G/B]", "the ", "river ", "light ", "{chorus}\n", "\n", "árvíztűrő ", "שלום ")

/** A raw DEFLATE stream with a dynamic Huffman block, which [Deflater] never writes: zlib's at level 9. */
private const val DYNAMIC_STREAM =
    "5d51c10d83300cfc7b8aac52f1600894178d1aa4b648819647d5213246e688b2571d5f4c050f08e73bdfc566f5ce5ce7ed69fcfc7066e8" +
        "ed7dbaf91525dabc0b8d1e3a8bf3f2b0255298de2ea067f57846068b21a99528077d463f87d7f2855189ec5fa5800c9a4e65b0a81150e4" +
        "c4b12ccb69cf463013728f12814978b41fcd72527cae63c47de29ac49e8ca95e11beb88676e6a47380d57a1b59a118c35418c107b20640" +
        "d03621c3c0540428b775f4161f259e4df5cf8047d37fbf250a0f5bbceb909da512a12006f0944dff00"
