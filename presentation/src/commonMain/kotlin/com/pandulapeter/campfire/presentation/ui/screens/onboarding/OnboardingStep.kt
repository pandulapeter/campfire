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
 * The pages of the onboarding, in the order they are shown. Each asks one thing, and the answers to the earlier ones
 * decide whether the later ones are asked at all ([onboardingSteps]), which is why the role, which decides the features,
 * comes before everything that depends on them.
 */
enum class OnboardingStep {
    WELCOME,
    ROLE,
    INSTRUMENT,
    NOTATION,
    AT_A_GLANCE,
    SYNC,
    DONE,
}

/**
 * The steps worth showing, given the features switched on: the instrument only while there is something it decides (the
 * chord diagrams with the chords on, the tuning with the tuner on), the notation only with the chords on, what a song
 * shows at a glance only while it has a key (the chords) or a tempo (the metronome) to show, and the cloud folder only
 * where the build can connect Dropbox, since a page offering a button that does nothing is worse than no page.
 */
internal fun onboardingSteps(
    canConnectDropbox: Boolean,
    areChordsEnabled: Boolean,
    isMetronomeEnabled: Boolean,
    isTunerEnabled: Boolean,
) = OnboardingStep.entries.filter { step ->
    when (step) {
        OnboardingStep.WELCOME, OnboardingStep.ROLE, OnboardingStep.DONE -> true
        OnboardingStep.INSTRUMENT -> areChordsEnabled || isTunerEnabled
        OnboardingStep.NOTATION -> areChordsEnabled
        OnboardingStep.AT_A_GLANCE -> areChordsEnabled || isMetronomeEnabled
        OnboardingStep.SYNC -> canConnectDropbox
    }
}

/**
 * The step after [current] among [steps], null where [current] is the last one. Counted by the steps' order rather than
 * by [current]'s place in [steps], which it is not part of where an answer took it away while it was on screen.
 */
internal fun nextOnboardingStep(current: OnboardingStep, steps: List<OnboardingStep>) = steps.firstOrNull { it > current }

/** The step before [current] among [steps], null on the first one, counted the same way as [nextOnboardingStep]. */
internal fun previousOnboardingStep(current: OnboardingStep, steps: List<OnboardingStep>) = steps.lastOrNull { it < current }
