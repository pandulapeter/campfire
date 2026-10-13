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
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument
import com.pandulapeter.campfire.presentation.resources.instrument_description
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.InstrumentList
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll

/**
 * The instrument in the player's hands, the one instrument picker the song details screen, the Tuner tab, the Chords tab,
 * and Settings all open: every instrument the app knows ([InstrumentList]). Its tuning is the [TuningSheet]'s, so that
 * neither list is long enough to hide the other. A pick is written at once and closes the sheet, so that the diagrams or
 * the strings it changes are what is seen next.
 */
@Composable
internal fun InstrumentSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.Instrument,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val preferences = userPreferences ?: return
    CampfireBottomSheet(
        title = stringResource(Res.string.instrument),
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
                text = stringResource(Res.string.instrument_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InstrumentList(
                selected = preferences.instrument,
                onSelected = { instrument ->
                    viewModel.setInstrument(instrument)
                    closeSheet()
                },
            )
        }
    }
}
