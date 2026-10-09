/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/**
 * The tuner's preferences, stored as the tuner's own ids rather than as its types so that the model depends on nothing;
 * an instrument this version does not know reads as chromatic.
 *
 * @param instrumentId The preset strings are tuned against, `chromatic` for the nearest semitone.
 * @param referencePitch The frequency of A4 in Hz, within [REFERENCE_PITCH_RANGE].
 */
data class TunerSettings(
    val instrumentId: String = "chromatic",
    val referencePitch: Int = 440,
) {

    companion object {
        /** The reference pitches the tuner offers: a baroque A at the bottom and a semitone over concert pitch at the top. */
        val REFERENCE_PITCH_RANGE = 415..466
    }
}
