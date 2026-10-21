/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class JsonBoundsTest {

    @Test
    fun `a document past the token budget is refused`() {
        assertFalse(JsonBounds.isWithin(zeros(600_001), JsonBounds.MAX_TOKENS))
        assertTrue(JsonBounds.isWithin(zeros(599_999), JsonBounds.MAX_TOKENS))
    }

    @Test
    fun `tokens inside strings do not count`() {
        assertTrue(JsonBounds.isWithin("[\"" + ",:".repeat(600_001) + "\"]", JsonBounds.MAX_TOKENS))
        assertTrue(JsonBounds.isWithin("[\"\\\"" + "[".repeat(100) + "\"]", JsonBounds.MAX_TOKENS))
        assertFalse(JsonBounds.isWithin("[\"\\\"\"," + "[".repeat(100) + "]".repeat(100) + "]", JsonBounds.MAX_TOKENS))
    }

    @Test
    fun `depth 64 passes and 65 does not`() {
        assertTrue(JsonBounds.isWithin("[".repeat(64) + "]".repeat(64), JsonBounds.MAX_TOKENS))
        assertFalse(JsonBounds.isWithin("[".repeat(65) + "]".repeat(65), JsonBounds.MAX_TOKENS))
    }

    private fun zeros(count: Int) = "[" + List(count) { "0" }.joinToString(",") + "]"
}
