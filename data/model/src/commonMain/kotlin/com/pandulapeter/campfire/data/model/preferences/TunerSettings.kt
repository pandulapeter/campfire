/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.preferences

/**
 * The tuner's own preferences; the instrument it listens for is [UserPreferences.instrument], shared with the chord
 * diagrams.
 *
 * @param mode Whether the tuner reads what it hears against the instrument's strings or against the nearest note.
 * @param referencePitch The frequency of A4 in Hz, within [REFERENCE_PITCH_RANGE].
 * @param hasTurnedOnMicrophone Whether the tuner's page has ever been told to use the microphone on this device, which
 * is what the platforms that cannot say whether it is allowed (the desktop, a browser without the Permissions API) go by:
 * there opening the input is the question, so it is only opened without a tap once a tap has asked, in any run since.
 */
data class TunerSettings(
    val mode: Mode = Mode.STRINGS,
    val referencePitch: Int = 440,
    val hasTurnedOnMicrophone: Boolean = false,
) {

    /**
     * [CHROMATIC] reads every note, whatever the instrument in the player's hands, which it leaves as it is for the
     * chord diagrams and the scales; [STRINGS] reads against that instrument's strings, every note for one with none.
     */
    enum class Mode(val id: String) {
        STRINGS("strings"),
        CHROMATIC("chromatic"),
    }

    companion object {
        /** The reference pitches the tuner offers: a baroque A at the bottom and a semitone over concert pitch at the top. */
        val REFERENCE_PITCH_RANGE = 415..466
    }
}
