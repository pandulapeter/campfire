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

import kotlin.random.Random
import kotlin.test.*

internal class PrintPdfWriterTest {
    @Test fun losslessCompressionHandlesLiteralAndRepeatedPacketBoundaries() {
        val input = ByteArray(400) { 255.toByte() } + ByteArray(300) { it.toByte() } +
            Random(7).nextBytes(2000) + byteArrayOf(0, 0, 1, 1, 1, 2)
        val encoded = encodePrintRuns(input)
        val output = mutableListOf<Byte>()
        var i = 0
        while (true) {
            val packet = encoded[i++].toInt() and 255
            if (packet == 128) break
            if (packet <= 127) repeat(packet + 1) { output += encoded[i++] }
            else { val value = encoded[i++]; repeat(257 - packet) { output += value } }
        }
        assertContentEquals(input, output.toByteArray())
        assertEquals(i, encoded.size)
        assertTrue(encodePrintRuns(ByteArray(10000) { -1 }).size < 200)
    }

    @Test fun pdfCrossReferenceOffsetsResolveToEveryObjectWithTwoPages() {
        val writer = PrintPdfWriter(595.276f, 841.89f)
        repeat(2) { writer.addPage(8, 8, ByteArray(64) { if (it % 8 == 0) 0 else -1 }) }
        val bytes = writer.finish()
        val text = bytes.decodeToString()
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue(text.contains("/Count 2"))
        val xrefOffset = text.substringAfterLast("startxref\n").substringBefore('\n').toInt()
        val xref = bytes.copyOfRange(xrefOffset, bytes.size).decodeToString()
        assertTrue(xref.startsWith("xref\n"))
        xref.lines().drop(3).takeWhile { it.endsWith(" n ") }.forEachIndexed { index, line ->
            val offset = line.take(10).toInt()
            assertTrue(bytes.copyOfRange(offset, minOf(offset + 16, bytes.size)).decodeToString().startsWith("${index + 1} 0 obj\n"))
        }
    }
}
