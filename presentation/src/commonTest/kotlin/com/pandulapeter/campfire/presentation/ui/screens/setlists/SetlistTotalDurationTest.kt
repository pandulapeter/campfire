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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SetlistTotalDurationTest {

    @Test
    fun `every song with a duration adds up to the exact total`() {
        assertEquals(
            SetlistTotalDuration(total = 7.minutes + 30.seconds, isMinimum = false),
            setlistTotalDuration(listOf(4.minutes + 28.seconds, 3.minutes + 2.seconds)),
        )
    }

    @Test
    fun `a song without one makes the total a minimum`() {
        assertEquals(
            SetlistTotalDuration(total = 4.minutes, isMinimum = true),
            setlistTotalDuration(listOf(null, 4.minutes, null)),
        )
    }

    @Test
    fun `no total without a single duration`() {
        assertNull(setlistTotalDuration(listOf(null, null)))
        assertNull(setlistTotalDuration(emptyList()))
    }
}
