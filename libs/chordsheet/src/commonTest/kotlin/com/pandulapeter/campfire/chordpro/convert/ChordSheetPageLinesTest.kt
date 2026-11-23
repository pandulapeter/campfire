/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

import com.pandulapeter.campfire.pagesong.PageLineLayout
import kotlin.test.Test
import kotlin.test.assertEquals

class ChordSheetPageLinesTest {

    @Test
    fun `each line of each page is read with its layout against the document's body type`() {
        val sheet = ChordSheet(
            listOf(
                ChordSheet.Page(
                    listOf(
                        ChordSheet.Line(listOf(ChordSheet.Span("Title", start = 20.0, end = 60.0, size = 20.0, isBold = true)), isHeading = true),
                        ChordSheet.Line(listOf(ChordSheet.Span("Am", start = 10.0, end = 20.0, size = 10.0), ChordSheet.Span("C", start = 40.0, end = 45.0, size = 10.0))),
                        ChordSheet.Line(listOf(ChordSheet.Span("Hello world", start = 10.0, end = 110.0, size = 10.0, isItalic = true)), column = 1),
                    ),
                ),
                ChordSheet.Page(listOf(ChordSheet.Line(listOf(ChordSheet.Span("1", start = 0.0, end = 5.0, size = 10.0, isMonospace = true))))),
            ),
        )

        val lines = ChordSheetPageLines.of(sheet)

        assertEquals(listOf("Title", "Am C", "Hello world", "1"), lines.map { it.text })
        assertEquals(
            listOf(
                PageLineLayout(page = 0, column = null, size = 2f, isBold = true, isItalic = false, isMonospace = false, indent = 0.1f, isHeading = true),
                PageLineLayout(page = 0, column = null, size = 1f, isBold = false, isItalic = false, isMonospace = false, indent = 0f, isHeading = false),
                PageLineLayout(page = 0, column = 1, size = 1f, isBold = false, isItalic = true, isMonospace = false, indent = 0f, isHeading = false),
                PageLineLayout(page = 1, column = null, size = 1f, isBold = false, isItalic = false, isMonospace = true, indent = 0f, isHeading = false),
            ),
            lines.map { it.layout },
        )
    }
}
