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

import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_choose
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.RadioListItem
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentLabel
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentTuning
import com.pandulapeter.campfire.presentation.ui.instrument.tuningStringsLabel
import com.pandulapeter.campfire.presentation.ui.instrument.tunings
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.tuner.tuningLabel

/**
 * The tuning of the instrument in the player's hands, opened from the tuning chip next to the instrument's wherever that
 * is: its tunings as a list, standard first, each named the way the tuner names it and with its open strings under it in
 * the chord notation, since those are what the diagrams are drawn on and the tuner listens for. A pick is written at once
 * and closes the sheet, as the [InstrumentSheet]'s does.
 */
@Composable
internal fun TuningSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.Tuning,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val preferences = userPreferences ?: return
    // Only an instrument with a choice of tunings has the chip that opens this, so any other has nothing to list here.
    if (preferences.instrument.tunings.size <= 1) return LaunchedEffect(Unit) { viewModel.dismissSheet(dialog) }
    val notation = preferences.chordSpelling.notation.toChordNotation()
    val selected = preferences.instrumentTuning
    CampfireBottomSheet(
        title = stringResource(Res.string.instrument_tuning_choose),
        // Named under the title, since the tunings are that instrument's and its chip is behind the sheet.
        subtitle = instrumentLabel(preferences.instrument),
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
                .padding(bottom = 8.dp)
                .selectableGroup(),
        ) {
            preferences.instrument.tunings.forEach { tuning ->
                RadioListItem(
                    title = tuningLabel(tuning, notation),
                    description = tuningStringsLabel(tuning, notation),
                    isSelected = tuning == selected,
                    onSelected = {
                        viewModel.setTuning(tuning)
                        closeSheet()
                    },
                )
            }
        }
    }
}
