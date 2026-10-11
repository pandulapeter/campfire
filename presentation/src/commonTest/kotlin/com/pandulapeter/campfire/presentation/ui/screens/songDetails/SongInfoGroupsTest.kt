/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import kotlin.test.Test
import kotlin.test.assertEquals

class SongInfoGroupsTest {

    @Test
    fun `every group is drawn in the order of the sheet`() = assertEquals(
        SongInfoGroupKind.entries,
        songInfoGroupsOf(hasDetails = true, hasDefaults = true, hasTags = true, hasLanguages = true, hasLinks = true),
    )

    @Test
    fun `a group with nothing to show is left out of the count`() = assertEquals(
        listOf(SongInfoGroupKind.DEFAULTS, SongInfoGroupKind.LINKS),
        songInfoGroupsOf(hasDetails = false, hasDefaults = true, hasTags = false, hasLanguages = false, hasLinks = true),
    )
}
