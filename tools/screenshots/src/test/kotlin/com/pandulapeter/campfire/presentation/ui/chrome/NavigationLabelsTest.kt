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
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.songs
import com.pandulapeter.campfire.presentation.resources.tools
import com.pandulapeter.campfire.screenshots.APP_LANGUAGES
import com.pandulapeter.campfire.screenshots.TestAppearance
import com.pandulapeter.campfire.screenshots.isWhole
import com.pandulapeter.campfire.screenshots.runAppTest
import com.pandulapeter.campfire.screenshots.textLayout
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class NavigationLabelsTest {

    @Test
    fun `every navigation label is whole at a large text size in every language`() {
        APP_LANGUAGES.forEach { language ->
            runAppTest(appearance = TestAppearance(language = language, textScale = 1.3f)) {
                listOf(Res.string.songs, Res.string.setlists, Res.string.tools, Res.string.settings).map { string(it) }.forEach { label ->
                    // The Songs screen names itself too; the bar is the lowest of the clickable places the name is in.
                    val node = onAllNodes(hasText(label) and hasAnyAncestor(hasClickAction()), useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .maxBy { it.boundsInRoot.top }
                    assertTrue(node.textLayout.isWhole, "${language.id}: the navigation label \"$label\" is cut short.")
                }
            }
        }
    }
}
