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

import kotlinx.serialization.Serializable

/**
 * The pages of the onboarding, in the order they are shown. Each asks one thing, and the answers to the earlier ones
 * decide whether the later ones are asked at all ([onboardingSteps]), which is why the features come before everything
 * that depends on them. Serializable because the page is saved with the back stack, see `Navigator.onboardingStep`.
 */
@Serializable
enum class OnboardingStep {
    WELCOME,
    FEATURES,
    INSTRUMENT,
    NOTATION,
    AT_A_GLANCE,
    SYNC,
}

/**
 * The steps worth showing, given the features switched on: the instrument only while there is something it decides (the
 * chord diagrams with the chords on, the tuning with the tuner on), the notation only with the chords on, and the cloud
 * folder only where the build can connect Dropbox, since a page offering a button that does nothing is worse than no
 * page. What a song card shows at a glance is always asked, since its duration goes with no feature.
 */
internal fun onboardingSteps(
    canConnectDropbox: Boolean,
    areChordsEnabled: Boolean,
    isTunerEnabled: Boolean,
) = OnboardingStep.entries.filter { step ->
    when (step) {
        OnboardingStep.WELCOME, OnboardingStep.FEATURES, OnboardingStep.AT_A_GLANCE -> true
        OnboardingStep.INSTRUMENT -> areChordsEnabled || isTunerEnabled
        OnboardingStep.NOTATION -> areChordsEnabled
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
