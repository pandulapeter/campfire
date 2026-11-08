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

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.key_ctrl
import com.pandulapeter.campfire.presentation.resources.settings
import com.pandulapeter.campfire.presentation.resources.settings_tooltips
import com.pandulapeter.campfire.presentation.resources.songs
import com.pandulapeter.campfire.presentation.resources.songs_search
import com.pandulapeter.campfire.presentation.resources.songs_sort
import com.pandulapeter.campfire.presentation.resources.songs_sorting_mode_by_title
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyNames
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import com.pandulapeter.campfire.presentation.ui.components.shortcuts.label
import com.pandulapeter.campfire.presentation.ui.platform.isAppleKeyboard
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.test.Test
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
internal class TooltipTest {

    @Test
    fun `resting the mouse on an icon button shows its name after a moment`() = runAppTest {
        mainClock.autoAdvance = false
        sortButton().performMouseInput { enter(center) }
        mainClock.advanceTimeBy(PASSING_MILLIS)
        assertFalse(isTooltipShown(), "A pointer only passing over the button shows its tooltip.")
        mainClock.autoAdvance = true
        waitUntil { isTooltipShown() }
        sortButton().performMouseInput { exit() }
        waitUntil { !isTooltipShown() }
    }

    @Test
    fun `a pointer that rested elsewhere waits out the delay again after a tooltip`() = runAppTest {
        sortButton().performMouseInput { enter(center) }
        waitUntil { isTooltipShown() }
        sortButton().performMouseInput { exit() }
        waitUntil { !isTooltipShown() }
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(RESTING_MILLIS)
        val search = onNodeWithContentDescription(string(Res.string.songs_search))
        search.performMouseInput { enter(center) }
        mainClock.advanceTimeBy(PASSING_MILLIS)
        assertFalse(onAllNodes(hasText(string(Res.string.songs_search))).fetchSemanticsNodes().isNotEmpty(), "The delay was skipped.")
    }

    @Test
    fun `a long press on an icon button shows its name instead of pressing it`() = runAppTest {
        sortButton().performTouchInput { longClick() }
        waitUntil { isTooltipShown() }
        // The sort menu would list its orders.
        onNode(hasText(string(Res.string.songs_sorting_mode_by_title))).assertDoesNotExist()
    }

    @Test
    fun `with tooltips switched off a long press presses the button`() = runAppTest {
        tap(hasText(string(Res.string.settings)) and hasClickAction())
        val tooltipsSwitch = hasText(string(Res.string.settings_tooltips)) and hasClickAction()
        onNode(hasScrollToIndexAction()).performScrollToNode(tooltipsSwitch)
        tap(tooltipsSwitch)
        // The navigation item, not Features' Songs switch beside it on a wide settings page.
        tap(hasText(string(Res.string.songs)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        sortButton().performTouchInput { longClick() }
        // No tooltip takes the press, so the sort menu opens and lists its orders.
        waitUntil { onAllNodes(hasText(string(Res.string.songs_sorting_mode_by_title))).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `on a phone without a keyboard a tooltip names no keys`() = runAppTest {
        val searchName = string(Res.string.songs_search)
        onNodeWithContentDescription(searchName).performTouchInput { longClick() }
        waitUntil { onAllNodes(hasText(searchName)).fetchSemanticsNodes().isNotEmpty() }
        val names = KeyNames(
            ctrl = string(Res.string.key_ctrl),
            shift = "",
            space = "",
            enter = "",
            escape = "",
            pageUp = "",
            pageDown = "",
            home = "",
            end = "",
            scroll = "",
        )
        val keys = KeyShortcut.FIND.chord.label(isApple = isAppleKeyboard, names = names)
        assertFalse(onAllNodes(hasText(keys, substring = true)).fetchSemanticsNodes().isNotEmpty(), "A phone without a keyboard is shown \"$keys\".")
    }

    private fun AppTestScope.sortButton() = onNodeWithContentDescription(string(Res.string.songs_sort))

    /** Whether the sort button's name is written anywhere on screen, which only its tooltip writes it. */
    private fun AppTestScope.isTooltipShown() = onAllNodes(hasText(string(Res.string.songs_sort))).fetchSemanticsNodes().isNotEmpty()
}

/** Well short of the hover delay: a pointer on its way across the button. */
private const val PASSING_MILLIS = 200L

/** A pause between two buttons, well past the moment after a tooltip in which the next one shows at once. */
private const val RESTING_MILLIS = 1000L
