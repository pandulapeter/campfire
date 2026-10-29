/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class NormalizeTextUseCaseImplTest {

    private val normalizeText = NormalizeTextUseCaseImpl()

    @Test
    fun `a decomposed accent sorts with the composed one`() {
        // What macOS and iOS hand out for a name typed with an accent, see normalizedToNfc.
        val decomposed = "Bésame mucho"

        assertEquals("besame mucho", normalizeText(decomposed))
        assertEquals(normalizeText("Bésame mucho"), normalizeText(decomposed))
    }

    @Test
    fun `hebrew points and cantillation are folded but its punctuation is kept`() {
        assertEquals(normalizeText("שלום"), normalizeText("שָׁלוֹם"))
        assertEquals("בראשית", normalizeText("בְּרֵאשִׁ֖ית"))
        // The maqaf, paseq, sof pasuq, and nun hafukha sit among the points but are not marks.
        assertEquals("בית־אל׀׃׆", normalizeText("בֵּית־אֵל׀׃׆"))
    }

    @Test
    fun `final letters fold to their base letters`() {
        assertEquals("שלומ", normalizeText("שלום"))
        assertEquals("מלכ ארצ כנפ אמנ", normalizeText("מלך ארץ כנף אמן"))
    }

    @Test
    fun `arabic harakat are folded`() {
        assertEquals("سلام", normalizeText("سَلَامٌ"))
    }

    @Test
    fun `latin and cyrillic text is returned as it is`() {
        val text = "катюша and tukorfurogep"

        assertSame(text, normalizeText(text))
    }
}
