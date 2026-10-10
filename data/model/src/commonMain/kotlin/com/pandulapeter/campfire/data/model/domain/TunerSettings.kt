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
 * @param hasTurnedOnMicrophone Whether the tuner's page has ever been told to use the microphone on this device, which
 * is what the platforms that cannot say whether it is allowed (the desktop, a browser without the Permissions API) go by:
 * there opening the input is the question, so it is only opened without a tap once a tap has asked, in any run since.
 */
data class TunerSettings(
    val instrumentId: String = "chromatic",
    val referencePitch: Int = 440,
    val hasTurnedOnMicrophone: Boolean = false,
) {

    companion object {
        /** The reference pitches the tuner offers: a baroque A at the bottom and a semitone over concert pitch at the top. */
        val REFERENCE_PITCH_RANGE = 415..466
    }
}
