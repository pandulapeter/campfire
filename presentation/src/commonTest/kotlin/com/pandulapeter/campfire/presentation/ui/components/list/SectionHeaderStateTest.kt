/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.list

import kotlin.test.Test
import kotlin.test.assertEquals

class SectionHeaderStateTest {

    @Test
    fun `a header stuck at the top of the viewport shows whole`() =
        assertEquals(1f, pushedHeaderVisibleFraction(offset = -16, height = 96, beforeContentPadding = 16))

    @Test
    fun `a header pushed out by one resting at the start of its section shows nothing in the room above it`() =
        assertEquals(0f, pushedHeaderVisibleFraction(offset = -96, height = 96, beforeContentPadding = 16))

    @Test
    fun `a header halfway pushed out shows half`() =
        assertEquals(0.5f, pushedHeaderVisibleFraction(offset = -56, height = 96, beforeContentPadding = 16))
}
