/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import com.pandulapeter.campfire.data.model.preferences.TunerSettings
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import com.pandulapeter.campfire.tuner.api.model.StringCourses
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TunerConfigTest {

    @Test
    fun `strings mode listens for the instrument's strings`() {
        val config = tunerConfigOf(TunerSettings(), InstrumentTuning.GUITAR, StringCourses.SINGLE)
        assertEquals(InstrumentTuning.GUITAR.strings, config.strings)
    }

    @Test
    fun `chromatic mode reads every note whatever the instrument`() {
        val config = tunerConfigOf(TunerSettings(mode = TunerSettings.Mode.CHROMATIC, referencePitch = 432), InstrumentTuning.GUITAR, StringCourses.SINGLE)
        assertNull(config.strings)
        assertEquals(432, config.referencePitch)
    }
}
