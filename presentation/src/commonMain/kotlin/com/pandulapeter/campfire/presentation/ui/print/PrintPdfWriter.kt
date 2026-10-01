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

/**
 * A portable PDF 1.4 writer. Pages use lossless grayscale images at 216 dpi, so the PDF retains the exact glyphs,
 * chord placement and fallback fonts of the preview on every platform. Run-length encoding keeps white paper small.
 * No platform printing service, external process, network request or third-party PDF library is needed.
 */
internal class PrintPdfWriter(private val width: Float, private val height: Float) {
    private val output = PrintBytes()
    // A page's image and content stream are encoded here first, because /Length precedes the data, and appended to the
    // output from it: one copy per stream, into a buffer that keeps its capacity from page to page.
    private val scratch = PrintBytes()
    private val offsets = mutableListOf(0, 0, 0)
    private val pageIds = mutableListOf<Int>()

    init { output.text("%PDF-1.4\n%Campfire\n") }

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

    /** Encodes [grayscale] into the writer's own output before returning, so the caller may reuse the array for the next page. */
    fun addPage(pixelWidth: Int, pixelHeight: Int, grayscale: ByteArray) {
        require(pixelWidth > 0 && pixelHeight > 0 && grayscale.size == pixelWidth * pixelHeight)
        scratch.reset()
        encodePrintRuns(grayscale, scratch)
        val image = stream("/Type /XObject /Subtype /Image /Width $pixelWidth /Height $pixelHeight /ColorSpace /DeviceGray /BitsPerComponent 8 /Filter /RunLengthDecode", scratch)
        scratch.reset()
        scratch.text("q $width 0 0 $height 0 0 cm /Im0 Do Q")
        val content = stream("", scratch)
        // IDs 1 and 2 are reserved for the catalog and page tree.
        val pageId = offsets.size
        pageIds += pageId
        objectText("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $width $height] /Resources << /XObject << /Im0 $image 0 R >> >> /Contents $content 0 R >>")
    }

    fun finish(): ByteArray {
        require(pageIds.isNotEmpty())
        objectText("<< /Type /Pages /Count ${pageIds.size} /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] >>", id = 2)
        objectText("<< /Type /Catalog /Pages 2 0 R >>", id = 1)
        val xref = output.size
        output.text("xref\n0 ${offsets.size}\n0000000000 65535 f \n")
        offsets.drop(1).forEach { output.text("${it.toString().padStart(10, '0')} 00000 n \n") }
        output.text("trailer\n<< /Size ${offsets.size} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return output.result()
    }
}

/** PDF RunLengthDecode, appended to [output]: literal packets 0..127, repeated packets 129..255, and 128 marks the end. */
internal fun encodePrintRuns(input: ByteArray, output: PrintBytes) {
    var i = 0
    fun run(at: Int): Int {
        var end = at + 1
        while (end < input.size && end - at < 128 && input[end] == input[at]) end++
        return end - at
    }
    while (i < input.size) {
        val count = run(i)
        if (count >= 3) {
            output.byte(257 - count)
            output.byte(input[i].toInt())
            i += count
        } else {
            val start = i
            i += count
            while (i < input.size && i - start < 128 && run(i) < 3) i += minOf(run(i), 128 - (i - start))
            output.byte(i - start - 1)
            output.bytes(input, start, i)
        }
    }
    output.byte(128)
}

/** A growable byte buffer that keeps its capacity across [reset], so a PDF's streams are built without fresh arrays. */
internal class PrintBytes {
    private var buffer = ByteArray(1024)
    var size = 0
        private set
    fun byte(value: Int) { reserve(1); buffer[size++] = value.toByte() }
    fun bytes(value: ByteArray, from: Int = 0, to: Int = value.size) {
        reserve(to - from)
        value.copyInto(buffer, size, from, to)
        size += to - from
    }
    fun bytes(value: PrintBytes) = bytes(value.buffer, 0, value.size)
    fun text(value: String) = bytes(value.encodeToByteArray())
    fun reset() { size = 0 }
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
