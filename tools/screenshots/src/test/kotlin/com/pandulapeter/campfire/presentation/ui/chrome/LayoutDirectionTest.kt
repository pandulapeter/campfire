/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chrome

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.pandulapeter.campfire.data.model.preferences.AppLanguage
import com.pandulapeter.campfire.presentation.localization.AppLocale
import com.pandulapeter.campfire.presentation.localization.LocalizedStrings
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings
import com.pandulapeter.campfire.presentation.resources.songs
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.RISING_SUN_TITLE
import com.pandulapeter.campfire.screenshots.TestAppearance
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.tap
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class LayoutDirectionTest {

    @Test
    fun `the navigation bar starts from the left in a language written from the left`() = runAppTest {
        assertTrue(navigationItemLeft(string(Res.string.songs)) < navigationItemLeft(string(Res.string.settings)))
    }

    @Test
    fun `the navigation bar starts from the right in Hebrew`() = runAppTest(
        appearance = TestAppearance(language = AppLanguage.HEBREW),
    ) {
        assertTrue(navigationItemLeft(string(Res.string.songs)) > navigationItemLeft(string(Res.string.settings)))
    }

    @Test
    fun `choosing a language written from the right in Settings turns the layout at once`() = runAppTest {
        tap(hasText(string(Res.string.settings)) and hasClickAction())
        onNode(hasText(HEBREW_OWN_NAME, substring = true) and hasClickAction()).performScrollTo().performTouchInput { click() }
        waitForIdle()
        val songs = LocalizedStrings.get(Res.string.songs, AppLocale.HE)
        val settings = LocalizedStrings.get(Res.string.settings, AppLocale.HE)
        assertTrue(navigationItemLeft(songs) > navigationItemLeft(settings))
    }

    @Test
    fun `a song opened in a language written from the left is dealt in from the right`() = runAppTest {
        val (moving, settled) = songTitleLeftWhileOpening()
        assertTrue(moving > settled, "The song was at $moving while opening and settled at $settled.")
    }

    @Test
    fun `a song opened in Hebrew is dealt in from the left`() = runAppTest(
        appearance = TestAppearance(language = AppLanguage.HEBREW),
    ) {
        val (moving, settled) = songTitleLeftWhileOpening()
        assertTrue(moving < settled, "The song was at $moving while opening and settled at $settled.")
    }

    /**
     * Where the song's title is a few frames into the slide that opens it, and where it settles, by its left edges. The
     * list under it shows the title too, moving only a little: the song's own is the one furthest from where the list's was.
     */
    private fun AppTestScope.songTitleLeftWhileOpening(): Pair<Float, Float> {
        val listTitleLeft = titleLefts().single()
        mainClock.autoAdvance = false
        try {
            onNode(hasText(RISING_SUN_TITLE) and hasClickAction()).performTouchInput { click() }
            repeat(SLIDE_SAMPLE_FRAMES) { mainClock.advanceTimeByFrame() }
            val moving = titleLefts().maxBy { abs(it - listTitleLeft) }
            mainClock.autoAdvance = true
            waitForIdle()
            return moving to titleLefts().single()
        } finally {
            mainClock.autoAdvance = true
        }
    }

    private fun AppTestScope.titleLefts() = onAllNodes(hasText(RISING_SUN_TITLE), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot.left }

    /** The left edge of the navigation bar's item named [label]: the lowest of the clickable places the name is in. */
    private fun AppTestScope.navigationItemLeft(label: String) = onAllNodes(hasText(label) and hasAnyAncestor(hasClickAction()), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .maxBy { it.boundsInRoot.top }
        .boundsInRoot.left
}

/** What the language picker calls Hebrew in every language, beside its name in the language the app is in. */
private const val HEBREW_OWN_NAME = "עברית"

/** Frames into the slide of a song being opened: under way, still far from where it lands. */
private const val SLIDE_SAMPLE_FRAMES = 5
