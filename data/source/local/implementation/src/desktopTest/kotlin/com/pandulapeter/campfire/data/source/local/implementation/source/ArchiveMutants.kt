/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import kotlin.random.Random

/**
 * Damaged copies of an archive from a fixed seed, aimed where the reader has the most to decide: a byte flipped
 * anywhere, bytes flipped in the headers near the end (the central directory and the end records, where most of an
 * archive's numbers are), a header field set to the ZIP64 marker, a field zeroed, and the archive cut short.
 */
internal object ArchiveMutants {

    fun of(archive: ArchiveCase, count: Int, seed: Int): List<ArchiveCase> {
        val random = Random(seed)
        return (0 until count).map { index ->
            val bytes = archive.bytes.copyOf()
            val kind = index % KINDS
            val tail = maxOf(0, bytes.size - minOf(bytes.size, TAIL))
            when (kind) {
                0 -> repeat(1 + random.nextInt(3)) { bytes.flip(random.nextInt(bytes.size), random) }
                1 -> repeat(1 + random.nextInt(4)) { bytes.flip(tail + random.nextInt(bytes.size - tail), random) }
                2 -> bytes.fill(tail + random.nextInt(bytes.size - tail), 0xFF.toByte(), length = 2 + 2 * random.nextInt(2))
                3 -> bytes.fill(tail + random.nextInt(bytes.size - tail), 0, length = 2 + 2 * random.nextInt(2))
            }
            val mutated = if (kind == 4) bytes.copyOf(random.nextInt(bytes.size)) else bytes
            ArchiveCase(label = "${archive.label}, mutant $index", name = archive.name, bytes = mutated)
        }
    }

    private fun ByteArray.flip(position: Int, random: Random) {
        this[position] = (this[position].toInt() xor (1 + random.nextInt(255))).toByte()
    }

    private fun ByteArray.fill(position: Int, value: Byte, length: Int) {
        for (index in position until minOf(size, position + length)) this[index] = value
    }

    private const val KINDS = 5

    /** Enough of the end to hold a small archive's central directory and end records, and none of a large one's data. */
    private const val TAIL = 512
}
