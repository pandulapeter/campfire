/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
internal class AppKeyboardShortcutsTest {

    @Test
    fun `Ctrl and F opens the songs list's search with nothing on the screen focused`() = runAppTest {
        onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.F) } }
        waitUntil { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    }
}
