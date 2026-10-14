/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.firstRun

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.onboarding_skip
import com.pandulapeter.campfire.presentation.resources.onboarding_welcome_title
import com.pandulapeter.campfire.presentation.resources.whats_new_title
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.HOME_ON_THE_RANGE
import com.pandulapeter.campfire.screenshots.HOUSE_OF_THE_RISING_SUN
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
internal class FirstRunTest {

    @Test
    fun `a first run opens the onboarding over the planted demo library, and What's new waits`() = runAppTest(isFirstRun = true) {
        onNodeWithText(string(Res.string.onboarding_welcome_title)).assertExists()
        waitForLibrary { libraryFiles() == DEMO_LIBRARY }
        onNode(hasText(string(Res.string.onboarding_skip)) and hasClickAction()).performClick()
        waitUntil(timeoutMillis = SONGS_TIMEOUT_MILLIS) {
            listOf(HOUSE_TITLE, HOME_TITLE).all { onAllNodesWithText(it, substring = true).fetchSemanticsNodes().isNotEmpty() }
        }
        waitForIdle()
        // The version a first run installs is recorded as seen rather than shown, so nothing follows the onboarding out.
        val whatsNew = string(Res.string.whats_new_title, System.getProperty("campfire.screenshots.versionName"))
        onNodeWithText(whatsNew).assertDoesNotExist()
        assertEquals(DEMO_LIBRARY, libraryFiles())
    }
}

/** Every file of the library, as its path inside the library folder. */
private fun AppTestScope.libraryFiles() =
    library.walk().filter { it.isFile }.map { it.relativeTo(library).invariantSeparatorsPath }.toSet()

private val DEMO_LIBRARY = setOf(
    "songs/$HOUSE_OF_THE_RISING_SUN",
    "songs/$HOME_ON_THE_RANGE",
    "setlists/getting_started.setlist.json",
)
private const val HOUSE_TITLE = "House of the Rising Sun"
private const val HOME_TITLE = "Home on the Range"
private const val SONGS_TIMEOUT_MILLIS = 10_000L
