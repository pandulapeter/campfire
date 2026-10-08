/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.document

import com.pandulapeter.campfire.data.formats.zip.Inflater
import kotlin.math.abs

internal object PdfFilters {
    fun decode(stream: PdfStream, resolve: (PdfValue?) -> PdfValue?): ByteArray {
        val filterValue = resolve(stream.dictionary["Filter"])
        val filters = if (filterValue is PdfArray) filterValue.values else listOfNotNull(filterValue)
        val paramsValue = resolve(stream.dictionary["DecodeParms"])
        val parameters = if (paramsValue is PdfArray) paramsValue.values else listOfNotNull(paramsValue)
        require(filters.size <= 8)
        var bytes = stream.bytes.copyOfRange(stream.offset, stream.offset + stream.length)
        for ((index, filter) in filters.withIndex()) {
            val params = resolve(parameters.getOrNull(index)) as? PdfDictionary
            bytes = when (resolve(filter).name()) {
                "FlateDecode", "Fl" -> {
                    require(bytes.size >= 4)
                    val cmf = bytes[0].toInt() and 255
                    val flags = bytes[1].toInt() and 255
                    require(cmf and 15 == 8 && cmf shr 4 <= 7 && (cmf * 256 + flags) % 31 == 0 && flags and 32 == 0)
                    // The inflater stops at the final block, so the Adler-32 trailer, which is never checked, may be cut off.
                    predictor(Inflater.inflate(bytes, offset = 2, length = bytes.size - 2), params)
                }
                "ASCIIHexDecode", "AHx" -> asciiHex(bytes)
                "ASCII85Decode", "A85" -> ascii85(bytes)
                "LZWDecode", "LZW" -> predictor(lzw(bytes, params?.get("EarlyChange").number(1.0).toInt()), params)
                else -> error("Unsupported PDF text filter")
            }
            require(bytes.size <= Inflater.MAX_ENTRY_SIZE)
        }
        return bytes
    }

    private fun asciiHex(bytes: ByteArray): ByteArray {
        val result = PdfOutput()
        var high: Int? = null
        for (byte in bytes) {
            val c = (byte.toInt() and 255).toChar()
            if (c == '>') break
            if (c.isWhitespace()) continue
            val value = c.digitToIntOrNull(16) ?: error("ASCIIHex digit")
            if (high == null) high = value else { result.add((high shl 4) + value); high = null }
        }
        high?.let { result.add(it shl 4) }
        return result.bytes()
    }

    private fun ascii85(bytes: ByteArray): ByteArray {
        val result = PdfOutput()
        var value = 0L
        var count = 0
        var index = 0
        if (bytes.size >= 2 && bytes[0].toInt() == 60 && bytes[1].toInt() == 126) index = 2
        fun write(number: Long, length: Int) {
            require(number <= 0xffffffffL)
            repeat(length) { result.add((number shr (24 - it * 8)).toInt()) }
        }
        while (index < bytes.size) {
            val c = (bytes[index++].toInt() and 255).toChar()
            if (c.isWhitespace()) continue
            if (c == '~') { require(bytes.getOrNull(index)?.toInt() == 62); break }
            if (c == 'z') { require(count == 0); write(0, 4); continue }
            require(c in '!'..'u')
            value = value * 85 + (c - '!')
            if (++count == 5) { write(value, 4); value = 0; count = 0 }
        }
        require(count != 1)
        if (count > 1) {
            val length = count - 1
            while (count++ < 5) value = value * 85 + 84
            write(value, length)
        }
        return result.bytes()
    }

    private fun lzw(bytes: ByteArray, earlyChange: Int): ByteArray {
        require(earlyChange in 0..1)
        val prefix = IntArray(4096) { -1 }
        val suffix = IntArray(4096) { it }
        val stack = IntArray(4097)
        val result = PdfOutput()
        var bit = 0
        var width = 9
        var next = 258
        var previous = -1
        var first = 0
        while (bit + width <= bytes.size * 8) {
            var code = 0
            repeat(width) { code = (code shl 1) or ((bytes[bit / 8].toInt() ushr (7 - bit % 8)) and 1); bit++ }
            if (code == 257) return result.bytes()
            if (code == 256) { width = 9; next = 258; previous = -1; continue }
            require(code < next || code == next && previous >= 0)
            val original = code
            var count = 0
            if (code == next) { stack[count++] = first; code = previous }
            while (code >= 256) {
                require(code < next && count < 4096)
                stack[count++] = suffix[code]
                code = prefix[code]
                require(code >= 0)
            }
            first = code
            stack[count++] = first
            for (index in count - 1 downTo 0) result.add(stack[index])
            if (previous >= 0 && next < 4096) {
                prefix[next] = previous; suffix[next] = first; next++
                if (width < 12 && next + earlyChange == 1 shl width) width++
            }
            previous = original
        }
        error("Truncated LZW stream")
    }

    private fun predictor(bytes: ByteArray, params: PdfDictionary?): ByteArray {
        val predictor = params?.get("Predictor").number(1.0).toInt()
        if (predictor == 1) return bytes
        val colors = params?.get("Colors").number(1.0).toInt()
        val bits = params?.get("BitsPerComponent").number(8.0).toInt()
        val columns = params?.get("Columns").number(1.0).toInt()
        require(colors in 1..32 && columns in 1..1_000_000 && bits in listOf(1, 2, 4, 8, 16))
        val rowLong = (colors.toLong() * columns * bits + 7) / 8
        require(rowLong in 1..Inflater.MAX_ENTRY_SIZE.toLong())
        val row = rowLong.toInt()
        val pixel = ((colors * bits + 7) / 8).coerceAtLeast(1)
        if (predictor == 2) {
            require(bytes.size % row == 0)
            val result = bytes.copyOf()
            val mask = (1 shl bits) - 1
            for (start in result.indices step row) {
                fun sample(index: Int): Int {
                    val bit = index * bits
                    var value = 0
                    repeat(bits) { at -> value = (value shl 1) or ((result[start + (bit + at) / 8].toInt() ushr (7 - (bit + at) % 8)) and 1) }
                    return value
                }
                for (index in colors until colors * columns) {
                    val value = (sample(index) + sample(index - colors)) and mask
                    repeat(bits) { at ->
                        val bit = index * bits + at
                        val offset = start + bit / 8
                        val shift = 7 - bit % 8
                        result[offset] = ((result[offset].toInt() and (1 shl shift).inv()) or (((value ushr (bits - at - 1)) and 1) shl shift)).toByte()
                    }
                }
            }
            return result
        }
        require(predictor in 10..15 && bytes.size % (row + 1) == 0)
        val result = ByteArray(bytes.size / (row + 1) * row)
        var source = 0
        var target = 0
        while (source < bytes.size) {
            val type = bytes[source++].toInt() and 255
            require(type in 0..4)
            repeat(row) { index ->
                val left = if (index >= pixel) result[target + index - pixel].toInt() and 255 else 0
                val above = if (target >= row) result[target + index - row].toInt() and 255 else 0
                val upperLeft = if (target >= row && index >= pixel) result[target + index - row - pixel].toInt() and 255 else 0
                val added = when (type) {
                    0 -> 0
                    1 -> left
                    2 -> above
                    3 -> (left + above) / 2
                    else -> {
                        val p = left + above - upperLeft
                        val a = abs(p - left); val b = abs(p - above); val c = abs(p - upperLeft)
                        if (a <= b && a <= c) left else if (b <= c) above else upperLeft
                    }
                }
                result[target + index] = (bytes[source++].toInt() + added).toByte()
            }
            target += row
        }
        return result
    }
}

internal class PdfOutput(private val limit: Int = Inflater.MAX_ENTRY_SIZE) {
    private var buffer = ByteArray(1024)
    private var size = 0
    fun add(value: Int) {
        require(size < limit) { "PDF output too large" }
        if (size == buffer.size) buffer = buffer.copyOf(minOf(buffer.size * 2, limit))
        buffer[size++] = value.toByte()
    }
    fun bytes() = buffer.copyOf(size)
}
