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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Known zlib streams with fixed Huffman codes, checked against zlib's own decoder when they were written down. */
internal class PrintDeflaterTest {
    @Test fun `empty input is an empty fixed block`() = runTest {
        assertEquals("7801030000000001", deflated(ByteArray(0)))
    }

    @Test fun `a repeated byte is one literal and maximal matches`() = runTest {
        assertEquals("78014b1c05a360140c7b0000f9d87af8", deflated(ByteArray(1000) { 'a'.code.toByte() }))
    }

    @Test fun `every byte value uses both literal code lengths`() = runTest {
        assertEquals(
            "78016360646266616563e7e0e4e2e6e1e5e3171014121611151397909492969195935750545256515553d7d0d4d2d6d1d5d33730343236313533" +
                "b7b0b4b2b6b1b5b37770747276717573f7f0f4f2f6f1f5f30f080c0a0e090d0b8f888c8a8e898d8b4f484c4a4e494d4bcfc8cccacec9cdcb2f282c2a" +
                "2e292d2bafa8acaaaea9adab6f686c6a6e696d6befe8eceaeee9edeb9f3071d2e42953a74d9f3173d6ec3973e7cd5fb070d1e2254b972d5fb172d5ea" +
                "356bd7addfb071d3e62d5bb76ddfb173d7ee3d7bf7ed3f70f0d0e123478f1d3f71f2d4e93367cf9dbf70f1d2e52b57af5dbf71f3d6ed3b77efdd7ff0f0" +
                "d1e3274f9f3d7ff1f2d5eb376fdfbdfff0f1d3e72f5fbf7dfff1f3d7ef3f7ffffd0700adf67f81",
            deflated(ByteArray(256) { it.toByte() }),
        )
    }

    private suspend fun deflated(input: ByteArray) = PrintBytes().also { PrintDeflater().deflate(input, it) }.result()
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}
