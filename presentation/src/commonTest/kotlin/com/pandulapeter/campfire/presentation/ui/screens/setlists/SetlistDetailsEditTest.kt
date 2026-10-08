/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.setlists

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** The setlist details sheet writes what it changed, and leaves what another device changed meanwhile alone. */
internal class SetlistDetailsEditTest {

    private val day1 = LocalDate(2026, 10, 1)
    private val day2 = LocalDate(2026, 10, 2)
    private val offered = SetlistDetails(title = "T", description = "D", date = day1, isCountdownShown = false)
    private val current = SetlistDetails(title = "T", description = "D2", date = day2, isCountdownShown = false)

    @Test
    fun `a new title keeps the description and the date another device wrote`() = assertEquals(
        SetlistDetails(title = "T2", description = "D2", date = day2, isCountdownShown = false),
        mergedSetlistDetails(offered = offered, chosen = offered.copy(title = "T2"), current = current),
    )

    @Test
    fun `a new description is written and the date another device wrote is kept`() = assertEquals(
        SetlistDetails(title = "T", description = "D3", date = day2, isCountdownShown = false),
        mergedSetlistDetails(offered = offered, chosen = offered.copy(description = "D3"), current = current),
    )

    @Test
    fun `nothing changed leaves the setlist as it is now`() =
        assertEquals(current, mergedSetlistDetails(offered = offered, chosen = offered, current = current))

    @Test
    fun `a title changed only by surrounding whitespace counts as unchanged`() =
        assertEquals(current, mergedSetlistDetails(offered = offered, chosen = offered.copy(title = " T "), current = current))
}
