/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The expected values are what a browser's `encodeURIComponent` and `decodeURIComponent` answer. */
class PercentEncodingTest {

    private val vectors = mapOf(
        "tukorfurogep-arviz" to "tukorfurogep-arviz",
        "a b&c~d" to "a%20b%26c%7Ed",
        "катюша" to "%D0%BA%D0%B0%D1%82%D1%8E%D1%88%D0%B0",
        "!*'()" to "!*'()",
        "a/b" to "a%2Fb",
        "é?#%+" to "%C3%A9%3F%23%25%2B",
    )

    @Test
    fun `a segment is encoded as encodeURIComponent encodes it, the tilde included`() =
        vectors.forEach { (decoded, encoded) -> assertEquals(encoded, encodePathSegment(decoded)) }

    @Test
    fun `an encoded segment decodes back to what was encoded`() =
        vectors.forEach { (decoded, encoded) -> assertEquals(decoded, decodePathSegment(encoded)) }

    @Test
    fun `malformed escapes do not decode`() =
        listOf("%", "%G1", "%C3", "%C3a", "a%2", "%ED%A0%80", "%C0%AF").forEach { assertNull(decodePathSegment(it), it) }

    @Test
    fun `a plus sign is not a space`() = assertEquals("a+b", decodePathSegment("a+b"))

    @Test
    fun `lowercase escapes decode`() = assertEquals("é", decodePathSegment("%c3%a9"))
}
