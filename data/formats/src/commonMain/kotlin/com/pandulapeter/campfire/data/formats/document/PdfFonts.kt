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

internal class PdfFont(private val file: PdfFile, dictionary: PdfDictionary) {
    data class Glyph(val code: Int, val text: String?, val width: Double)
    private data class Code(val value: Int, val length: Int)
    private val cid = dictionary["Subtype"].name() == "Type0"
    private val descendant = if (cid) file.dictionary(file.array(dictionary["DescendantFonts"]).firstOrNull()) ?: dictionary else dictionary
    private val descriptor = file.dictionary(descendant["FontDescriptor"])
    private val baseName = dictionary["BaseFont"].name().orEmpty().substringAfter('+')
    val bold = baseName.contains("bold", true) || baseName.contains("black", true) || file.number(descriptor?.get("FontWeight")) >= 600
    val monospace = file.number(descriptor?.get("Flags")).toInt() and 1 != 0 || listOf("courier", "mono", "consolas").any { baseName.contains(it, true) }
    private val unicode = mutableMapOf<Code, String>()
    private val lengths = mutableSetOf<Int>()
    private val encoding: Array<String?> = arrayOfNulls(256)
    private val widths = mutableMapOf<Int, Double>()
    /**
     * A Type 3 font's widths are in glyph space, which its matrix maps to text space; every other simple font's are in
     * thousandths of an em already. Declared before [defaultWidth], which reads it while the font is being constructed.
     */
    private val widthScale = if (dictionary["Subtype"].name() == "Type3") {
        file.array(dictionary["FontMatrix"]).firstOrNull()?.let { file.number(it) }?.takeIf { it.isFinite() && it > 0.0 }?.times(1000.0) ?: 1.0
    } else 1.0
    /** Only a width the font gives is scaled: the fallback is a guess in thousandths of an em already. */
    private val defaultWidth = if (cid) file.number(descendant["DW"], 1000.0)
    else (file.resolve(descriptor?.get("MissingWidth")) as? PdfNumber)?.value?.times(widthScale) ?: if (monospace) 600.0 else 500.0
    private var encodedLength = if (cid) 2 else 1

    init {
        file.stream(dictionary["ToUnicode"])?.let(::cmap)
        if (cid) cidWidths() else simpleWidths()
        if (!cid) {
            val specified = file.resolve(dictionary["Encoding"])
            val encodingDictionary = specified as? PdfDictionary
            val name = specified.name() ?: encodingDictionary?.get("BaseEncoding").name() ?: if (isStandardFont()) "StandardEncoding" else null
            when (name) {
                "WinAnsiEncoding" -> for (code in 0..255) encoding[code] = winAnsi(code)
                "MacRomanEncoding" -> for (code in 32..255) encoding[code] = if (code < 128) code.toChar().toString() else macRoman[code - 128].toString()
                "StandardEncoding" -> {
                    for (code in 32..126) encoding[code] = code.toChar().toString()
                    encoding[39] = "\u2019"; encoding[96] = "\u2018"
                    standardNames.forEach { (code, name) -> encoding[code] = glyphName(name) }
                }
            }
            var code = 0
            for (value in file.array(encodingDictionary?.get("Differences"))) when (val item = file.resolve(value)) {
                is PdfNumber -> code = item.value.toInt()
                is PdfName -> { if (code in 0..255) encoding[code] = glyphName(item.value); code++ }
                else -> Unit
            }
        }
    }

    fun decode(bytes: ByteArray): Sequence<Glyph> = sequence {
        var index = 0
        val choices = lengths.sortedDescending()
        while (index < bytes.size) {
            var length = encodedLength.coerceAtMost(bytes.size - index)
            var mapped: String? = null
            for (candidate in choices) if (index + candidate <= bytes.size) {
                unicode[Code(code(bytes, index, candidate), candidate)]?.let { mapped = it; length = candidate }
                if (mapped != null) break
            }
            val value = code(bytes, index, length)
            val text = mapped ?: if (length == 1) encoding.getOrNull(value) else null
            yield(Glyph(value, text, widths[value] ?: defaultWidth))
            index += length
        }
    }

    private fun isStandardFont() = baseName.startsWith("Helvetica") || baseName.startsWith("Times-") || baseName.startsWith("Courier")
    private fun simpleWidths() {
        val first = file.number(dictionaryValue("FirstChar")).toInt()
        val values = file.array(dictionaryValue("Widths"))
        if (values.isNotEmpty()) {
            file.chargeFontTables(values.size)
            values.forEachIndexed { index, value -> widths[first + index] = (file.number(value) * widthScale).coerceIn(0.0, 10_000.0) }
        } else if (isStandardFont()) {
            val metrics = if (baseName.startsWith("Times-")) timesWidths else helveticaWidths
            for (code in 32..126) widths[code] = if (monospace) 600.0 else metrics[code - 32].toDouble()
        }
    }
    private fun dictionaryValue(name: String) = descendant[name]
    private fun cidWidths() {
        val values = file.array(descendant["W"])
        var index = 0
        var expanded = 0L
        while (index < values.size) {
            val first = file.number(values[index++]).toInt()
            require(first in 0..65535 && index < values.size)
            val next = file.resolve(values[index++])
            if (next is PdfArray) {
                expanded += next.values.size
                require(expanded <= MAX_EXPANDED_WIDTHS)
                file.chargeFontTables(next.values.size)
                next.values.forEachIndexed { offset, value ->
                    require(first + offset <= 65535)
                    widths[first + offset] = file.number(value).coerceIn(0.0, 10_000.0)
                }
            } else {
                val last = next.number().toInt()
                require(last in first..65535 && index < values.size)
                val width = file.number(values[index++]).coerceIn(0.0, 10_000.0)
                // Charged before the range is written, so that the refusal comes before the writes.
                expanded += last - first + 1
                require(expanded <= MAX_EXPANDED_WIDTHS)
                file.chargeFontTables(last - first + 1)
                for (code in first..last) widths[code] = width
            }
        }
    }

    private fun cmap(bytes: ByteArray) {
        val parser = PdfSyntax(bytes)
        var count = 0
        fun string() = (parser.next(references = false) as? PdfString)?.bytes ?: error("Invalid ToUnicode CMap")
        fun insert(source: ByteArray, text: ByteArray) {
            require(source.size in 1..4 && text.size <= 128 && unicode.size < 100_000)
            file.chargeFontTables(1)
            unicode[Code(code(source, 0, source.size), source.size)] = utf16(text)
            lengths += source.size
        }
        while (true) when (val value = parser.next(references = false) ?: break) {
            is PdfNumber -> { count = value.value.toInt(); require(count in 0..100_000) }
            is PdfKeyword -> when (value.value) {
                "begincodespacerange" -> repeat(count) {
                    val first = string(); val last = string()
                    require(first.size in 1..4 && first.size == last.size)
                    lengths += first.size
                    if (lengths.size == 1) encodedLength = first.size
                }
                "beginbfchar" -> repeat(count) { insert(string(), string()) }
                "beginbfrange" -> repeat(count) {
                    val first = string(); val last = string()
                    require(first.size == last.size && first.size in 1..4)
                    val start = code(first, 0, first.size).toLong() and 0xffffffffL
                    val end = code(last, 0, last.size).toLong() and 0xffffffffL
                    require(end >= start && end - start <= 65535)
                    when (val destination = parser.next(references = false)) {
                        is PdfString -> {
                            val current = destination.bytes.copyOf()
                            for (source in start..end) {
                                val key = ByteArray(first.size) { (source ushr ((first.size - it - 1) * 8)).toByte() }
                                insert(key, current)
                                for (index in current.lastIndex downTo 0) { current[index]++; if (current[index].toInt() != 0) break }
                            }
                        }
                        is PdfArray -> {
                            require(destination.values.size.toLong() == end - start + 1)
                            destination.values.forEachIndexed { offset, text ->
                                insert(ByteArray(first.size) { ((start + offset) ushr ((first.size - it - 1) * 8)).toByte() }, (text as? PdfString)?.bytes ?: error("CMap range"))
                            }
                        }
                        else -> error("CMap range destination")
                    }
                }
                else -> Unit
            }
            else -> Unit
        }
    }

    private fun code(bytes: ByteArray, offset: Int, length: Int): Int {
        var value = 0
        repeat(length) { value = (value shl 8) or (bytes[offset + it].toInt() and 255) }
        return value
    }

    companion object {
        /**
         * Twice the 65,536 codes a CID font can have: a real font's ranges do not overlap and stay at or under 65,536, so
         * only a font that writes the same codes over and over is refused.
         */
        private const val MAX_EXPANDED_WIDTHS = 131_072
        internal fun utf16(bytes: ByteArray): String {
            require(bytes.size % 2 == 0)
            return buildString {
                for (index in bytes.indices step 2) append((((bytes[index].toInt() and 255) shl 8) + (bytes[index + 1].toInt() and 255)).toChar())
            }.removePrefix("\ufeff")
        }
        internal fun glyphName(name: String): String? {
            val base = name.substringBefore('.')
            if ('_' in base) return base.split('_').map { glyphName(it) ?: return null }.joinToString("")
            pdfGlyphNames[base]?.let { return it }
            if (base.startsWith("uni") && (base.length - 3) % 4 == 0 && base.length > 3) {
                val codes = base.drop(3).chunked(4).map { it.toIntOrNull(16) ?: return null }
                return codes.joinToString("") { it.toChar().toString() }
            }
            if (base.startsWith('u') && base.length in 5..7) {
                val code = base.drop(1).toIntOrNull(16) ?: return null
                if (code !in 1..0x10ffff || code in 0xd800..0xdfff) return null
                return if (code <= 0xffff) code.toChar().toString() else
                    (0xd800 + ((code - 0x10000) shr 10)).toChar().toString() + (0xdc00 + ((code - 0x10000) and 1023)).toChar()
            }
            return null
        }
        private fun winAnsi(code: Int): String? = when {
            code in 32..126 || code in 160..255 -> code.toChar().toString()
            code in 128..159 -> winAnsiControls[code - 128].takeUnless { it == '\u0000' }?.toString()
            else -> null
        }
        private const val winAnsiControls = "\u20ac\u0000\u201a\u0192\u201e\u2026\u2020\u2021\u02c6\u2030\u0160\u2039\u0152\u0000\u017d\u0000\u0000\u2018\u2019\u201c\u201d\u2022\u2013\u2014\u02dc\u2122\u0161\u203a\u0153\u0000\u017e\u0178"
        private const val macRoman = "\u00c4\u00c5\u00c7\u00c9\u00d1\u00d6\u00dc\u00e1\u00e0\u00e2\u00e4\u00e3\u00e5\u00e7\u00e9\u00e8" +
            "\u00ea\u00eb\u00ed\u00ec\u00ee\u00ef\u00f1\u00f3\u00f2\u00f4\u00f6\u00f5\u00fa\u00f9\u00fb\u00fc" +
            "\u2020\u00b0\u00a2\u00a3\u00a7\u2022\u00b6\u00df\u00ae\u00a9\u2122\u00b4\u00a8\u2260\u00c6\u00d8" +
            "\u221e\u00b1\u2264\u2265\u00a5\u00b5\u2202\u2211\u220f\u03c0\u222b\u00aa\u00ba\u03a9\u00e6\u00f8" +
            "\u00bf\u00a1\u00ac\u221a\u0192\u2248\u2206\u00ab\u00bb\u2026\u00a0\u00c0\u00c3\u00d5\u0152\u0153" +
            "\u2013\u2014\u201c\u201d\u2018\u2019\u00f7\u25ca\u00ff\u0178\u2044\u20ac\u2039\u203a\ufb01\ufb02" +
            "\u2021\u00b7\u201a\u201e\u2030\u00c2\u00ca\u00c1\u00cb\u00c8\u00cd\u00ce\u00cf\u00cc\u00d3\u00d4" +
            "\uf8ff\u00d2\u00da\u00db\u00d9\u0131\u02c6\u02dc\u00af\u02d8\u02d9\u02da\u00b8\u02dd\u02db\u02c7"
        private val standardNames = mapOf(
            161 to "exclamdown", 162 to "cent", 163 to "sterling", 164 to "fraction", 165 to "yen", 166 to "florin", 167 to "section", 168 to "currency",
            169 to "quotesingle", 170 to "quotedblleft", 171 to "guillemotleft", 172 to "guilsinglleft", 173 to "guilsinglright", 174 to "fi", 175 to "fl",
            177 to "endash", 178 to "dagger", 179 to "daggerdbl", 180 to "periodcentered", 182 to "paragraph", 183 to "bullet", 184 to "quotesinglbase", 185 to "quotedblbase",
            186 to "quotedblright", 187 to "guillemotright", 188 to "ellipsis", 189 to "perthousand", 191 to "questiondown", 193 to "grave", 194 to "acute", 195 to "circumflex",
            196 to "tilde", 197 to "macron", 198 to "breve", 199 to "dotaccent", 200 to "dieresis", 202 to "ring", 203 to "cedilla", 205 to "hungarumlaut", 206 to "ogonek",
            207 to "caron", 208 to "emdash", 225 to "AE", 227 to "ordfeminine", 232 to "Lslash", 233 to "Oslash", 234 to "OE", 235 to "ordmasculine",
            241 to "ae", 245 to "dotlessi", 248 to "lslash", 249 to "oslash", 250 to "oe", 251 to "germandbls",
        )
        private val helveticaWidths = "278 278 355 556 556 889 667 191 333 333 389 584 278 333 278 278 556 556 556 556 556 556 556 556 556 556 278 278 584 584 584 556 1015 667 667 722 722 667 611 778 722 278 500 667 556 833 722 778 667 778 722 667 611 722 667 944 667 667 611 278 278 278 469 556 333 556 556 500 556 556 278 556 556 222 222 500 222 833 556 556 556 556 333 500 278 556 500 722 500 500 500 334 260 334 584".split(' ').map(String::toInt)
        private val timesWidths = "250 333 408 500 500 833 778 180 333 333 500 564 250 333 250 278 500 500 500 500 500 500 500 500 500 500 278 278 564 564 564 444 921 722 667 667 722 611 556 722 722 333 389 722 611 889 722 722 556 722 667 556 611 722 722 944 722 722 611 333 278 333 469 500 333 444 500 444 500 444 333 500 500 278 278 500 278 778 500 500 500 500 333 389 278 500 500 722 500 500 444 480 200 480 541".split(' ').map(String::toInt)
    }
}
