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
        val encoded = runs(input)
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
        assertTrue(runs(ByteArray(10000) { -1 }).size < 200)
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

    @Test fun aReusedScratchBufferWritesIdenticalPagesIdentically() {
        val page = ByteArray(64 * 64) { if (it % 7 == 0) 0 else -1 }
        val writer = PrintPdfWriter(100f, 100f)
        writer.addPage(16, 16, ByteArray(256) { it.toByte() })
        repeat(2) { writer.addPage(64, 64, page) }
        val bytes = writer.finish()
        // One character per byte, so that a match's position is the stream's offset in the file.
        val text = CharArray(bytes.size) { (bytes[it].toInt() and 255).toChar() }.concatToString()
        val streams = Regex("""/Width (\d+) [^>]*/Length (\d+) >>\nstream\n""").findAll(text).map { match ->
            val start = match.range.last + 1
            bytes.copyOfRange(start, start + match.groupValues[2].toInt())
        }.toList()
        assertEquals(3, streams.size)
        assertContentEquals(runs(page), streams[1])
        assertContentEquals(streams[1], streams[2])
    }

    private fun runs(input: ByteArray) = PrintBytes().also { encodePrintRuns(input, it) }.result()
}
