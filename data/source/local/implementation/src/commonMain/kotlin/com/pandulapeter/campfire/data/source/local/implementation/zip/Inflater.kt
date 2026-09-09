package com.pandulapeter.campfire.data.source.local.implementation.zip

/**
 * A raw DEFLATE decompressor (RFC 1951), written from scratch so that the zip support works identically on every
 * platform. It mirrors Mark Adler's public domain `puff.c`: canonical Huffman codes are decoded bit by bit against
 * per-length counts instead of lookup tables, which is plenty fast for the small files the app deals with.
 */
internal object Inflater {

    /**
     * Decompresses [length] bytes of raw DEFLATE data starting at [offset] of [source].
     *
     * @param expectedSize the known uncompressed size, or a negative number when it is unknown. When it is known the
     * output buffer is allocated once and the result is verified against it.
     */
    fun inflate(
        source: ByteArray,
        offset: Int = 0,
        length: Int = source.size - offset,
        expectedSize: Int = -1
    ): ByteArray {
        if (offset < 0 || length < 0 || offset + length > source.size) {
            throw ZipException("Deflate input range $offset..${offset + length} is outside the ${source.size} byte input.")
        }
        val state = State(source, offset, length, expectedSize)
        state.inflate()
        val result = state.output()
        if (expectedSize >= 0 && result.size != expectedSize) {
            throw ZipException("Deflate stream produced ${result.size} bytes instead of the expected $expectedSize.")
        }
        return result
    }

    private class State(
        private val source: ByteArray,
        offset: Int,
        length: Int,
        expectedSize: Int
    ) {
        private val start = offset
        private val end = offset + length
        private var position = offset
        private var bitBuffer = 0
        private var bitCount = 0
        private var out = ByteArray(if (expectedSize > 0) expectedSize else 1024)
        private var outSize = 0

        fun output(): ByteArray = if (outSize == out.size) out else out.copyOf(outSize)

        fun inflate() {
            var last: Int
            do {
                last = bits(1)
                when (val type = bits(2)) {
                    STORED -> stored()
                    FIXED -> codes(fixedLengthCode, fixedDistanceCode)
                    DYNAMIC -> dynamic()
                    else -> throw ZipException("Invalid deflate block type $type.")
                }
            } while (last == 0)
        }

        // region Bit reading

        /** Reads [count] bits from the stream, least significant bit of each byte first. */
        private fun bits(count: Int): Int {
            while (bitCount < count) {
                if (position >= end) {
                    throw ZipException("Deflate stream ended while reading ${position - start + 1} bytes in.")
                }
                bitBuffer = bitBuffer or ((source[position++].toInt() and 0xFF) shl bitCount)
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
            ensureCapacity(1)
            out[outSize++] = value.toByte()
        }

        private fun copyBack(distance: Int, count: Int) {
            if (distance > outSize) {
                throw ZipException("Deflate back reference of $distance bytes points before the start of the output.")
            }
            ensureCapacity(count)
            var from = outSize - distance
            repeat(count) {
                out[outSize++] = out[from++]
            }
        }

        private fun ensureCapacity(additional: Int) {
            val required = outSize + additional
            if (required > out.size) {
                var newSize = if (out.size == 0) 1024 else out.size
                while (newSize < required) {
                    newSize *= 2
                }
                out = out.copyOf(newSize)
            }
        }

        // endregion

        // region Blocks

        private fun stored() {
            bitBuffer = 0
            bitCount = 0
            if (position + 4 > end) {
                throw ZipException("Deflate stream ended inside a stored block header.")
            }
            val storedLength = (source[position].toInt() and 0xFF) or ((source[position + 1].toInt() and 0xFF) shl 8)
            val complement = (source[position + 2].toInt() and 0xFF) or ((source[position + 3].toInt() and 0xFF) shl 8)
            position += 4
            if (storedLength != (complement.inv() and 0xFFFF)) {
                throw ZipException("Stored deflate block length $storedLength does not match its complement.")
            }
            if (position + storedLength > end) {
                throw ZipException("Deflate stream ended inside a stored block of $storedLength bytes.")
            }
            ensureCapacity(storedLength)
            source.copyInto(out, outSize, position, position + storedLength)
            outSize += storedLength
            position += storedLength
        }

        private fun dynamic() {
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

        private fun codes(lengthCode: Huffman, distanceCode: Huffman) {
            while (true) {
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
    }

    /** A canonical Huffman code: how many symbols use each bit length, and the symbols themselves in canonical order. */
    private class Huffman(lengths: IntArray, allowIncomplete: Boolean, what: String) {

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

    private const val STORED = 0
    private const val FIXED = 1
    private const val DYNAMIC = 2
    private const val MAX_CODE_LENGTH = 15
    private const val MAX_LITERAL_CODES = 286
    private const val MAX_DISTANCE_CODES = 30
    private const val TOO_MANY_LENGTHS = "Dynamic deflate block declares more code lengths than it announced."

    private val CODE_LENGTH_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

    private val LENGTH_BASE = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258
    )

    private val LENGTH_EXTRA = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
        3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
    )

    private val DISTANCE_BASE = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
        257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577
    )

    private val DISTANCE_EXTRA = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
        7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13
    )

    /** Literal/length lengths of the fixed code: 0-143 are 8 bits, 144-255 are 9, 256-279 are 7 and 280-287 are 8. */
    private val fixedLengthCode = Huffman(
        lengths = IntArray(288) { symbol ->
            when {
                symbol < 144 -> 8
                symbol < 256 -> 9
                symbol < 280 -> 7
                else -> 8
            }
        },
        allowIncomplete = false,
        what = "fixed literal/length"
    )

    /** The fixed distance code: 32 five bit codes, of which 30 and 31 never appear in a valid stream. */
    private val fixedDistanceCode = Huffman(
        lengths = IntArray(32) { 5 },
        allowIncomplete = false,
        what = "fixed distance"
    )
}
