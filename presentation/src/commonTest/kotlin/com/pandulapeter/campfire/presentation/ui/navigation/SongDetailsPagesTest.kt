/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

/** The song pager is keyed by file name, so the pages an import report opens name every song once. */
internal class SongDetailsPagesTest {

    @Test
    fun `a song named twice is one page`() = assertEquals(listOf("a") to 0, reportedSongPages(listOf("a", "a"), 1))

    @Test
    fun `the index follows its song past a removed repeat`() =
        assertEquals(listOf("a", "b", "c") to 2, reportedSongPages(listOf("a", "b", "a", "c"), 3))

    @Test
    fun `a list without repeats is unchanged`() = assertEquals(listOf("a", "b") to 1, reportedSongPages(listOf("a", "b"), 1))

    @Test
    fun `an index past the end opens the first page`() = assertEquals(listOf("a", "b") to 0, reportedSongPages(listOf("a", "b"), 5))
}
