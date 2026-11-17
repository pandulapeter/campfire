/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import com.pandulapeter.campfire.presentation.ui.state.LibrarySummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibrarySizeBreakdownTest {

    @Test
    fun `the breakdown names every part that takes up space and in order and the total adds them all`() {
        val summary = summary(songsSize = 1_200, setlistsSize = 0, picturesSize = 34_000, attachmentsSize = 210_000)

        assertEquals(
            listOf(LibraryPart.SONGS to 1_200L, LibraryPart.PICTURES to 34_000L, LibraryPart.ATTACHMENTS to 210_000L),
            sizeBreakdownOf(summary),
        )
        assertEquals(245_200L, summary.size)
    }

    @Test
    fun `a library that is only one part has no breakdown`() {
        assertTrue(sizeBreakdownOf(summary(songsSize = 1_200, setlistsSize = 0, picturesSize = 0, attachmentsSize = 0)).isEmpty())
        assertTrue(sizeBreakdownOf(summary(songsSize = 0, setlistsSize = 0, picturesSize = 0, attachmentsSize = 0)).isEmpty())
    }

    private fun summary(songsSize: Long, setlistsSize: Long, picturesSize: Long, attachmentsSize: Long) = LibrarySummary(
        songCount = 1,
        setlistCount = 0,
        songsSize = songsSize,
        setlistsSize = setlistsSize,
        picturesSize = picturesSize,
        attachmentsSize = attachmentsSize,
    )
}
