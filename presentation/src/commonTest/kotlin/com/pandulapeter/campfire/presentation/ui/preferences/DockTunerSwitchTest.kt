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
    fun `the switch stays enabled while it is off so that it can be turned on again`() =
        assertTrue(isDockTunerSwitchEnabled(testPreferences().copy(isDockTunerEnabled = false)))

    @Test
    fun `the tuner feature off hides the switch rather than disabling it`() {
        assertFalse(isDockTunerSwitchShown(testPreferences().copy(isTunerEnabled = false)))
        assertTrue(isDockTunerSwitchShown(testPreferences()))
        assertTrue(isDockTunerSwitchEnabled(testPreferences().copy(isTunerEnabled = false)))
    }

    @Test
    fun `the chord diagrams off keep the one line instrument card for the tuner and the switch enabled`() =
        assertTrue(isDockTunerSwitchEnabled(testPreferences().copy(areChordDiagramsEnabled = false)))

    @Test
    fun `lyrics only mode leaves no diagrams and the switch is disabled`() =
        assertFalse(isDockTunerSwitchEnabled(testPreferences().copy(areChordsEnabled = false)))

    @Test
    fun `the switch does not depend on the metronome`() =
        assertTrue(isDockTunerSwitchEnabled(testPreferences().copy(isMetronomeEnabled = false, isDockMetronomeEnabled = false)))
}
