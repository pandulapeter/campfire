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

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorKeyboardTest {

    @Test
    fun `no keyboard makes no window short`() {
        assertFalse(isTypingInShortWindow(imeHeight = 0.dp, windowHeight = 300.dp))
        assertFalse(isControlRowHidden(imeHeight = 0.dp, windowHeight = 100.dp))
    }

    @Test
    fun `an upright phone's keyboard leaves a short window but keeps the control row`() {
        assertTrue(isTypingInShortWindow(imeHeight = 300.dp, windowHeight = 640.dp))
        assertFalse(isControlRowHidden(imeHeight = 300.dp, windowHeight = 640.dp))
    }

    @Test
    fun `a phone on its side loses the control row to the keyboard`() {
        assertTrue(isControlRowHidden(imeHeight = 200.dp, windowHeight = 360.dp))
    }

    @Test
    fun `a tall window keeps its room with the keyboard up`() {
        assertFalse(isTypingInShortWindow(imeHeight = 300.dp, windowHeight = 1000.dp))
        assertFalse(isControlRowHidden(imeHeight = 300.dp, windowHeight = 1000.dp))
    }
}
