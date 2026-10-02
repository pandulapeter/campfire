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

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PdfFiltersTest {
    private fun decode(bytes: ByteArray, filter: String, params: String = ""): ByteArray {
        val dictionary = PdfSyntax("<< /Filter /$filter $params >>".encodeToByteArray()).next() as PdfDictionary
        return PdfFilters.decode(PdfStream(dictionary, bytes, 0, bytes.size)) { it }
    }

    @Test
    fun supportsFlateAsciiHexAndAscii85IncludingPartialGroups() {
        assertEquals("Readable", decode(PdfTestWriter.storedZlib("Readable".encodeToByteArray()), "FlateDecode").decodeToString())
        assertEquals("AB", decode("41 42>".encodeToByteArray(), "ASCIIHexDecode").decodeToString())
        assertContentEquals(byteArrayOf(0xf0.toByte()), decode("F>".encodeToByteArray(), "ASCIIHexDecode"))
        assertEquals("Hello, world!", decode("87cURD_*#TDfTZ)+T~>".encodeToByteArray(), "ASCII85Decode").decodeToString())
        assertContentEquals(ByteArray(4), decode("z~>".encodeToByteArray(), "ASCII85Decode"))
        assertFailsWith<IllegalArgumentException> { decode("!~>".encodeToByteArray(), "ASCII85Decode") }
        assertFailsWith<IllegalStateException> { decode(byteArrayOf(1), "DCTDecode") }
    }

    @Test
    fun supportsLzwClearEndAndTheRepeatedPrefixCase() {
        val codes = listOf(256, 65, 66, 258, 260, 257)
        val bytes = ByteArray((codes.size * 9 + 7) / 8)
        var bit = 0
        for (code in codes) repeat(9) { index ->
            bytes[bit / 8] = (bytes[bit / 8].toInt() or (((code ushr (8 - index)) and 1) shl (7 - bit % 8))).toByte()
            bit++
        }
        assertEquals("ABABABA", decode(bytes, "LZWDecode").decodeToString())
    }

    @Test
    fun reconstructsPngAndTiffPredictorsAndRejectsInvalidDimensions() {
        fun predict(bytes: ByteArray, params: String) = decode(PdfTestWriter.storedZlib(bytes), "FlateDecode", "/DecodeParms << $params >>")
        assertContentEquals(byteArrayOf(10, 20, 30, 11, 22, 33), predict(byteArrayOf(1, 10, 10, 10, 2, 1, 2, 3), "/Predictor 15 /Columns 3"))
        assertContentEquals(byteArrayOf(10, 20, 30), predict(byteArrayOf(10, 10, 10), "/Predictor 2 /Columns 3"))
        assertContentEquals(byteArrayOf(0x6c), predict(byteArrayOf(0x55), "/Predictor 2 /Columns 4 /BitsPerComponent 2"))
        assertFailsWith<IllegalArgumentException> { predict(byteArrayOf(0), "/Predictor 12 /Columns 1000000000") }
        assertFailsWith<IllegalArgumentException> { predict(byteArrayOf(6, 0), "/Predictor 12 /Columns 1") }
    }
}
