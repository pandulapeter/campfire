/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_banjo
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_bass
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_chromatic
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_guitar
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_guitar_drop_d
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_mandolin
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_ukulele
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_violin
import com.pandulapeter.campfire.presentation.ui.components.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.components.SelectableChip
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/** What a sound is read against: the nearest semitone (chromatic, null), or the strings of one of the presets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InstrumentChoice(
    selected: InstrumentTuning?,
    onSelected: (InstrumentTuning?) -> Unit,
) = FlowRow(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
    verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
) {
    (listOf(null) + InstrumentTuning.entries).forEach { tuning ->
        SelectableChip(
            isSelected = tuning == selected,
            role = Role.RadioButton,
            onClick = { onSelected(tuning) },
        ) { contentColor ->
            Text(
                text = instrumentLabel(tuning),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun instrumentLabel(tuning: InstrumentTuning?) = stringResource(
    when (tuning) {
        null -> Res.string.tuner_instrument_chromatic
        InstrumentTuning.GUITAR -> Res.string.tuner_instrument_guitar
        InstrumentTuning.GUITAR_DROP_D -> Res.string.tuner_instrument_guitar_drop_d
        InstrumentTuning.BASS -> Res.string.tuner_instrument_bass
        InstrumentTuning.UKULELE -> Res.string.tuner_instrument_ukulele
        InstrumentTuning.VIOLIN -> Res.string.tuner_instrument_violin
        InstrumentTuning.MANDOLIN -> Res.string.tuner_instrument_mandolin
        InstrumentTuning.BANJO -> Res.string.tuner_instrument_banjo
    }
)
