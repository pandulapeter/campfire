/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.instrument

import com.pandulapeter.campfire.data.model.preferences.UserPreferences.Instrument
import com.pandulapeter.campfire.presentation.ui.playing.testPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ToolCapoTest {

    private fun on(instrument: Instrument) = testPreferences().copy(instrument = instrument, toolCapo = 2)

    @Test
    fun `the Chords tab takes the capo on every instrument it draws fretted shapes for`() {
        assertEquals(2, on(Instrument.GUITAR).chordLibraryCapo())
        assertEquals(2, on(Instrument.UKULELE).chordLibraryCapo())
        assertEquals(2, on(Instrument.GUITAR_TWELVE_STRING).chordLibraryCapo())
        assertNull(on(Instrument.KEYBOARD).chordLibraryCapo())
        assertNull(on(Instrument.VIOLIN).chordLibraryCapo())
        assertNull(on(Instrument.OTHER).chordLibraryCapo())
    }

    @Test
    fun `the Scales tab takes the capo on every fretted neck, with or without chord shapes`() {
        assertEquals(2, on(Instrument.GUITAR).scaleLibraryCapo())
        assertEquals(2, on(Instrument.BASS_FIVE_STRING).scaleLibraryCapo())
        assertEquals(2, on(Instrument.GUITAR_SEVEN_STRING).scaleLibraryCapo())
        assertNull(on(Instrument.VIOLIN).scaleLibraryCapo())
        assertNull(on(Instrument.KEYBOARD).scaleLibraryCapo())
        assertNull(on(Instrument.OTHER).scaleLibraryCapo())
    }
}
