/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.document

internal sealed interface PdfValue
internal data class PdfNumber(val value: Double) : PdfValue
internal data class PdfName(val value: String) : PdfValue
internal data class PdfString(val bytes: ByteArray) : PdfValue
internal data class PdfArray(val values: List<PdfValue>) : PdfValue
internal data class PdfDictionary(val values: Map<String, PdfValue>) : PdfValue {
    operator fun get(name: String) = values[name]
}
internal data class PdfReference(val number: Int, val generation: Int = 0) : PdfValue
internal data class PdfKeyword(val value: String) : PdfValue
internal data class PdfStream(val dictionary: PdfDictionary, val bytes: ByteArray, val offset: Int, val length: Int) : PdfValue

internal fun PdfValue?.number(default: Double = 0.0) = (this as? PdfNumber)?.value ?: default
internal fun PdfValue?.name() = (this as? PdfName)?.value
internal fun PdfValue?.array() = (this as? PdfArray)?.values.orEmpty()
internal fun ByteArray.latin1(start: Int = 0, end: Int = size) = buildString(end - start) {
    for (index in start until end) append((this@latin1[index].toInt() and 255).toChar())
}

/** PDF lexical syntax, including binary strings. Streams retain a range of the input until actually needed. */
internal class PdfSyntax(
    val bytes: ByteArray,
    var position: Int = 0,
    private val lengthOf: (PdfValue?) -> Int? = { (it as? PdfNumber)?.value?.toInt() },
) {
    private var steps = 0
    fun skip() {
        while (position < bytes.size) when (char()) {
            '\u0000', '\t', '\n', '\u000c', '\r', ' ' -> position++
            '%' -> { while (position < bytes.size && char() !in "\r\n") position++ }
            else -> return
        }
    }
    fun next(depth: Int = 0, references: Boolean = true): PdfValue? {
        require(depth <= 64 && ++steps <= 2_000_000) { "PDF syntax limit" }
        skip()
        if (position >= bytes.size) return null
        return when (char()) {
            '/' -> { position++; PdfName(name()) }
            '(' -> PdfString(literal())
            '<' -> if (char(1) == '<') {
                position += 2
                val values = mutableMapOf<String, PdfValue>()
                while (true) {
                    skip()
                    if (char() == '>' && char(1) == '>') { position += 2; break }
                    require(values.size < 10_000)
                    val key = next(depth + 1) as? PdfName ?: error("PDF dictionary key")
                    values[key.value] = next(depth + 1) ?: error("Truncated PDF dictionary")
                }
                val dictionary = PdfDictionary(values)
                val after = position
                skip()
                if (keywordAt("stream")) {
                    position += 6
                    while (char() in " \t") position++
                    require(char() in "\r\n")
                    if (char() == '\r') position++
                    if (char() == '\n') position++
                    val start = position
                    val declared = lengthOf(dictionary["Length"])
                    val end = if (declared != null && declared >= 0 && start.toLong() + declared <= bytes.size) start + declared
                    else indexOf("endstream", start).also { require(it >= start) }
                    position = end
                    skip()
                    if (!keywordAt("endstream")) {
                        position = indexOf("endstream", start).also { require(it >= start) }
                    }
                    val actualEnd = if (position == end || declared != null && end < position && bytes.sliceArray(end until position).all { it.toInt().toChar().isWhitespace() }) end else position
                    position += 9
                    PdfStream(dictionary, bytes, start, actualEnd - start)
                } else { position = after; dictionary }
            } else PdfString(hex())
            '[' -> {
                position++
                val values = mutableListOf<PdfValue>()
                while (true) {
                    skip()
                    if (char() == ']') { position++; break }
                    require(values.size < 100_000)
                    values += next(depth + 1) ?: error("Truncated PDF array")
                }
                PdfArray(values)
            }
            '+', '-', '.', in '0'..'9' -> {
                val number = word().toDoubleOrNull() ?: error("PDF number")
                require(number.isFinite())
                val after = position
                if (references && number >= 0 && number <= Int.MAX_VALUE && number % 1.0 == 0.0) {
                    skip()
                    val generation = if (char() in '0'..'9') word().toIntOrNull() else null
                    skip()
                    if (generation != null && generation in 0..65535 && keywordAt("R")) {
                        position++; PdfReference(number.toInt(), generation)
                    } else { position = after; PdfNumber(number) }
                } else PdfNumber(number)
            }
            else -> PdfKeyword(word().also { require(it.isNotEmpty()) { "Unexpected PDF delimiter" } })
        }
    }
    fun word(): String {
        skip()
        val start = position
        while (position < bytes.size && !delimiter(char())) position++
        return bytes.latin1(start, position)
    }
    fun indexOf(value: String, start: Int): Int {
        for (index in start..bytes.size - value.length) if (value.indices.all { bytes[index + it].toInt() and 255 == value[it].code }) return index
        return -1
    }
    private fun keywordAt(value: String) = position + value.length <= bytes.size &&
        value.indices.all { char(it) == value[it] } && (position + value.length == bytes.size || delimiter(char(value.length)))
    private fun char(ahead: Int = 0) = bytes.getOrNull(position + ahead)?.let { (it.toInt() and 255).toChar() } ?: '\u0000'
    private fun delimiter(c: Char) = c in "\u0000\t\n\u000c\r ()<>[]{}/%"
    private fun name(): String = buildString {
        while (position < bytes.size && !delimiter(char())) {
            if (char() == '#') {
                require(position + 2 < bytes.size)
                append(((digit(char(1)) shl 4) + digit(char(2))).toChar()); position += 3
            } else append(char()).also { position++ }
        }
    }
    private fun literal(): ByteArray {
        position++
        val result = PdfOutput(limit = 8 shl 20)
        var nesting = 1
        while (position < bytes.size) {
            var c = char(); position++
            when (c) {
                '(' -> { require(++nesting <= 64); result.add(c.code) }
                ')' -> { if (--nesting == 0) return result.bytes(); result.add(c.code) }
                '\\' -> {
                    require(position < bytes.size)
                    c = char(); position++
                    when (c) {
                        '\n' -> Unit
                        '\r' -> if (char() == '\n') position++
                        in '0'..'7' -> {
                            var code = c - '0'
                            repeat(2) { if (char() in '0'..'7') { code = code * 8 + (char() - '0'); position++ } }
                            result.add(code)
                        }
                        else -> result.add(when (c) { 'n' -> 10; 'r' -> 13; 't' -> 9; 'b' -> 8; 'f' -> 12; else -> c.code })
                    }
                }
                '\r' -> { if (char() == '\n') position++; result.add(10) }
                else -> result.add(c.code)
            }
        }
        error("Truncated PDF string")
    }
    private fun hex(): ByteArray {
        position++
        val result = PdfOutput(limit = 8 shl 20)
        var high: Int? = null
        while (position < bytes.size) {
            val c = char(); position++
            if (c == '>') { high?.let { result.add(it shl 4) }; return result.bytes() }
            if (c.isWhitespace()) continue
            val value = digit(c)
            if (high == null) high = value else { result.add((high shl 4) + value); high = null }
        }
        error("Truncated PDF hex string")
    }
    private fun digit(c: Char) = c.digitToIntOrNull(16) ?: error("Invalid PDF hex digit")
}
