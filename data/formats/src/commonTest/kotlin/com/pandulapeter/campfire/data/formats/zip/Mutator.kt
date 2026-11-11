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
 * Damages a valid document the way a hostile or broken file differs from one: bits flipped, bytes inserted, cut or
 * repeated, the file truncated, and the length, offset, and count fields every format here is built from overwritten
 * with the values a reader is least prepared for. Seeded, so a failure names the input that caused it.
 */
internal class Mutator(seed: Int) {

    val random = Random(seed)

    /** One to [maxMutations] damages applied to a copy of [input], each picked at random. */
    fun mutate(input: ByteArray, maxMutations: Int = 4): ByteArray {
        var bytes = input
        repeat(1 + random.nextInt(maxMutations)) { bytes = mutateOnce(bytes) }
        return bytes
    }

    /** Overwrites the little-endian field of [width] bytes at [offset] with a hostile value, where it fits. */
    fun overwriteField(bytes: ByteArray, offset: Int, width: Int) {
        if (offset < 0 || offset + width > bytes.size) return
        val value = hostileValue(bytes.size)
        for (index in 0 until width) bytes[offset + index] = (value ushr (8 * index)).toByte()
    }

    private fun mutateOnce(input: ByteArray): ByteArray {
        if (input.isEmpty()) return random.nextBytes(1 + random.nextInt(64))
        val bytes = input.copyOf()
        val at = random.nextInt(bytes.size)
        return when (random.nextInt(10)) {
            0 -> bytes.also { it[at] = (it[at].toInt() xor (1 shl random.nextInt(8))).toByte() }
            1 -> bytes.also { it[at] = random.nextInt(256).toByte() }
            2 -> bytes.copyOfRange(0, at) + random.nextBytes(1 + random.nextInt(16)) + bytes.copyOfRange(at, bytes.size)
            3 -> bytes.copyOfRange(0, at) + bytes.copyOfRange(minOf(bytes.size, at + 1 + random.nextInt(64)), bytes.size)
            4 -> bytes.copyOfRange(0, at)
            5 -> {
                // A range repeated in place, which makes duplicate headers, directory entries, and objects.
                val end = minOf(bytes.size, at + 1 + random.nextInt(256))
                bytes.copyOfRange(0, end) + bytes.copyOfRange(at, end) + bytes.copyOfRange(end, bytes.size)
            }
            6, 7 -> bytes.also { overwriteField(it, at, if (random.nextBoolean()) 2 else 4) }
            8 -> {
                // A range copied over another, so that a header points at what another one holds.
                val from = random.nextInt(bytes.size)
                val length = minOf(1 + random.nextInt(64), bytes.size - from, bytes.size - at)
                bytes.copyInto(bytes, at, from, from + length)
            }
            else -> bytes.copyOfRange(0, at) + ByteArray(random.nextInt(1, 32)) { bytes[at] } + bytes.copyOfRange(at, bytes.size)
        }
    }

    private fun hostileValue(size: Int): Long = when (random.nextInt(9)) {
        0 -> 0L
        1 -> 0xFFFFL
        2 -> 0xFFFFFFFFL
        3 -> 0x7FFFFFFFL
        4 -> 0x80000000L
        5 -> (size + random.nextInt(-4, 5)).toLong()
        6 -> random.nextInt(size + 1).toLong()
        7 -> 1L
        else -> random.nextLong(0, 0x100000000L)
    }
}
