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

import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.instrument.hasDiagrams

/**
 * The answers the onboarding's "What do you play?" offers: every instrument with the tuner on, and with it off only the
 * ones the answer still decides something for, the instruments the diagrams are drawn for and Other, since every other
 * instrument would be Other by another name.
 */
internal fun onboardingInstruments(isTunerEnabled: Boolean) = UserPreferences.Instrument.entries.filter {
    isTunerEnabled || it.hasDiagrams || it == UserPreferences.Instrument.OTHER
}
