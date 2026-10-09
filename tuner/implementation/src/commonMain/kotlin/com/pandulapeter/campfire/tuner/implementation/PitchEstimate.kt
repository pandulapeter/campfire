/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.tuner.implementation

/**
 * What [PitchDetector] makes of one window.
 *
 * @param frequency The pitch in Hz, null where no clear one was found in the range asked for.
 * @param clarity How periodic the window is at that pitch, from 0 to 1: the height of the peak it was read from.
 * @param level The window's RMS, full scale being 1: exactly 0 only for digital silence.
 */
internal data class PitchEstimate(
    val frequency: Float?,
    val clarity: Float,
    val level: Float,
)
