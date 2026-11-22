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

import kotlin.test.Test
import kotlin.test.assertEquals

class PinnedHeaderFadeTest {

    @Test
    fun `nothing fades while the items before the header are first visible`() =
        assertEquals(0f, pinnedHeaderScrolledUnder(firstVisibleIndex = 1, firstVisibleOffset = 30, index = 2, fadeHeight = 60f))

    @Test
    fun `the fade follows the header's own item as it scrolls under the top`() =
        assertEquals(0.5f, pinnedHeaderScrolledUnder(firstVisibleIndex = 2, firstVisibleOffset = 30, index = 2, fadeHeight = 60f))

    @Test
    fun `the fade is full once the header's item has scrolled past the fade's height`() =
        assertEquals(1f, pinnedHeaderScrolledUnder(firstVisibleIndex = 2, firstVisibleOffset = 90, index = 2, fadeHeight = 60f))

    @Test
    fun `the fade is full once the items past the header are first visible`() =
        assertEquals(1f, pinnedHeaderScrolledUnder(firstVisibleIndex = 3, firstVisibleOffset = 0, index = 2, fadeHeight = 60f))
}
