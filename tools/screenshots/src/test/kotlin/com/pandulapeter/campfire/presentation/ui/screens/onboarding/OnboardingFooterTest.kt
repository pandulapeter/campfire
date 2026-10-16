/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.TextLayoutResult
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.back
import com.pandulapeter.campfire.presentation.resources.onboarding_progress
import com.pandulapeter.campfire.presentation.resources.onboarding_start_playing
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.openOnboardingLastPage
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class OnboardingFooterTest {

    @Test
    fun `Start playing stays on one line beside the dots on the smallest phone`() = runAppTest(isFirstRun = true) {
        openOnboardingLastPage()
        val startPlaying = string(Res.string.onboarding_start_playing)
        // The footer also measures each label it can show without placing it, so the one read is the button's own.
        val label = onNode(hasText(startPlaying) and hasAnyAncestor(hasClickAction()), useUnmergedTree = true).fetchSemanticsNode()
        val layout = mutableListOf<TextLayoutResult>().also { label.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.single()
        assertEquals(1, layout.lineCount, "Start playing is broken onto ${layout.lineCount} lines.")
        val button = onNode(hasText(startPlaying) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val back = onNode(hasText(string(Res.string.back)) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        // The last page reads out as "Step N of N", however many pages the build shows.
        val dots = (1..MAX_PAGES).map { hasContentDescription(string(Res.string.onboarding_progress, it, it)) }
            .single { onAllNodes(it).fetchSemanticsNodes().isNotEmpty() }
            .let { onNode(it).fetchSemanticsNode().boundsInRoot }
        assertTrue(back.right <= dots.left, "Back ($back) reaches into the dots ($dots).")
        assertTrue(dots.right <= button.left, "The dots ($dots) reach into Start playing ($button).")
        assertTrue(button.right <= TestWindow.PHONE.width.value, "Start playing ($button) runs off the window.")
    }
}

/** More pages than the onboarding has with every one of them shown. */
private const val MAX_PAGES = 10
