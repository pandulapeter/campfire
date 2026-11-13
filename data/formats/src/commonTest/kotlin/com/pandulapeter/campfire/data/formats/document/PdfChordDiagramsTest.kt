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

import com.pandulapeter.campfire.chordpro.convert.ChordSheet
import com.pandulapeter.campfire.data.formats.document.PdfLineLayout.Positioned
import kotlin.test.Test
import kotlin.test.assertEquals

class PdfChordDiagramsTest {

    @Test
    fun `a row of one long word takes time in proportion to its length`() {
        assertTakesTimeInProportion(
            partSize = 20_000,
            inputOf = { size -> List(size) { Positioned(ChordSheet.Span("a", it * 6.0, it * 6.0 + 6.0, 10.0), 0.0) } },
        ) { word ->
            assertEquals(word.size, PdfChordDiagrams.withoutDiagrams(word).size)
        }
    }
}
