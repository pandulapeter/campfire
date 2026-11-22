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

/**
 * A raw DEFLATE decompressor (RFC 1951), written from scratch so that the zip support works identically on every
 * platform. Its one decoder, `StreamingInflate`, mirrors Mark Adler's public domain `puff.c`: canonical Huffman codes are
 * decoded bit by bit against per-length counts instead of lookup tables, which is plenty fast for the small files the
 * app deals with. This object holds the tables and limits it shares, and the in-memory entry point the PDF reader uses.
 */
internal object Inflater {

    /**
     * Decompresses [length] bytes of raw DEFLATE data starting at [offset] of [source].
     *
     * @param expectedSize the known uncompressed size, or a negative number when it is unknown. When it is known the
     * output buffer starts at that size, up to [INITIAL_CAPACITY_LIMIT], and the stream is held to it.
     * @param maxOutput how many bytes of the stream's start are wanted, or a negative number for all of it. With it the
     * output buffer is that size whatever the stream declares, the inflate ends as soon as it is full, and neither the
     * declared size nor what the stream would go on to produce is checked: a peek at what an entry holds costs at most
     * this much memory, however many entries are peeked at. A stream shorter than that comes back whole.
     * @throws ZipException when the data is malformed, when it declares more than [MAX_DECLARED_ENTRY_SIZE] bytes, or
     * when it produces more than it declared (more than [MAX_ENTRY_SIZE] where it declared nothing) or fewer.
     */
    fun inflate(
        source: ByteArray,
        offset: Int = 0,
        length: Int = source.size - offset,
        expectedSize: Int = -1,
        maxOutput: Int = -1,
    ): ByteArray {
        if (offset < 0 || length < 0 || offset.toLong() + length > source.size) {
            throw ZipException("Deflate input range $offset..${offset.toLong() + length} is outside the ${source.size} byte input.")
        }
        val isPrefix = maxOutput >= 0
        if (!isPrefix && expectedSize > MAX_DECLARED_ENTRY_SIZE) {
            throw ZipException("Entry would inflate past $MAX_DECLARED_ENTRY_SIZE bytes.")
        }
        // The declared size comes from the archive itself, so it is only trusted as far as a first guess: a corrupted or
        // crafted central directory can claim gigabytes for a stream of a few bytes, and an allocation that large is an
        // OutOfMemoryError on Android and a trap on the web, neither of which an import can catch.
        val output = ByteArrayBuilder(
            when {
                isPrefix -> maxOutput
                expectedSize > 0 -> minOf(expectedSize, INITIAL_CAPACITY_LIMIT)
                else -> 1024
            },
        )
        // An entry is held to the size it declared as it inflates rather than once it has ended: an archive can name the
        // same stream from any number of entries, and each of them would otherwise be inflated to MAX_ENTRY_SIZE first.
        val outputLimit = when {
            isPrefix -> maxOutput
            expectedSize >= 0 -> expectedSize
            else -> MAX_ENTRY_SIZE
        }
        completedWithoutSuspending {
            StreamingInflate(ByteArrayZipSource(source), offset.toLong(), length.toLong(), maxOutput, outputLimit.toLong()) { bytes, from, count ->
                output.bytes(bytes, from, count)
            }.inflate()
        }
        val result = output.build()
        if (!isPrefix && expectedSize >= 0 && result.size != expectedSize) {
            throw ZipException("Deflate stream produced ${result.size} bytes instead of the expected $expectedSize.")
        }
        return result
    }

    /** A canonical Huffman code: how many symbols use each bit length, and the symbols themselves in canonical order. */
    internal class Huffman(lengths: IntArray, allowIncomplete: Boolean, what: String) {

        val count = IntArray(MAX_CODE_LENGTH + 1)
        val symbol: IntArray

        init {
            for (length in lengths) {
                count[length]++
            }
            symbol = IntArray(lengths.size - count[0])
            if (count[0] != lengths.size) {
                var left = 1
                for (length in 1..MAX_CODE_LENGTH) {
                    left = (left shl 1) - count[length]
                    if (left < 0) {
                        throw ZipException("Over-subscribed $what Huffman code.")
                    }
                }
                if (left > 0 && !allowIncomplete) {
                    throw ZipException("Incomplete $what Huffman code.")
                }
            }
            // Where the symbols of each bit length start inside the canonically ordered symbol table.
            val offsets = IntArray(MAX_CODE_LENGTH + 2)
            for (length in 1..MAX_CODE_LENGTH) {
                offsets[length + 1] = offsets[length] + count[length]
            }
            for (index in lengths.indices) {
                if (lengths[index] != 0) {
                    symbol[offsets[lengths[index]]++] = index
                }
            }
        }
    }

    /** The largest buffer allocated on the strength of a declared size alone; anything bigger grows into place. */
    const val INITIAL_CAPACITY_LIMIT = 1 shl 20

    /**
     * The most a single entry may inflate to, which is the most an import unpacks to apart from pictures
     * (`ImportLimits.MAX_IMPORT_SIZE`): the largest entry worth reading is an archive inside the archive, which counts
     * against the songs' budget whatever it holds, and a picture is never more than `ImportLimits.MAX_IMAGE_FILE_SIZE`.
     */
    const val MAX_ENTRY_SIZE = 24 shl 20

    /**
     * The most an entry that declares its size may inflate to: a file attached to a song
     * (`ImportLimits.MAX_ATTACHMENT_FILE_SIZE`), which is larger than anything else an archive holds. The zip reader always
     * declares the size, which its caller's budget has already been charged for and the stream is held to as it inflates;
     * a stream that declares nothing (a PDF's) stays within [MAX_ENTRY_SIZE].
     */
    const val MAX_DECLARED_ENTRY_SIZE = 50 shl 20

    internal const val STORED = 0
    internal const val FIXED = 1
    internal const val DYNAMIC = 2
    internal const val MAX_CODE_LENGTH = 15
    internal const val MAX_LITERAL_CODES = 286
    internal const val MAX_DISTANCE_CODES = 30
    internal const val TOO_MANY_LENGTHS = "Dynamic deflate block declares more code lengths than it announced."

    internal val CODE_LENGTH_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

    internal val LENGTH_BASE = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258,
    )

    internal val LENGTH_EXTRA = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
        3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0,
    )

    internal val DISTANCE_BASE = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
        257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577,
    )

    internal val DISTANCE_EXTRA = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
        7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13,
    )

    /** Literal/length lengths of the fixed code: 0-143 are 8 bits, 144-255 are 9, 256-279 are 7 and 280-287 are 8. */
    internal val fixedLengthCode = Huffman(
        lengths = IntArray(288) { symbol ->
            when {
                symbol < 144 -> 8
                symbol < 256 -> 9
                symbol < 280 -> 7
                else -> 8
            }
        },
        allowIncomplete = false,
        what = "fixed literal/length",
    )

    /** The fixed distance code: 32 five bit codes, of which 30 and 31 never appear in a valid stream. */
    internal val fixedDistanceCode = Huffman(
        lengths = IntArray(32) { 5 },
        allowIncomplete = false,
        what = "fixed distance",
    )
}
