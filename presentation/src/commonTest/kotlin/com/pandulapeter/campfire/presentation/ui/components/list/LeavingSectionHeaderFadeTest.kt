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

class LeavingSectionHeaderFadeTest {

    @Test
    fun `a header just starting to leave is drawn whole`() =
        assertEquals(1f, leavingSectionHeaderOpacity(rowTop = 24f, pushedDistance = 0f, height = 48f, nameCenter = 28f))

    @Test
    fun `a leaving header fades out as its name rises towards the window's top`() {
        // Pinned under a 100 px status bar, its name is 128 px down, but the list lets it go once pushed 96 px.
        assertEquals(0.5f, leavingSectionHeaderOpacity(rowTop = 52f, pushedDistance = 48f, height = 96f, nameCenter = 28f))
        // Under a 4 px edge its name reaches the window's top after 32 px.
        assertEquals(0.5f, leavingSectionHeaderOpacity(rowTop = -12f, pushedDistance = 16f, height = 96f, nameCenter = 28f))
        assertEquals(0f, leavingSectionHeaderOpacity(rowTop = -28f, pushedDistance = 32f, height = 96f, nameCenter = 28f))
    }
}
