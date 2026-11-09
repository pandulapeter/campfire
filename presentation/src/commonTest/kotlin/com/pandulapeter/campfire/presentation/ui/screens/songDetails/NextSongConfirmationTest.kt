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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NextSongConfirmationTest {

    @Test
    fun `the first press past the end waits and the second goes on`() {
        val confirmation = NextSongConfirmation(isAsked = true)
        assertFalse(confirmation.confirm(2))
        assertEquals(2, confirmation.armedPage)
        assertTrue(confirmation.confirm(2))
        assertNull(confirmation.armedPage)
    }

    @Test
    fun `a press past the end of another song waits again`() {
        val confirmation = NextSongConfirmation(isAsked = true)
        confirmation.confirm(2)
        assertFalse(confirmation.confirm(3))
    }

    @Test
    fun `a dismissed press has to be made again`() {
        val confirmation = NextSongConfirmation(isAsked = true)
        confirmation.confirm(2)
        confirmation.dismiss()
        assertFalse(confirmation.confirm(2))
    }

    @Test
    fun `nothing waits where no confirmation is asked`() {
        val confirmation = NextSongConfirmation(isAsked = false)
        assertTrue(confirmation.confirm(2))
        assertNull(confirmation.armedPage)
    }
}
