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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.LayoutDirection
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings
import com.pandulapeter.campfire.presentation.resources.songs
import com.pandulapeter.campfire.screenshots.AppTestScope
import com.pandulapeter.campfire.screenshots.TestAppearance
import com.pandulapeter.campfire.screenshots.runAppTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class LayoutDirectionTest {

    @Test
    fun `the navigation bar starts from the left in a language written from the left`() = runAppTest {
        assertTrue(navigationItemLeft(string(Res.string.songs)) < navigationItemLeft(string(Res.string.settings)))
    }

    @Test
    fun `the navigation bar starts from the right in a right-to-left layout`() = runAppTest(
        appearance = TestAppearance(layoutDirection = LayoutDirection.Rtl),
    ) {
        assertTrue(navigationItemLeft(string(Res.string.songs)) > navigationItemLeft(string(Res.string.settings)))
    }

    /** The left edge of the navigation bar's item named [label]: the lowest of the clickable places the name is in. */
    private fun AppTestScope.navigationItemLeft(label: String) = onAllNodes(hasText(label) and hasAnyAncestor(hasClickAction()), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .maxBy { it.boundsInRoot.top }
        .boundsInRoot.left
}
