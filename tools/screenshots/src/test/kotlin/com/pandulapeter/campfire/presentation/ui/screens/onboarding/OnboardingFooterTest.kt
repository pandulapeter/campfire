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

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.back
import com.pandulapeter.campfire.presentation.resources.onboarding_next
import com.pandulapeter.campfire.presentation.resources.onboarding_progress
import com.pandulapeter.campfire.presentation.resources.onboarding_skip
import com.pandulapeter.campfire.presentation.resources.onboarding_start_playing
import com.pandulapeter.campfire.screenshots.APP_LANGUAGES
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestAppearance
import com.pandulapeter.campfire.screenshots.TestWindow
import com.pandulapeter.campfire.screenshots.isWhole
import com.pandulapeter.campfire.screenshots.openOnboardingLastPage
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.textLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class OnboardingFooterTest {

    @Test
    fun `the footer's labels stay whole and on one line in every language at the default and a large text size`() {
        APP_LANGUAGES.forEach { language ->
            listOf(1f, 1.3f).forEach { textScale ->
                runAppTest(isFirstRun = true, appearance = TestAppearance(language = language, textScale = textScale)) {
                    val case = "${language.id} at $textScale"
                    assertFooter(case, string(Res.string.onboarding_skip), string(Res.string.onboarding_next))
                    openOnboardingLastPage()
                    val startPlaying = assertFooter(case, string(Res.string.back), string(Res.string.onboarding_start_playing))
                    assertTrue(startPlaying.right <= TestWindow.PHONE.width.value, "$case: Start playing ($startPlaying) runs off the window.")
                }
            }
        }
    }

    /** Checks the footer's two buttons, labelled [startLabel] and [endLabel], and the dots between them; returns the end button's bounds. */
    private fun AppTestScope.assertFooter(case: String, startLabel: String, endLabel: String): Rect {
        listOf(startLabel, endLabel).forEach { label ->
            // The footer also measures each label it can show without placing it, so the one read is the button's own.
            val layout = textLayout(hasText(label) and hasAnyAncestor(hasClickAction()))
            assertEquals(1, layout.lineCount, "$case: \"$label\" is broken onto ${layout.lineCount} lines.")
            assertTrue(layout.isWhole, "$case: \"$label\" is cut short.")
        }
        val start = onNode(hasText(startLabel) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val end = onNode(hasText(endLabel) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        // A page reads out as "Step N of M", however many pages the build shows.
        val dots = (1..MAX_PAGES).flatMap { step -> (step..MAX_PAGES).map { hasContentDescription(string(Res.string.onboarding_progress, step, it)) } }
            .single { onAllNodes(it).fetchSemanticsNodes().isNotEmpty() }
            .let { onNode(it).fetchSemanticsNode().boundsInRoot }
        // Which button is on the left is the language's direction.
        val (left, right) = listOf(start, end).sortedBy { it.left }
        val isBeside = left.right <= dots.left && dots.right <= right.left
        val isAbove = dots.bottom <= start.top && dots.bottom <= end.top
        assertTrue(isBeside || isAbove, "$case: the dots ($dots) overlap the buttons ($start, $end).")
        return end
    }
}

/** More pages than the onboarding has with every one of them shown. */
private const val MAX_PAGES = 10
