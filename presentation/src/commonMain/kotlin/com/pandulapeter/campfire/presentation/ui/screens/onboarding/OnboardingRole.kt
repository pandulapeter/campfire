/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.onboarding

/**
 * The answers to "How will you use Campfire?", each a starting point for the four features a newcomer decides on, which
 * stay switches of their own under the answers: a role is a way of setting them all at once rather than a setting, so it
 * is not stored, and nothing is picked until the user picks it.
 */
internal enum class OnboardingRole(
    val features: OnboardingFeatures,
) {
    /** Somebody playing along: the chords and everything that helps play them, without the gig's running order. */
    PLAYER(OnboardingFeatures(areChordsEnabled = true, areSetlistsEnabled = false, isMetronomeEnabled = true, isTunerEnabled = true)),

    /** Somebody singing: the words in the order of the evening, without anything that is only there for an instrument. */
    SINGER(OnboardingFeatures(areChordsEnabled = false, areSetlistsEnabled = true, isMetronomeEnabled = false, isTunerEnabled = false)),

    /** Somebody leading a band or a group: everything, since they are the one who counts the songs in and keeps the list. */
    LEADER(OnboardingFeatures(areChordsEnabled = true, areSetlistsEnabled = true, isMetronomeEnabled = true, isTunerEnabled = true)),
}

/** The four features of Settings → Features that the onboarding asks about. */
internal data class OnboardingFeatures(
    val areChordsEnabled: Boolean,
    val areSetlistsEnabled: Boolean,
    val isMetronomeEnabled: Boolean,
    val isTunerEnabled: Boolean,
)
