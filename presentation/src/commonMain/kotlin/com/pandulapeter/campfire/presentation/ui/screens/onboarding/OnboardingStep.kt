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
 * decide whether the later ones are asked at all ([onboardingSteps]). The features come before everything that depends
 * on them. The welcome and the look are two pages rather than one, so that neither is a page too long for a window
 * while the next is a page with almost nothing on it. Serializable because the page is saved with the back stack, see
 * `Navigator.onboardingStep`.
 */
@Serializable
enum class OnboardingStep {
    WELCOME,
    LOOK,
    INSTRUMENT,
    FEATURES,
    NOTATION,
    SONG_LAYOUT,
    SYNC,
}

/**
 * The steps worth showing, given the features switched on: the notation only with the chords on, and the cloud folder only
 * where the build can connect one, since a page offering a button that does nothing is worse than no page. The
 * instrument is always asked, being asked before the features it is for. What a song card shows under its artist is
 * not asked: the defaults follow the features, and Settings changes it.
 */
internal fun onboardingSteps(
    canConnectSync: Boolean,
    areChordsEnabled: Boolean,
) = OnboardingStep.entries.filter { step ->
    when (step) {
        OnboardingStep.WELCOME, OnboardingStep.LOOK, OnboardingStep.INSTRUMENT, OnboardingStep.FEATURES, OnboardingStep.SONG_LAYOUT -> true
        OnboardingStep.NOTATION -> areChordsEnabled
        OnboardingStep.SYNC -> canConnectSync
    }
}

/**
 * The step after [current] among [steps], null where [current] is the last one. Counted by the steps' order rather than
 * by [current]'s place in [steps], which it is not part of where an answer took it away while it was on screen.
 */
internal fun nextOnboardingStep(current: OnboardingStep, steps: List<OnboardingStep>) = steps.firstOrNull { it > current }

/** The step before [current] among [steps], null on the first one, counted the same way as [nextOnboardingStep]. */
internal fun previousOnboardingStep(current: OnboardingStep, steps: List<OnboardingStep>) = steps.lastOrNull { it < current }
