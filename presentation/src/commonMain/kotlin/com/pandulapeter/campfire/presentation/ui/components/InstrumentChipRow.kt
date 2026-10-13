/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The [InstrumentChips] in a row of their own, at the size of the body text, the tuning chip going under the
 * instrument's where the two do not fit side by side rather than either being cut short.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InstrumentChipRow(
    modifier: Modifier = Modifier,
    instrument: UserPreferences.Instrument,
    tuning: InstrumentTuning?,
    notation: ChordNotation,
    isEnabled: Boolean = true,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
) = FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
    verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
) {
    InstrumentChips(
        instrument = instrument,
        tuning = tuning,
        notation = notation,
        isEnabled = isEnabled,
        onInstrumentClicked = onInstrumentClicked,
        onTuningClicked = onTuningClicked,
    )
}
