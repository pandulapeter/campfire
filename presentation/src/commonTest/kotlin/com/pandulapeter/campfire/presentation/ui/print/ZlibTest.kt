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

import com.pandulapeter.campfire.data.formats.zip.Deflater
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Known zlib streams, checked against zlib's own decoder when they were written down. */
internal class ZlibTest {
    @Test fun `empty input is the header, an empty fixed block, and a checksum of one`() = runTest {
        assertEquals("7801030000000001", zlib(ByteArray(0)))
    }

    @Test fun `a repeated byte ends in its Adler-32`() = runTest {
        assertEquals("78014b1c05a360140c7b0000f9d87af8", zlib(ByteArray(1000) { 'a'.code.toByte() }))
    }

    private suspend fun zlib(input: ByteArray) = PrintBytes().also { Deflater().deflateZlib(input, it) }.result()
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}
