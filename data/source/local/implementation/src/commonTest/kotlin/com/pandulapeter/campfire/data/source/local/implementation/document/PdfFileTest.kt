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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class PdfFileTest {
    @Test
    fun recoveryScanOverManyUnterminatedStreamsIsFast() {
        val text = buildString {
            append("%PDF-1.7\n")
            repeat(20_000) { append("$it 0 obj\n<< /A 1 >>\nstream\nab\n") }
        }
        val elapsed = measureTime { assertFails { PdfFile(text.encodeToByteArray()).catalog() } }
        assertTrue(elapsed < 2.seconds, "Took $elapsed")
    }

    @Test
    fun streamsWithoutALengthEndAtTheirEndstreamInARecoveryScan() = runTest {
        val content = "BT /F1 10 Tf 50 700 Td (Unmeasured) Tj ET"
        val bytes = PdfTestWriter.song(content, brokenXref = true).decodeToString().replace("/Length ${content.length} ", "")
        assertTrue("/Length" !in bytes)
        assertEquals("Unmeasured", PdfTextExtractor.extract(bytes.encodeToByteArray()).pages.single().lines.single().spans.joinToString("") { it.text })
    }

    @Test
    fun aLengthPointingIntoSharedWhitespaceIsLinear() {
        val placeholder = "0000000000"
        val header = "%PDF-1.7\n"
        val objects = List(5_000) { "$it 0 obj\n<< /Length $placeholder >>\nstream\nab\nendstream\nendobj\n" }
        val blockStart = header.length + objects.sumOf { it.length }
        var offset = header.length
        val text = buildString {
            append(header)
            for (item in objects) {
                val dataStart = offset + item.indexOf("stream\n") + "stream\n".length
                append(item.replace(placeholder, (blockStart - dataStart).toString().padStart(placeholder.length, '0')))
                offset += item.length
            }
            append(" ".repeat(2 shl 20))
            append("x")
        }
        val elapsed = measureTime { PdfFile(text.encodeToByteArray()) }
        assertTrue(elapsed < 2.seconds, "Took $elapsed")
    }
}
