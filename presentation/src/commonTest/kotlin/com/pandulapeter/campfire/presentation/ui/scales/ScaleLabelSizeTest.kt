/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.scales

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ScaleLabelSizeTest {

    @Test
    fun `a label that fits its dot keeps the full size`() {
        assertEquals(10f * LABEL_TEXT, scaleLabelFontSize(radius = 10f, widestAtDefault = 12f))
    }

    @Test
    fun `a label wider than its dot is made small enough to fit it`() {
        val size = scaleLabelFontSize(radius = 10f, widestAtDefault = 24f)
        assertEquals(10f * LABEL_ROOM, size * 24f / (10f * LABEL_TEXT), absoluteTolerance = 0.01f)
    }

    @Test
    fun `the widest label decides, so a wider one is never drawn larger`() {
        assertTrue(scaleLabelFontSize(radius = 10f, widestAtDefault = 30f) < scaleLabelFontSize(radius = 10f, widestAtDefault = 24f))
    }
}
