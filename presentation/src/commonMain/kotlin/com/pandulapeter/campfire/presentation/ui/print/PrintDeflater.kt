/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

import kotlinx.coroutines.yield

/**
 * A zlib stream (RFC 1950) holding one deflate block with the fixed Huffman codes (RFC 1951), which is what a PDF's
 * FlateDecode reads. No platform offers a compressor to common code, and a page is mostly white paper and repeated
 * glyph edges, where LZ77 matches do nearly all of the work: dynamic codes would save another fifth at several times the
 * code. The hash tables are allocated once and reused for every page a writer encodes.
 */
internal class PrintDeflater {
    private val head = IntArray(HASH_SIZE)
    private val previous = IntArray(WINDOW_SIZE)
    private lateinit var input: ByteArray
    private lateinit var output: PrintBytes
    private var bitBuffer = 0L
    private var bitCount = 0
    private var matchDistance = 0

    /** Appends the zlib stream of [data] to [destination], yielding (and so checking for cancellation) every 256 KB. */
    suspend fun deflate(data: ByteArray, destination: PrintBytes) {
        input = data
        output = destination
        head.fill(-1)
        previous.fill(-1)
        bitBuffer = 0L
        bitCount = 0
        // CMF 0x78 is deflate with a 32 KB window, and FLG 0x01 makes the pair a multiple of 31 with no dictionary.
        output.byte(0x78)
        output.byte(0x01)
        // BFINAL, then BTYPE 01: the whole page is one final block with the fixed codes.
        bits(1, 1)
        bits(1, 2)
        var position = 0
        var nextYield = YIELD_INTERVAL
        while (position < data.size) {
            if (position >= nextYield) {
                yield()
                nextYield += YIELD_INTERVAL
            }
            val length = longestMatch(position)
            if (length < MIN_MATCH) {
                symbol(data[position].toInt() and 255)
                position++
                continue
            }
            val distance = matchDistance
            // One step of lazy matching: a longer match starting at the next byte is worth a literal in front of it.
            if (length < MAX_MATCH && position + 1 < data.size) {
                val nextLength = longestMatch(position + 1)
                if (nextLength > length) {
                    symbol(data[position].toInt() and 255)
                    match(nextLength, matchDistance)
                    insert(position + 2, position + 1 + nextLength)
                    position += 1 + nextLength
                    continue
                }
                match(length, distance)
                insert(position + 2, position + length)
            } else {
                match(length, distance)
                insert(position + 1, position + length)
            }
            position += length
        }
        symbol(END_OF_BLOCK)
        if (bitCount > 0) bits(0, 8 - bitCount)
        val checksum = adler32(data)
        for (shift in 24 downTo 0 step 8) output.byte(checksum ushr shift and 255)
    }

    private fun hash(position: Int): Int {
        val key = (input[position].toInt() and 255 shl 16) or (input[position + 1].toInt() and 255 shl 8) or
            (input[position + 2].toInt() and 255)
        return (key * -1640531535) ushr (32 - HASH_BITS)
    }

    /** Records [position] in the hash chains, if three bytes start there. */
    private fun insert(position: Int) {
        if (position + MIN_MATCH > input.size) return
        val hash = hash(position)
        previous[position and WINDOW_MASK] = head[hash]
        head[hash] = position
    }

    private fun insert(from: Int, until: Int) {
        for (position in from until until) insert(position)
    }

    /**
     * The length of the longest earlier match for [position] (its distance left in [matchDistance]), then records the
     * position. The search ends at the first match of the greatest length deflate can express, so that every 258 bytes
     * of white paper cost one lookup and one comparison.
     */
    private fun longestMatch(position: Int): Int {
        if (position + MIN_MATCH > input.size) return 0
        val limit = minOf(MAX_MATCH, input.size - position)
        var best = 0
        var candidate = head[hash(position)]
        var chain = MAX_CHAIN
        while (candidate >= 0 && position - candidate <= WINDOW_SIZE && chain-- > 0) {
            if (best < limit && input[candidate + best] == input[position + best]) {
                var length = 0
                while (length < limit && input[candidate + length] == input[position + length]) length++
                if (length > best) {
                    best = length
                    matchDistance = position - candidate
                    if (length == limit) break
                }
            }
            val next = previous[candidate and WINDOW_MASK]
            // A slot the window has wrapped around and written over points forward; the chain ends there.
            if (next >= candidate) break
            candidate = next
        }
        insert(position)
        return if (best >= MIN_MATCH) best else 0
    }

    private fun match(length: Int, distance: Int) {
        var lengthCode = LENGTH_BASES.size - 1
        while (LENGTH_BASES[lengthCode] > length) lengthCode--
        symbol(257 + lengthCode)
        bits(length - LENGTH_BASES[lengthCode], LENGTH_EXTRA_BITS[lengthCode])
        var distanceCode = DISTANCE_BASES.size - 1
        while (DISTANCE_BASES[distanceCode] > distance) distanceCode--
        bits(reverse(distanceCode, 5), 5)
        bits(distance - DISTANCE_BASES[distanceCode], DISTANCE_EXTRA_BITS[distanceCode])
    }

    private fun symbol(value: Int) = bits(FIXED_CODES[value], FIXED_LENGTHS[value])

    /** Deflate packs bits from the least significant end; a Huffman code goes in reversed, as [FIXED_CODES] holds it. */
    private fun bits(value: Int, count: Int) {
        bitBuffer = bitBuffer or (value.toLong() shl bitCount)
        bitCount += count
        while (bitCount >= 8) {
            output.byte((bitBuffer and 255).toInt())
            bitBuffer = bitBuffer ushr 8
            bitCount -= 8
        }
    }

    private companion object {
        const val HASH_BITS = 15
        const val HASH_SIZE = 1 shl HASH_BITS
        const val WINDOW_SIZE = 32_768
        const val WINDOW_MASK = WINDOW_SIZE - 1
        const val MIN_MATCH = 3
        const val MAX_MATCH = 258
        const val MAX_CHAIN = 32
        const val END_OF_BLOCK = 256
        const val YIELD_INTERVAL = 256 * 1024
        val LENGTH_BASES = intArrayOf(
            3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258,
        )
        val LENGTH_EXTRA_BITS = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0)
        val DISTANCE_BASES = intArrayOf(
            1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145,
            8193, 12289, 16385, 24577,
        )
        val DISTANCE_EXTRA_BITS = IntArray(30) { if (it < 4) 0 else it / 2 - 1 }
        val FIXED_LENGTHS = IntArray(288) {
            when {
                it < 144 -> 8
                it < 256 -> 9
                it < 280 -> 7
                else -> 8
            }
        }
        val FIXED_CODES = IntArray(288) {
            val code = when {
                it < 144 -> 0x30 + it
                it < 256 -> 0x190 + it - 144
                it < 280 -> it - 256
                else -> 0xC0 + it - 280
            }
            reverse(code, FIXED_LENGTHS[it])
        }

        fun reverse(code: Int, length: Int): Int {
            var result = 0
            for (bit in 0 until length) result = result or ((code ushr bit and 1) shl (length - 1 - bit))
            return result
        }

        fun adler32(data: ByteArray): Int {
            var a = 1L
            var b = 0L
            var index = 0
            while (index < data.size) {
                val end = minOf(index + 5552, data.size)
                while (index < end) {
                    a += data[index++].toInt() and 255
                    b += a
                }
                a %= 65521
                b %= 65521
            }
            return ((b shl 16) or a).toInt()
        }
    }
}
