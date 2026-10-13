/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_description
import com.pandulapeter.campfire.presentation.resources.settings_chord_tuning
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.chords.chordTuning
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.ChordInstrumentList
import com.pandulapeter.campfire.presentation.ui.components.ChordTuningChips
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll

/**
 * The instrument the chord diagrams are drawn for and its tuning, as Settings offers them but without Other, which would
 * take away the Chords section the sheet was opened from. A pick is written at once and closes the sheet, so that the
 * diagrams it changes are what is seen next.
 */
@Composable
internal fun ChordInstrumentSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.ChordInstrument,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val notation = (userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default).notation.toChordNotation()
    CampfireBottomSheet(
        title = stringResource(Res.string.settings_chord_instrument),
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        val scrollState = rememberScrollState()
        val closeSheet = { close() }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .fadingTopEdge(scrollState, sheetContainerColor())
                .bounceVerticalScroll(scrollState)
                .padding(contentPadding)
                .padding(bottom = 8.dp),
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = stringResource(Res.string.settings_chord_instrument_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChordInstrumentList(
                selected = userPreferences?.chordInstrument,
                chordTunings = userPreferences?.chordTunings.orEmpty(),
                notation = notation,
                isOtherShown = false,
                onSelected = { instrument ->
                    viewModel.setChordInstrument(instrument)
                    closeSheet()
                },
            )
            userPreferences?.chordTuning()?.takeIf { ChordTuning.of(it.instrument).size > 1 }?.let { tuning ->
                Text(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    text = stringResource(Res.string.settings_chord_tuning),
                    style = MaterialTheme.typography.titleSmall,
                )
                ChordTuningChips(
                    selected = tuning,
                    notation = notation,
                    onSelected = {
                        viewModel.setChordTuning(it)
                        closeSheet()
                    },
                )
            }
        }
    }
}
