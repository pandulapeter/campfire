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
import kotlin.math.roundToInt

/**
 * A portable PDF 1.4 writer. Pages are 16-level grayscale images at 216 dpi, so the PDF retains the exact glyphs, chord
 * placement and fallback fonts of the preview on every platform; sixteen grays are indistinguishable from 256 on paper
 * at that resolution, and they halve what Flate has to compress. No platform printing service, external process,
 * network request or third-party PDF library is needed. Invisible Type 3 text uses the renderer's selection rectangles
 * and explicit Unicode maps: it never substitutes a visible font or changes the image.
 */
internal class PrintPdfWriter(
    private val width: Float,
    private val height: Float,
    private val title: String,
) {
    private val output = PrintBytes()

    /**
     * A page's image and content stream are encoded here first, because /Length precedes the data, and appended to the
     * output from it: one copy per stream, into a buffer that keeps its capacity from page to page.
     */
    private val scratch = PrintBytes()
    private val deflater = PrintDeflater()
    private val offsets = mutableListOf(0, 0, 0)
    private val pageIds = mutableListOf<Int>()
    private data class FontStyle(val bold: Boolean, val italic: Boolean, val monospace: Boolean)
    private data class TextCharacter(val text: String, val width: Int)
    private class TextFont(val id: Int, val name: String, val style: FontStyle, val codes: MutableMap<TextCharacter, Int> = linkedMapOf())
    private val fonts = mutableListOf<TextFont>()
    private val characterFonts = mutableMapOf<Pair<FontStyle, TextCharacter>, TextFont>()

    private fun character(glyph: PrintPdfText) = TextCharacter(glyph.text, (glyph.width / glyph.height * 1000).roundToInt().coerceAtLeast(1))

    private fun textFont(glyph: PrintPdfText): TextFont {
        val style = FontStyle(glyph.style.bold, glyph.style.italic, glyph.style.monospace)
        val character = character(glyph)
        return characterFonts.getOrPut(style to character) {
            val font = fonts.lastOrNull { it.style == style && it.codes.size < 255 } ?: run {
                val id = offsets.size
                offsets += 0 // Font dictionaries are written at finish, once all pages have supplied their characters.
                TextFont(id, "F${fonts.size}", style).also { fonts += it }
            }
            font.codes[character] = font.codes.size + 1
            font
        }
    }

    init {
        // Four bytes above 127 on the second line tell mail gateways and transfer tools that the file is binary.
        output.text("%PDF-1.4\n%")
        output.bytes(byteArrayOf(0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte()))
        output.text("\n")
    }

    private fun beginObject(id: Int = offsets.size): Int {
        if (id == offsets.size) offsets += output.size else offsets[id] = output.size
        output.text("$id 0 obj\n")
        return id
    }

    private fun objectText(body: String, id: Int = offsets.size) = beginObject(id).also {
        output.text(body)
        output.text("\nendobj\n")
    }

    private fun stream(dictionary: String, data: PrintBytes) = beginObject().also {
        output.text("<< $dictionary /Length ${data.size} >>\nstream\n")
        output.bytes(data)
        output.text("\nendstream\nendobj\n")
    }

    /**
     * Adds a page from [packed], the rows of [packPrintRows]. The pixels are compressed into the writer's own output
     * before this returns, so the caller may reuse the array for the next page.
     */
    suspend fun addPage(pixelWidth: Int, pixelHeight: Int, packed: ByteArray, text: List<PrintPdfText> = emptyList()) {
        require(pixelWidth > 0 && pixelHeight > 0 && packed.size == printRowBytes(pixelWidth) * pixelHeight)
        scratch.reset()
        deflater.deflate(packed, scratch)
        val image = stream(
            dictionary = "/Type /XObject /Subtype /Image /Width $pixelWidth /Height $pixelHeight " +
                "/ColorSpace /DeviceGray /BitsPerComponent 4 /Filter /FlateDecode",
            data = scratch,
        )
        scratch.reset()
        scratch.text("q ${printPdfNumber(width)} 0 0 ${printPdfNumber(height)} 0 0 cm /Im0 Do Q\n")
        val pageFonts = linkedSetOf<TextFont>()
        if (text.isNotEmpty()) {
            scratch.text("BT 3 Tr\n")
            var current: TextFont? = null
            var currentSize = 0f
            var currentRun = -1
            var currentY = 0f
            var nextX = 0f
            text.forEachIndexed { index, glyph ->
                if (index % 256 == 0) yield()
                require(glyph.text.isNotEmpty() && glyph.text.length <= 64 && glyph.width > 0 && glyph.height > 0)
                val font = textFont(glyph)
                val character = character(glyph)
                pageFonts += font
                val startsAnotherRow = currentRun != glyph.run && glyph.x < nextX - 0.001f
                if (current !== font || currentSize != glyph.height || currentY != glyph.y || startsAnotherRow) {
                    if (current != null) scratch.text("] TJ\n")
                    scratch.text("/${font.name} ${printPdfNumber(glyph.height)} Tf\n")
                    scratch.text("1 0 0 1 ${printPdfNumber(glyph.x)} ${printPdfNumber(height - glyph.y - glyph.height)} Tm [")
                    current = font
                    currentSize = glyph.height
                    currentRun = glyph.run
                    currentY = glyph.y
                    nextX = glyph.x
                }
                // TJ keeps a whole shaped run, and the runs that follow it along the same baseline, in one PDF text object,
                // while retaining exact kerning and chord anchors.
                val adjustment = (nextX - glyph.x) * 1000 / glyph.height
                if (kotlin.math.abs(adjustment) >= 0.001f) scratch.text(" ${printPdfNumber(adjustment)} ")
                scratch.text("<${font.codes.getValue(character).toString(16).padStart(2, '0')}>")
                nextX = glyph.x + character.width * glyph.height / 1000
                currentRun = glyph.run
            }
            scratch.text("] TJ\nET\n")
        }
        val uncompressed = scratch.result()
        scratch.reset()
        deflater.deflate(uncompressed, scratch)
        val content = stream("/Filter /FlateDecode", scratch)
        // IDs 1 and 2 are reserved for the catalog and page tree.
        val pageId = offsets.size
        pageIds += pageId
        objectText(
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $width $height] " +
                "/Resources << /XObject << /Im0 $image 0 R >> " +
                "/Font << ${pageFonts.joinToString(" ") { "/${it.name} ${it.id} 0 R" }} >> >> /Contents $content 0 R >>",
        )
    }

    private fun writeTextFonts() {
        if (fonts.isEmpty()) return
        // Empty glyph procedures supply measured advances and selection boxes, never painted pixels.
        val procedures = fonts.flatMap { font -> font.codes.keys.map { it.width } }.distinct().associateWith { width ->
            scratch.reset()
            scratch.text("$width 0 0 0 $width 1000 d1")
            stream("", scratch)
        }
        for (font in fonts) {
            val fontName = "CampfireText" + (if (font.style.bold) "Bold" else "") +
                (if (font.style.italic) "Italic" else "") + (if (font.style.monospace) "Mono" else "") + font.name
            val flags = 4 + (if (font.style.monospace) 1 else 0) + (if (font.style.italic) 64 else 0)
            val maximumWidth = font.codes.keys.maxOf { it.width }
            val descriptor = objectText("<< /Type /FontDescriptor /FontName /$fontName /Flags $flags " +
                "/FontBBox [0 0 $maximumWidth 1000] /ItalicAngle ${if (font.style.italic) -12 else 0} " +
                "/Ascent 1000 /Descent 0 /CapHeight 1000 /StemV 80 /FontWeight ${if (font.style.bold) 700 else 400} >>")
            scratch.reset()
            scratch.text("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n" +
                "/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n" +
                "/CMapName /CampfireUnicode${font.name} def\n/CMapType 2 def\n1 begincodespacerange\n<00> <ff>\nendcodespacerange\n")
            // PDF limits each bfchar section to 100 mappings; destinations are complete UTF-16 clusters.
            font.codes.entries.chunked(100).forEach { entries ->
                scratch.text("${entries.size} beginbfchar\n")
                entries.forEach { (character, code) -> scratch.text("<${code.toString(16).padStart(2, '0')}> <${printUtf16Hex(character.text)}>\n") }
                scratch.text("endbfchar\n")
            }
            scratch.text("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n")
            val unicode = stream("", scratch)
            val count = font.codes.size
            val widths = font.codes.keys.map { it.width }
            objectText("<< /Type /Font /Subtype /Type3 /Name /${font.name} /BaseFont /$fontName " +
                "/FontBBox [0 0 $maximumWidth 1000] /FontMatrix [0.001 0 0 0.001 0 0] " +
                "/CharProcs << ${widths.distinct().joinToString(" ") { "/g$it ${procedures.getValue(it)} 0 R" }} >> " +
                "/Encoding << /Type /Encoding /Differences [1 ${widths.joinToString(" ") { "/g$it" }}] >> " +
                "/FirstChar 1 /LastChar $count /Widths [${widths.joinToString(" ")}] " +
                "/Resources << >> /FontDescriptor $descriptor 0 R /ToUnicode $unicode 0 R >>", font.id)
        }
    }

    /** Writes the page tree, the catalog, the title and the cross-reference table after the pages, and hands out the file. */
    fun finish(): ByteArray {
        require(pageIds.isNotEmpty())
        writeTextFonts()
        objectText("<< /Type /Pages /Count ${pageIds.size} /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] >>", id = 2)
        objectText("<< /Type /Catalog /Pages 2 0 R >>", id = 1)
        // A UTF-16BE hex string needs no escaping and holds any script, so viewers and print dialogs show the real title.
        val titleHex = printUtf16Hex(title)
        val info = objectText("<< /Title <FEFF$titleHex> /Producer (Campfire) >>")
        val xref = output.size
        output.text("xref\n0 ${offsets.size}\n0000000000 65535 f \n")
        offsets.drop(1).forEach { output.text("${it.toString().padStart(10, '0')} 00000 n \n") }
        output.text("trailer\n<< /Size ${offsets.size} /Root 1 0 R /Info $info 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return output.result()
    }
}

/** The bytes a row of [width] pixels takes at four bits a pixel: every row starts on a byte boundary. */
internal fun printRowBytes(width: Int) = (width + 1) / 2

/**
 * Packs [rows] rows of ARGB [pixels] into [packed] from row [firstRow] on, two pixels a byte with the first one in the
 * high nibble and an odd row's last low nibble white. Each gray is rounded to the nearest of the sixteen levels, since
 * dropping the low bits would turn every pixel from 240 to 254 white and darken the mid-grays of antialiased edges.
 */
internal fun packPrintRows(pixels: IntArray, width: Int, rows: Int, packed: ByteArray, firstRow: Int) {
    val rowBytes = printRowBytes(width)
    for (row in 0 until rows) {
        val source = row * width
        val target = (firstRow + row) * rowBytes
        for (column in 0 until rowBytes) {
            val high = printLevel(pixels[source + 2 * column])
            val low = if (2 * column + 1 < width) printLevel(pixels[source + 2 * column + 1]) else 15
            packed[target + column] = (high shl 4 or low).toByte()
        }
    }
}

/** The nearest of the sixteen grays a 4-bit image holds, 0 black and 15 white. */
private fun printLevel(argb: Int) = (printGray(argb) * 15 + 127) / 255

/** A growable byte buffer that keeps its capacity across [reset], so a PDF's streams are built without fresh arrays. */
internal class PrintBytes {
    private var buffer = ByteArray(1024)
    var size = 0
        private set

    fun byte(value: Int) {
        reserve(1)
        buffer[size++] = value.toByte()
    }

    fun bytes(value: ByteArray, from: Int = 0, to: Int = value.size) {
        reserve(to - from)
        value.copyInto(buffer, size, from, to)
        size += to - from
    }

    fun bytes(value: PrintBytes) = bytes(value.buffer, 0, value.size)

    fun text(value: String) = bytes(value.encodeToByteArray())

    fun reset() {
        size = 0
    }

    /** A copy of the bytes written, the size of the content rather than of the buffer. */
    fun result() = buffer.copyOf(size)

    private fun reserve(count: Int) {
        val required = size.toLong() + count
        check(required <= MAX_PRINT_BYTES) { "The PDF is too large" }
        if (required > buffer.size) {
            // Doubling a large document would leave up to half of a buffer the size of the whole file unused.
            val grown = if (buffer.size < 16 * 1024 * 1024) buffer.size * 2L else buffer.size + buffer.size / 2L
            buffer = buffer.copyOf(maxOf(grown, required).coerceAtMost(MAX_PRINT_BYTES.toLong()).toInt())
        }
    }
}

/** The largest array every platform can allocate stays a little under Int.MAX_VALUE. */
private const val MAX_PRINT_BYTES = Int.MAX_VALUE - 8
