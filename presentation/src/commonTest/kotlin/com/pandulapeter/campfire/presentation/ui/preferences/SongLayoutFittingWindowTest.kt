/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.preferences

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.SongLayout
import kotlin.test.Test
import kotlin.test.assertEquals

class SongLayoutFittingWindowTest {

    @Test
    fun `a window taller than it is wide suggests one scrolling column`() =
        assertEquals(SongLayout.CONTINUOUS, songLayoutFittingWindow(DpSize(360.dp, 640.dp)))

    @Test
    fun `a window wider than it is tall suggests pages`() =
        assertEquals(SongLayout.PAGES, songLayoutFittingWindow(DpSize(1280.dp, 800.dp)))

    @Test
    fun `a square window suggests pages`() =
        assertEquals(SongLayout.PAGES, songLayoutFittingWindow(DpSize(600.dp, 600.dp)))
}
