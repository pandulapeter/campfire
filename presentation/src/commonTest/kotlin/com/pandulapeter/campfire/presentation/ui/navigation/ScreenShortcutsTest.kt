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

import com.pandulapeter.campfire.presentation.ui.components.shortcuts.KeyShortcut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreenShortcutsTest {

    @Test
    fun `every main screen but Settings is numbered in the chrome's order, and Settings has its own key`() {
        val all = CampfireDestination.TopLevel.entries
        assertEquals(KeyShortcut.screens + KeyShortcut.SETTINGS, all.map { screenShortcutOf(it, all) })
        all.forEach { destination -> assertEquals(destination, screenOf(screenShortcutOf(destination, all)!!, all)) }
    }

    @Test
    fun `a screen switched off takes no number, and the ones after it move up`() {
        val destinations = listOf(CampfireDestination.Songs, CampfireDestination.Tuner, CampfireDestination.Settings)
        assertEquals(KeyShortcut.SCREEN_2, screenShortcutOf(CampfireDestination.Tuner, destinations))
        assertNull(screenShortcutOf(CampfireDestination.Setlists, destinations))
        assertNull(screenOf(KeyShortcut.SCREEN_3, destinations))
        assertEquals(CampfireDestination.Settings, screenOf(KeyShortcut.SETTINGS, destinations))
        assertNull(screenOf(KeyShortcut.SETTINGS, destinations - CampfireDestination.Settings))
    }
}
