/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.preferences

import com.pandulapeter.campfire.presentation.ui.playing.testPreferences
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockTunerSwitchTest {

    @Test
    fun `the switch is enabled with the defaults and before the preferences are read`() {
        assertTrue(isDockTunerSwitchEnabled(testPreferences()))
        assertTrue(isDockTunerSwitchEnabled(null))
    }

    @Test
    fun `lyrics only keeps the switch while the song's click holds a dock`() =
        assertTrue(isDockTunerSwitchEnabled(testPreferences().copy(areChordsEnabled = false)))

    @Test
    fun `lyrics only without the metronome feature leaves no dock so the switch is disabled`() =
        assertFalse(isDockTunerSwitchEnabled(testPreferences().copy(areChordsEnabled = false, isMetronomeEnabled = false)))

    @Test
    fun `lyrics only without the dock's metronome leaves no dock so the switch is disabled`() =
        assertFalse(isDockTunerSwitchEnabled(testPreferences().copy(areChordsEnabled = false, isDockMetronomeEnabled = false)))

    @Test
    fun `the chords keep the switch without any metronome`() =
        assertTrue(isDockTunerSwitchEnabled(testPreferences().copy(isMetronomeEnabled = false)))

    @Test
    fun `the tuner feature off disables the switch`() =
        assertFalse(isDockTunerSwitchEnabled(testPreferences().copy(isTunerEnabled = false)))
}
