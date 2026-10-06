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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

class ChordDiagramTest {

    @Test
    fun `the other pressed keys move away from the white keys in either theme`() {
        val darkTheme = pressedKeyColor(root = Color(0xFFFE851E), light = Color(0xFFE3E1EA), dark = Color(0xFF15121C))
        assertTrue(darkTheme.luminance() < Color(0xFFFE851E).luminance(), "the Campfire dark theme deepens the accent")
        val lightTheme = pressedKeyColor(root = Color(0xFFA04F03), light = Color(0xFFFAF8FE), dark = Color(0xFF1D1A23))
        assertTrue(lightTheme.luminance() > Color(0xFFA04F03).luminance(), "the Campfire light theme pales the accent")
        val print = pressedKeyColor(root = Color(110, 110, 110), light = Color.White, dark = Color.Black)
        assertTrue(print.luminance() > Color(110, 110, 110).luminance(), "the PDF's gray is paled towards the paper")
    }
}
