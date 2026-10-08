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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PdfSyntaxTest {
    @Test
    fun `equal values parsed separately hash alike`() {
        fun parse(text: String) = PdfSyntax(text.encodeToByteArray()).next()
        val first = parse("<< /Widths [1 2 3] /Name /A >>")
        val second = parse("<< /Widths [1 2 3] /Name /A >>")
        val different = parse("<< /Widths [1 2 4] /Name /A >>")
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertTrue(first != different)
    }

    @Test
    fun `tokenizer preserves binary strings references and escapes`() {
        val parser = PdfSyntax("<< /Na#6de (a\\(b\\)\\101\\n) /Hex <4142F> /Ref 12 0 R /List [1 -2.5 /Name] >>".encodeToByteArray())
        val value = parser.next() as PdfDictionary
        assertEquals("a(b)A\n", (value["Name"] as PdfString).bytes.decodeToString())
        assertEquals(listOf(65, 66, 240), (value["Hex"] as PdfString).bytes.map { it.toInt() and 255 })
        assertEquals(PdfReference(12), value["Ref"])
        assertEquals(-2.5, value["List"].array()[1].number())
        assertFailsWith<IllegalArgumentException> { PdfSyntax(("[".repeat(66) + "]".repeat(66)).encodeToByteArray()).next() }
    }
}
