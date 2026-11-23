/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.data.model.importing.LoadedPage
import kotlin.test.Test
import kotlin.test.assertEquals

class PageFailureTest {

    @Test
    fun `a page that never arrived says to check the connection`() {
        assertEquals(ImportFailure.PAGE_UNREACHABLE, pageFailureOf(LoadedPage.NotRead.Reason.UNREACHABLE))
    }

    @Test
    fun `a page that arrived with nothing to read says to bring the page itself`() {
        assertEquals(ImportFailure.PAGE_NOT_READ, pageFailureOf(LoadedPage.NotRead.Reason.UNREADABLE))
    }
}
