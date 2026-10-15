/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceholderTitleSelectionTest {

    @Test
    fun `the placeholder title is selected`() {
        assertEquals(TextRange(8, 16), placeholderTitleSelection("{title: New song}\n{artist: Unknown artist}\n", "New song"))
    }

    @Test
    fun `a song with a title of its own starts at the top`() {
        assertEquals(TextRange.Zero, placeholderTitleSelection("{title: Real title}\n{artist: Unknown artist}\n", "New song"))
    }
}
