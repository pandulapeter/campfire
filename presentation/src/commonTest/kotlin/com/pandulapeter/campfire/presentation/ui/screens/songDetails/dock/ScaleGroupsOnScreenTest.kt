/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import com.pandulapeter.campfire.presentation.ui.songView.model.SongScaleGroup
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ScaleGroupsOnScreenTest {

    private val groups = listOf(
        group(isHome = true, sections = listOf(0, 1, 4)),
        group(sections = listOf(2)),
        group(sections = listOf(3, 5)),
    )

    @Test
    fun `a group is on the screen while any of its sections is`() {
        assertEquals(setOf(0, 1), scaleGroupsOnScreen(groups, 1..2))
        assertEquals(setOf(0, 2), scaleGroupsOnScreen(groups, 5..6))
    }

    @Test
    fun `the home group is never faded even with none of its sections on the screen`() =
        assertEquals(setOf(0, 2), scaleGroupsOnScreen(groups, 3..3))

    @Test
    fun `a group whose sections were not given is never faded`() =
        assertEquals(setOf(0), scaleGroupsOnScreen(listOf(group(sections = emptyList())), 7..9))

    private fun group(isHome: Boolean = false, sections: List<Int>) = SongScaleGroup(
        label = null,
        rootName = "C",
        scales = emptyList(),
        exceptions = emptyList(),
        joinsSeveral = false,
        hasUnnamed = false,
        opening = null,
        isHome = isHome,
        sections = sections,
    )
}
