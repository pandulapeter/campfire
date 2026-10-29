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
import kotlin.test.assertTrue

internal class NormalizeSearchTextUseCaseImplTest {

    private val normalizeSearchText = NormalizeSearchTextUseCaseImpl(normalizeText = NormalizeTextUseCaseImpl())

    @Test
    fun `punctuation and spaces are ignored`() {
        assertEquals("ymca", normalizeSearchText("Y.M.C.A."))
        assertEquals("acdc", normalizeSearchText("AC/DC"))
        assertEquals("acdc", normalizeSearchText(" ac dc "))
        assertEquals("dontstopmenow", normalizeSearchText("Don't Stop Me Now!"))
        assertEquals("rockroll", normalizeSearchText("Rock & Roll"))
    }

    @Test
    fun `accents and case are folded as for sorting`() {
        assertEquals("tukorfurogep", normalizeSearchText("Tükörfúrógép"))
        assertEquals("катюша", normalizeSearchText("Катюша"))
    }

    @Test
    fun `an arabic query finds a word stretched by tatweel or carrying the small marks of a religious text`() {
        assertEquals(normalizeSearchText("\u0633\u0644\u0627\u0645"), normalizeSearchText("\u0633\u0640\u0644\u0640\u0627\u0645"))
        assertEquals(normalizeSearchText("\u0645\u062d\u0645\u062f"), normalizeSearchText("\u0645\u062d\u0645\u062f\u0610"))
        assertEquals(normalizeSearchText("\u0627\u0644\u0644\u0647"), normalizeSearchText("\u0627\u0644\u0644\u0651\u0670\u0647\u06e1"))
    }

    @Test
    fun `an unpointed hebrew query finds the pointed word`() {
        assertTrue(normalizeSearchText("שָׁלוֹם עֲלֵיכֶם").contains(normalizeSearchText("שלום")))
    }

    @Test
    fun `a word typed with its final letter finds it inside a longer word`() {
        assertTrue(normalizeSearchText("שלומית").contains(normalizeSearchText("שלום")))
    }

    @Test
    fun `marks of other scripts are kept`() {
        assertEquals("नमस्ते", normalizeSearchText("नमस्ते"))
    }

    @Test
    fun `text with nothing searchable is empty`() {
        assertEquals("", normalizeSearchText(" ... "))
    }
}
