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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.keyboard_shortcuts
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.songs
import com.pandulapeter.campfire.presentation.ui.platform.ImpersonatedPlatform
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class AppKeyboardShortcutsTest {

    @Test
    fun `Ctrl and F opens the songs list's search with nothing on the screen focused`() = runAppTest {
        onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.F) } }
        waitUntil { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `on a desktop Ctrl and slash opens the keyboard shortcuts sheet`() = runDesktopTest {
        onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Slash) } }
        waitUntil { onAllNodes(hasText(string(Res.string.keyboard_shortcuts))).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `on a desktop a question mark opens the keyboard shortcuts sheet with nothing on the screen focused`() = runDesktopTest {
        pressQuestionMark()
        waitUntil { isKeyboardShortcutsSheetShown() }
    }

    @Test
    fun `on a desktop a question mark opens the keyboard shortcuts sheet after a click focused a control`() = runDesktopTest {
        // A mouse click focuses the clickable it lands on, here the selected navigation item.
        val songsItem = hasText(string(Res.string.songs)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        onNode(songsItem).performMouseInput { click() }
        waitForIdle()
        pressQuestionMark()
        waitUntil { isKeyboardShortcutsSheetShown() }
    }

    @Test
    fun `on a desktop a question mark typed into the songs search opens no keyboard shortcuts sheet`() = runDesktopTest {
        onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.F) } }
        waitUntil { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        waitForIdle()
        // A bare key-down, as the desktop sends one before the event that types the character.
        pressQuestionMark()
        waitForIdle()
        assertTrue(!isKeyboardShortcutsSheetShown())
    }

    @Test
    fun `on a desktop Ctrl and 2 switches to the second screen`() = runDesktopTest {
        onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Two) } }
        val setlistsItem = hasText(string(Res.string.setlists)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        waitUntil { onAllNodes(setlistsItem and isSelected()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun AppTestScope.pressQuestionMark() = onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Slash) } }

    private fun AppTestScope.isKeyboardShortcutsSheetShown() = onAllNodes(hasText(string(Res.string.keyboard_shortcuts))).fetchSemanticsNodes().isNotEmpty()

    private fun runDesktopTest(test: suspend AppTestScope.() -> Unit) = runAppTest(
        window = TestWindow.DESKTOP,
        platform = ImpersonatedPlatform.WINDOWS,
        test = test,
    )
}
