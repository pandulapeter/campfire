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

import com.pandulapeter.campfire.data.formats.zip.Inflater.CODE_LENGTH_ORDER
import com.pandulapeter.campfire.data.formats.zip.Inflater.DISTANCE_BASE
import com.pandulapeter.campfire.data.formats.zip.Inflater.DISTANCE_EXTRA
import com.pandulapeter.campfire.data.formats.zip.Inflater.DYNAMIC
import com.pandulapeter.campfire.data.formats.zip.Inflater.FIXED
import com.pandulapeter.campfire.data.formats.zip.Inflater.Huffman
import com.pandulapeter.campfire.data.formats.zip.Inflater.LENGTH_BASE
import com.pandulapeter.campfire.data.formats.zip.Inflater.LENGTH_EXTRA
import com.pandulapeter.campfire.data.formats.zip.Inflater.MAX_CODE_LENGTH
import com.pandulapeter.campfire.data.formats.zip.Inflater.MAX_DISTANCE_CODES
import com.pandulapeter.campfire.data.formats.zip.Inflater.MAX_LITERAL_CODES
import com.pandulapeter.campfire.data.formats.zip.Inflater.STORED
import com.pandulapeter.campfire.data.formats.zip.Inflater.TOO_MANY_LENGTHS
import kotlinx.coroutines.yield

/**
 * Inflates the [length] bytes of raw DEFLATE data at [offset] of [source] into [sink], the way [Inflater.inflate] does
 * an array but holding no more than a 64 KiB input buffer and a 64 KiB output window of it: what an entry inflates to
 * reaches the sink in slices as the window slides, with its CRC-32 and SHA-256 taken from the same slices. The stream is
 * held to [declaredSize] as it inflates and must produce exactly that many bytes with the CRC-32 [crc]; there is no
 * ceiling of its own, the caller's limits having been charged for the declared size before.
 *
 * The sink is closed or abandoned as [EntrySink] promises, abandoned too where the range lies outside [source] or the
 * coroutine is cancelled. It yields at least every 256 KiB of input read or output written.
 *
 * @throws ZipException when the data is malformed, or produces more or fewer bytes than declared, or another CRC-32.
 */
internal suspend fun Inflater.inflate(source: ZipSource, offset: Long, length: Long, declaredSize: Long, crc: Long, sink: EntrySink) =
    sink.receive(declaredSize, crc) { writer ->
        StreamingInflate(source, offset, length, maxOutput = -1, outputLimit = declaredSize, output = writer::write).inflate()
    }

/**
 * The first [maxOutput] bytes the [length] bytes of raw DEFLATE data at [offset] of [source] inflate to, or all of them
 * where there are fewer, as [Inflater.inflate] with its `maxOutput` gives them: nothing is checked but the stream up to
 * there, and nothing past that is read.
 *
 * @throws ZipException when the stream is malformed before the prefix is complete.
 */
internal suspend fun Inflater.inflatePrefix(source: ZipSource, offset: Long, length: Long, maxOutput: Int): ByteArray {
    val prefix = ByteArrayBuilder(maxOutput)
    StreamingInflate(source, offset, length, maxOutput, outputLimit = maxOutput.toLong()) { bytes, from, count ->
        prefix.bytes(bytes, from, count)
    }.inflate()
    return prefix.build()
}

/**
 * One inflate in the structure of [Inflater]'s own, which mirrors `puff.c`, kept apart so that the in-memory one, which
 * the PDF reader and the differential tests rely on, stays as it is. [bits] and [decode] remain plain functions over
 * [input]: only the block loops suspend, topping the input up between symbols to more than the longest one can take
 * (and sliding the window down when it is nearly full), so that no suspension is paid per bit.
 */
private class StreamingInflate(
    private val source: ZipSource,
    offset: Long,
    length: Long,
    private val maxOutput: Int,
    private val outputLimit: Long,
    private val output: suspend (bytes: ByteArray, offset: Int, length: Int) -> Unit,
) {
    private var sourcePosition = offset
    private val sourceEnd = checkedSum(offset, length)
    private val input = ByteArray(INPUT_BUFFER_SIZE)
    private var position = 0
    private var inputEnd = 0
    private var readBefore = 0L
    private var readSinceYield = 0L
    private var bitBuffer = 0
    private var bitCount = 0

    // The history a back reference reaches into is the last WINDOW_SIZE bytes, so the window is twice that and slides
    // down by half once less than a longest match is left at its end.
    private val window = ByteArray(2 * WINDOW_SIZE)
    private var outSize = 0
    private var flushed = 0
    private var total = 0L

    suspend fun inflate() {
        if (sourceEnd > source.size) {
            throw ZipException("Deflate input range $sourcePosition..$sourceEnd is outside the ${source.size} byte input.")
        }
        try {
            blocks()
        } catch (_: PrefixComplete) {
            // The prefix is all that was asked for, so whatever the rest of the stream holds is none of this call's business.
        }
        flush()
    }

    private suspend fun blocks() {
        var last: Int
        do {
            if (needsInput(MAX_STEP_BYTES)) refill()
            last = bits(1)
            when (val type = bits(2)) {
                STORED -> stored()
                FIXED -> codes(Inflater.fixedLengthCode, Inflater.fixedDistanceCode)
                DYNAMIC -> dynamic()
                else -> throw ZipException("Invalid deflate block type $type.")
            }
        } while (last == 0)
    }

    // region Input

    /** Whether fewer than [count] bytes are buffered while the source has more. */
    private fun needsInput(count: Int) = inputEnd - position < count && sourcePosition < sourceEnd

    private suspend fun refill() {
        val kept = inputEnd - position
        input.copyInto(input, 0, position, inputEnd)
        readBefore += position
        position = 0
        val count = minOf((input.size - kept).toLong(), sourceEnd - sourcePosition).toInt()
        source.read(sourcePosition, input, kept, count)
        sourcePosition += count
        inputEnd = kept + count
        readSinceYield += count
        if (readSinceYield >= YIELD_INTERVAL) {
            readSinceYield = 0
            yield()
        }
    }

    /** Reads [count] bits from the stream, least significant bit of each byte first. */
    private fun bits(count: Int): Int {
        while (bitCount < count) {
            if (position >= inputEnd) {
                throw ZipException("Deflate stream ended while reading ${readBefore + position + 1} bytes in.")
            }
            bitBuffer = bitBuffer or ((input[position++].toInt() and 0xFF) shl bitCount)
            bitCount += 8
        }
        val value = bitBuffer and ((1 shl count) - 1)
        bitBuffer = bitBuffer ushr count
        bitCount -= count
        return value
    }

    // endregion

    // region Output

    private fun emit(value: Int) {
        if (prefixLeft(1) == 0) throw PrefixComplete
        ensureCapacity(1)
        window[outSize++] = value.toByte()
        total++
    }

    private fun copyBack(distance: Int, count: Int) {
        if (distance > total) {
            throw ZipException("Deflate back reference of $distance bytes points before the start of the output.")
        }
        val copied = prefixLeft(count)
        ensureCapacity(copied)
        var from = outSize - distance
        repeat(copied) {
            window[outSize++] = window[from++]
        }
        total += copied
        if (copied < count) throw PrefixComplete
    }

    /** How many of [count] bytes still fit into the prefix that was asked for, all of them where none was. */
    private fun prefixLeft(count: Int) = if (maxOutput >= 0) minOf(count.toLong(), maxOutput - total).toInt() else count

    private fun ensureCapacity(additional: Int) {
        if (total + additional > outputLimit) {
            throw ZipException("Deflate stream produces more than the $outputLimit bytes it declared.")
        }
    }

    /** Hands what the window holds past what was handed on before to [output]. */
    private suspend fun flush() {
        if (outSize > flushed) {
            output(window, flushed, outSize - flushed)
            flushed = outSize
        }
    }

    /** Hands the window on and keeps only the history a back reference may reach into. */
    private suspend fun slide() {
        flush()
        window.copyInto(window, 0, outSize - WINDOW_SIZE, outSize)
        outSize = WINDOW_SIZE
        flushed = WINDOW_SIZE
    }

    // endregion

    // region Blocks

    private suspend fun stored() {
        bitBuffer = 0
        bitCount = 0
        if (needsInput(4)) refill()
        if (inputEnd - position < 4) {
            throw ZipException("Deflate stream ended inside a stored block header.")
        }
        val storedLength = (input[position].toInt() and 0xFF) or ((input[position + 1].toInt() and 0xFF) shl 8)
        val complement = (input[position + 2].toInt() and 0xFF) or ((input[position + 3].toInt() and 0xFF) shl 8)
        position += 4
        if (storedLength != (complement.inv() and 0xFFFF)) {
            throw ZipException("Stored deflate block length $storedLength does not match its complement.")
        }
        if (storedLength > inputEnd - position + (sourceEnd - sourcePosition)) {
            throw ZipException("Deflate stream ended inside a stored block of $storedLength bytes.")
        }
        val copied = prefixLeft(storedLength)
        ensureCapacity(copied)
        var left = copied
        while (left > 0) {
            if (position == inputEnd) refill()
            if (outSize == window.size) slide()
            val piece = minOf(left, inputEnd - position, window.size - outSize)
            input.copyInto(window, outSize, position, position + piece)
            outSize += piece
            position += piece
            total += piece
            left -= piece
        }
        if (copied < storedLength) throw PrefixComplete
    }

    private suspend fun dynamic() {
        if (needsInput(DYNAMIC_HEADER_BYTES)) refill()
        val literalCount = bits(5) + 257
        val distanceCount = bits(5) + 1
        val codeLengthCount = bits(4) + 4
        if (literalCount > MAX_LITERAL_CODES || distanceCount > MAX_DISTANCE_CODES) {
            throw ZipException("Dynamic deflate block declares too many codes ($literalCount / $distanceCount).")
        }
        val codeLengthLengths = IntArray(CODE_LENGTH_ORDER.size)
        for (index in 0 until codeLengthCount) {
            codeLengthLengths[CODE_LENGTH_ORDER[index]] = bits(3)
        }
        val codeLengthCode = Huffman(codeLengthLengths, allowIncomplete = false, what = "code length")
        val lengths = IntArray(literalCount + distanceCount)
        var index = 0
        while (index < lengths.size) {
            if (needsInput(MAX_STEP_BYTES)) refill()
            when (val symbol = decode(codeLengthCode)) {
                16 -> {
                    if (index == 0) {
                        throw ZipException("Dynamic deflate block repeats a code length before the first one.")
                    }
                    val previous = lengths[index - 1]
                    repeat(3 + bits(2)) {
                        if (index < lengths.size) lengths[index++] = previous else throw ZipException(TOO_MANY_LENGTHS)
                    }
                }

                17 -> repeat(3 + bits(3)) {
                    if (index < lengths.size) lengths[index++] = 0 else throw ZipException(TOO_MANY_LENGTHS)
                }

                18 -> repeat(11 + bits(7)) {
                    if (index < lengths.size) lengths[index++] = 0 else throw ZipException(TOO_MANY_LENGTHS)
                }

                else -> lengths[index++] = symbol
            }
        }
        if (lengths[256] == 0) {
            throw ZipException("Dynamic deflate block has no end of block code.")
        }
        val lengthCode = Huffman(lengths.copyOfRange(0, literalCount), allowIncomplete = false, what = "literal/length")
        // A distance code with a single symbol is incomplete but legal: it happens when a block has no back references.
        val distanceLengths = lengths.copyOfRange(literalCount, lengths.size)
        val distanceCode = Huffman(distanceLengths, allowIncomplete = distanceLengths.count { it != 0 } <= 1, what = "distance")
        codes(lengthCode, distanceCode)
    }

    private suspend fun codes(lengthCode: Huffman, distanceCode: Huffman) {
        while (true) {
            if (needsInput(MAX_STEP_BYTES)) refill()
            if (outSize > window.size - MAX_MATCH) slide()
            val symbol = decode(lengthCode)
            when {
                symbol < 256 -> emit(symbol)
                symbol == 256 -> return
                else -> {
                    val lengthIndex = symbol - 257
                    if (lengthIndex >= LENGTH_BASE.size) {
                        throw ZipException("Invalid deflate length symbol $symbol.")
                    }
                    val count = LENGTH_BASE[lengthIndex] + bits(LENGTH_EXTRA[lengthIndex])
                    val distanceSymbol = decode(distanceCode)
                    if (distanceSymbol >= DISTANCE_BASE.size) {
                        throw ZipException("Invalid deflate distance symbol $distanceSymbol.")
                    }
                    copyBack(DISTANCE_BASE[distanceSymbol] + bits(DISTANCE_EXTRA[distanceSymbol]), count)
                }
            }
        }
    }

    /** Walks the canonical code one bit at a time until the accumulated code falls inside a length's range. */
    private fun decode(huffman: Huffman): Int {
        var code = 0
        var first = 0
        var index = 0
        for (length in 1..MAX_CODE_LENGTH) {
            code = code or bits(1)
            val count = huffman.count[length]
            if (code - count < first) {
                return huffman.symbol[index + (code - first)]
            }
            index += count
            first = (first + count) shl 1
            code = code shl 1
        }
        throw ZipException("Invalid Huffman code in the deflate stream.")
    }

    // endregion

    /** Ends a prefix inflate once the prefix is full; never a [ZipException], since nothing is wrong with the stream. */
    private object PrefixComplete : RuntimeException()

    private companion object {
        const val INPUT_BUFFER_SIZE = 64 * 1024

        /** How far back a back reference reaches. */
        const val WINDOW_SIZE = 32 * 1024

        /** The longest a back reference copies. */
        const val MAX_MATCH = 258

        /**
         * More than one step between two top-ups reads: a length code with its extra bits and a distance code with its
         * extra bits come to 48 bits, a code length code with its extra bits to 14, a block header to 3.
         */
        const val MAX_STEP_BYTES = 8

        /** A dynamic block's three counts and up to 19 code length code lengths: 71 bits. */
        const val DYNAMIC_HEADER_BYTES = 16
    }
}
