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
import kotlin.test.assertNull

class AppLinksTest {

    @Test
    fun `a link opens the web address it carries`() {
        assertEquals("setlists", AppLinks.pathOf("campfire://setlists"))
        assertEquals("song/%D0%BA%D0%B0%D1%82%D1%8E%D1%88%D0%B0", AppLinks.pathOf("campfire://song/%D0%BA%D0%B0%D1%82%D1%8E%D1%88%D0%B0/"))
        assertEquals("", AppLinks.pathOf("CAMPFIRE://"))
    }

    @Test
    fun `a query or a fragment is no part of the address`() = assertEquals("metronome", AppLinks.pathOf("campfire://metronome?from=widget#top"))

    @Test
    fun `a link of another scheme is none of the app's`() = listOf("https://campfire-songbook.com/app/setlists", "campfire:setlists", "setlists").forEach {
        assertNull(AppLinks.pathOf(it), it)
    }

    @Test
    fun `a link built for an address reads back as that address`() = listOf("new", "paste", "tuner", "setlist/set/a").forEach {
        assertEquals(it, AppLinks.pathOf(AppLinks.of(it)))
    }
}
