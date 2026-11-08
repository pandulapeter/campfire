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

import com.pandulapeter.campfire.presentation.ui.navigation.SettingsTab
import com.pandulapeter.campfire.presentation.ui.playing.testPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SettingsTabsTest {

    @Test
    fun `a build with sync and every feature on has every tab, general first`() =
        assertEquals(SettingsTab.entries, settingsTabs(hasSync = true, userPreferences = testPreferences()))

    @Test
    fun `a build without sync has no sharing tab`() =
        assertFalse(SettingsTab.SHARING in settingsTabs(hasSync = false, userPreferences = testPreferences()))

    @Test
    fun `the song and the metronome tabs go with their features`() = assertEquals(
        SettingsTab.entries - SettingsTab.SONG - SettingsTab.METRONOME,
        settingsTabs(hasSync = true, userPreferences = testPreferences().copy(areSongsEnabled = false, isMetronomeEnabled = false)),
    )
}
