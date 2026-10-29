/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HorizontalArrowStepTest {

    @Test
    fun `in a left-to-right layout Right goes to the next item and Left to the previous one`() {
        assertEquals(1, Key.DirectionRight.horizontalArrowStep(LayoutDirection.Ltr))
        assertEquals(-1, Key.DirectionLeft.horizontalArrowStep(LayoutDirection.Ltr))
    }

    @Test
    fun `in a right-to-left layout Left goes to the next item and Right to the previous one`() {
        assertEquals(1, Key.DirectionLeft.horizontalArrowStep(LayoutDirection.Rtl))
        assertEquals(-1, Key.DirectionRight.horizontalArrowStep(LayoutDirection.Rtl))
    }

    @Test
    fun `a key that is not a horizontal arrow is no step`() {
        assertNull(Key.DirectionUp.horizontalArrowStep(LayoutDirection.Rtl))
        assertNull(Key.PageDown.horizontalArrowStep(LayoutDirection.Ltr))
    }
}
