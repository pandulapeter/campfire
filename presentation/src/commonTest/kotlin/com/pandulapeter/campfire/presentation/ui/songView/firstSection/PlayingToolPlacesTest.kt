/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.firstSection

import com.pandulapeter.campfire.presentation.ui.songView.firstSection.PlayingToolPlace.AFTER_CHORD_VALUES
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.PlayingToolPlace.AFTER_CLICK_VALUES
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.PlayingToolPlace.ALONE
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayingToolPlacesTest {

    @Test
    fun `the tuner follows the key and the capo, and the metronome the tempo and the time signature`() {
        assertEquals(PlayingToolPlaces(tuner = AFTER_CHORD_VALUES, metronome = AFTER_CLICK_VALUES), places(hasChordValues = true, hasClickValues = true))
    }

    @Test
    fun `a button whose values are not shown follows the other group`() {
        assertEquals(PlayingToolPlaces(tuner = AFTER_CLICK_VALUES, metronome = AFTER_CLICK_VALUES), places(hasChordValues = false, hasClickValues = true))
        assertEquals(PlayingToolPlaces(tuner = AFTER_CHORD_VALUES, metronome = AFTER_CHORD_VALUES), places(hasChordValues = true, hasClickValues = false))
    }

    @Test
    fun `the buttons stand alone where no value is shown`() {
        assertEquals(PlayingToolPlaces(tuner = ALONE, metronome = ALONE), places(hasChordValues = false, hasClickValues = false))
    }

    @Test
    fun `a tool switched off has no place`() {
        assertEquals(
            PlayingToolPlaces(tuner = null, metronome = AFTER_CLICK_VALUES),
            playingToolPlaces(hasTuner = false, hasMetronome = true, hasInstrumentChips = false, hasChordValues = true, hasClickValues = true),
        )
        assertEquals(
            PlayingToolPlaces(tuner = AFTER_CHORD_VALUES, metronome = null),
            playingToolPlaces(hasTuner = true, hasMetronome = false, hasInstrumentChips = false, hasChordValues = true, hasClickValues = true),
        )
    }

    private fun places(hasChordValues: Boolean, hasClickValues: Boolean) =
        playingToolPlaces(hasTuner = true, hasMetronome = true, hasInstrumentChips = false, hasChordValues = hasChordValues, hasClickValues = hasClickValues)
}
