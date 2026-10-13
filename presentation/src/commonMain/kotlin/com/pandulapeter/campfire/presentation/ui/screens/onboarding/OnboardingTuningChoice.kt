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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument_tuning
import com.pandulapeter.campfire.presentation.ui.components.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.components.TuningChips
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The tunings of the instrument picked on the onboarding's "What do you play?" page, the instrument sheet's own chips
 * under a title, so that the page and the sheet offer the same tunings by the same names.
 */
@Composable
internal fun OnboardingTuningChoice(
    modifier: Modifier = Modifier,
    selected: InstrumentTuning,
    notation: ChordNotation,
    onSelected: (InstrumentTuning) -> Unit,
) = SettingsSubsection(
    modifier = modifier,
    title = stringResource(Res.string.instrument_tuning),
    shouldApplyPadding = false,
) {
    TuningChips(
        selected = selected,
        notation = notation,
        onSelected = onSelected,
    )
}
