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

/** Small independent writer so parser tests can specify exact objects, operators and glyph positions. */
internal class PdfTestWriter {
    private val objects = mutableListOf<ByteArray>()
    fun add(text: String) = add(text.encodeToByteArray())
    fun add(bytes: ByteArray): Int { objects += bytes; return objects.size }
    fun stream(text: String, dictionary: String = "") = stream(text.encodeToByteArray(), dictionary)
    fun stream(bytes: ByteArray, dictionary: String = "") = add("<< /Length ${bytes.size} $dictionary >>\nstream\n".encodeToByteArray() + bytes + "\nendstream".encodeToByteArray())

    fun write(trailer: String = "/Root 1 0 R", brokenXref: Boolean = false): ByteArray {
        val result = mutableListOf<Byte>()
        fun append(bytes: ByteArray) { result.addAll(bytes.toList()) }
        fun append(text: String) = append(text.encodeToByteArray())
        append("%PDF-1.7\n")
        val offsets = mutableListOf(0)
        objects.forEachIndexed { index, bytes ->
            offsets += result.size
            append("${index + 1} 0 obj\n"); append(bytes); append("\nendobj\n")
        }
        val xref = result.size
        append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.drop(1).forEach { append("${it.toString().padStart(10, '0')} 00000 n \n") }
        append("trailer\n<< /Size ${objects.size + 1} $trailer >>\nstartxref\n${if (brokenXref) 3 else xref}\n%%EOF\n")
        return result.toByteArray()
    }

    fun writeXrefStream(compressed: Map<Int, Pair<Int, Int>> = emptyMap(), predictor: Boolean = false): ByteArray {
        var result = "%PDF-1.7\n".encodeToByteArray()
        val offsets = mutableListOf(0)
        objects.forEachIndexed { index, bytes ->
            offsets += result.size
            result += "${index + 1} 0 obj\n".encodeToByteArray() + bytes + "\nendobj\n".encodeToByteArray()
        }
        val xref = result.size
        offsets += xref
        val rows = offsets.mapIndexed { index, offset ->
            val packed = compressed[index]
            val first = packed?.first ?: offset
            val second = packed?.second ?: if (index == 0) 65535 else 0
            byteArrayOf((if (index == 0) 0 else if (packed != null) 2 else 1).toByte(), (first ushr 24).toByte(), (first ushr 16).toByte(), (first ushr 8).toByte(), first.toByte(), (second ushr 8).toByte(), second.toByte())
        }
        val data = if (predictor) {
            var previous = ByteArray(7)
            rows.flatMap { row -> (byteArrayOf(2) + ByteArray(7) { (row[it] - previous[it]).toByte() }).toList().also { previous = row } }.toByteArray()
        } else rows.flatMap { it.toList() }.toByteArray()
        val encoded = if (predictor) storedZlib(data) else data
        val filter = if (predictor) "/Filter /FlateDecode /DecodeParms << /Predictor 12 /Columns 7 >>" else ""
        result += "${objects.size + 1} 0 obj\n<< /Type /XRef /Root 1 0 R /Size ${offsets.size} /W [1 4 2] /Length ${encoded.size} $filter >>\nstream\n".encodeToByteArray() +
            encoded + "\nendstream\nendobj\nstartxref\n$xref\n%%EOF\n".encodeToByteArray()
        return result
    }

    companion object {
        fun storedZlib(bytes: ByteArray): ByteArray {
            require(bytes.size <= 65535)
            val size = bytes.size
            var a = 1; var b = 0
            for (byte in bytes) { a = (a + (byte.toInt() and 255)) % 65521; b = (b + a) % 65521 }
            val checksum = (b shl 16) or a
            return byteArrayOf(0x78, 0x01, 0x01, size.toByte(), (size ushr 8).toByte(), size.inv().toByte(), (size.inv() ushr 8).toByte()) + bytes +
                byteArrayOf((checksum ushr 24).toByte(), (checksum ushr 16).toByte(), (checksum ushr 8).toByte(), checksum.toByte())
        }
        fun song(content: String, font: String = "/Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding", extra: (PdfTestWriter) -> Unit = {}, brokenXref: Boolean = false): ByteArray {
            val writer = PdfTestWriter()
            writer.add("<< /Type /Catalog /Pages 2 0 R >>")
            writer.add("<< /Type /Pages /Count 1 /Kids [3 0 R] /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> >>")
            writer.add("<< /Type /Page /Parent 2 0 R /Contents 5 0 R >>")
            writer.add("<< $font >>")
            writer.stream(content)
            extra(writer)
            return writer.write(brokenXref = brokenXref)
        }
    }
}
