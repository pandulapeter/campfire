/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.page

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.importing.LoadedPage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopPageSourceTest {

    /** Tests run with no app resources folder, as a build made without a toolchain for the helper ships none. */
    private val source = DesktopPageSource(logger = Logger { })

    @Test
    fun `a build without the web view helper cannot read pages`() {
        assertFalse(source.canLoadPages)
    }

    @Test
    fun `a build without the web view helper answers at once that the page was not read`() = runTest {
        assertEquals(LoadedPage.NotRead(LoadedPage.NotRead.Reason.UNREADABLE), source.load("https://chords.example.com/song"))
    }
}
