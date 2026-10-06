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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SegmentedChoiceTest {

    @Test
    fun `a row where one label would not fit beside the mark has no marks`() {
        assertFalse(hasRoomForCheckMarks(labelWidths = listOf(50f, 60f, 70f), rowWidth = 328f, contentPadding = 24f, checkMark = 26f))
    }

    @Test
    fun `a row wide enough for every label beside the mark keeps them`() {
        assertTrue(hasRoomForCheckMarks(labelWidths = listOf(50f, 60f, 70f), rowWidth = 600f, contentPadding = 24f, checkMark = 26f))
    }

    @Test
    fun `a label exactly as wide as the room left fits`() {
        assertTrue(hasRoomForCheckMarks(labelWidths = listOf(50f, 50f), rowWidth = 200f, contentPadding = 24f, checkMark = 26f))
    }

    @Test
    fun `a row with no labels keeps its marks`() {
        assertTrue(hasRoomForCheckMarks(labelWidths = emptyList(), rowWidth = 100f, contentPadding = 24f, checkMark = 26f))
    }
}
