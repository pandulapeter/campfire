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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.onboarding_tuning_of_instrument
import com.pandulapeter.campfire.presentation.resources.tuner_tuning
import com.pandulapeter.campfire.presentation.ui.components.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.components.SelectableChip
import com.pandulapeter.campfire.presentation.ui.components.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.tuner.instrumentLabel
import com.pandulapeter.campfire.presentation.ui.tuner.instrumentOf
import com.pandulapeter.campfire.presentation.ui.tuner.tuningLabel
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The tunings of the instrument picked on the onboarding's "What do you play?" page ([OnboardingInstrument.tuningsOffered]),
 * as a radio group of chips set the way the Tuner tab sets its own. A tuning of the instrument's first tuner instrument
 * is named the way the tab names it ("Drop D"), the standard tuning of another one by that instrument ("7-string guitar"),
 * and any other tuning of it by both, its note in the reader's [notation].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OnboardingTuningChoice(
    modifier: Modifier = Modifier,
    instrument: OnboardingInstrument,
    selected: InstrumentTuning?,
    notation: ChordNotation,
    onSelected: (InstrumentTuning) -> Unit,
) = SettingsSubsection(
    modifier = modifier,
    title = stringResource(Res.string.tuner_tuning),
    shouldApplyPadding = false,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        instrument.tuningsOffered(selected).forEach { tuning ->
            SelectableChip(
                isSelected = tuning == selected,
                role = Role.RadioButton,
                onClick = { onSelected(tuning) },
            ) { contentColor ->
                Text(
                    text = onboardingTuningLabel(instrument, tuning, notation),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
private fun onboardingTuningLabel(instrument: OnboardingInstrument, tuning: InstrumentTuning, notation: ChordNotation): String {
    val tunerInstrument = instrumentOf(tuning)
    return when {
        tunerInstrument == null || tunerInstrument == instrument.tunerInstruments.firstOrNull() -> tuningLabel(tuning, notation)
        tuning == tunerInstrument.tunings.first() -> instrumentLabel(tunerInstrument)
        else -> stringResource(Res.string.onboarding_tuning_of_instrument, instrumentLabel(tunerInstrument), tuningLabel(tuning, notation))
    }
}
