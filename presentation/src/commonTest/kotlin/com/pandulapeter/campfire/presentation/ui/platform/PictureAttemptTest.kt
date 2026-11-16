/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import com.pandulapeter.campfire.data.model.image.ImageSize
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PictureAttemptTest {

    @Test
    fun `a picture within both bounds keeps its size`() {
        assertEquals(ImageSize(width = 3_000, height = 2_000), reencodedSizeOf(3_000, 2_000))
        assertEquals(ImageSize(width = 1, height = 1), reencodedSizeOf(1, 1))
    }

    @Test
    fun `a long picture is shrunk to the longest side, its proportions kept`() {
        assertEquals(ImageSize(width = 4_096, height = 1_024), reencodedSizeOf(16_384, 4_096))
        assertEquals(ImageSize(width = 512, height = 4_096), reencodedSizeOf(1_000, 8_000))
    }

    @Test
    fun `a large photo is shrunk to the most pixels, its proportions kept`() {
        val size = reencodedSizeOf(6_000, 4_500)
        assertEquals(ImageSize(width = 4_000, height = 3_000), size)
        for ((width, height) in listOf(7_000 to 5_000, 9_999 to 7_777, 4_096 to 4_096, 32_768 to 32_768)) {
            val fitted = reencodedSizeOf(width, height)
            assertTrue(fitted.width.toLong() * fitted.height <= MAX_REENCODED_PIXELS, "$width × $height")
            assertTrue(maxOf(fitted.width, fitted.height) <= MAX_REENCODED_SIDE, "$width × $height")
            assertTrue(abs(fitted.width.toDouble() / fitted.height - width.toDouble() / height) < 0.01, "$width × $height")
        }
    }

    @Test
    fun `a scale shrinks each side of the fitted size`() {
        assertEquals(ImageSize(width = 3_000, height = 2_250), reencodedSizeOf(6_000, 4_500, scale = 0.75f))
        assertEquals(ImageSize(width = 1_500, height = 1_000), reencodedSizeOf(3_000, 2_000, scale = 0.5f))
    }

    @Test
    fun `a sliver keeps at least one pixel across`() {
        assertEquals(ImageSize(width = 4_096, height = 1), reencodedSizeOf(32_768, 2))
    }
}
