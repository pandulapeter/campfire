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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_instrument
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.tuner.InstrumentPicker
import com.pandulapeter.campfire.presentation.ui.tuner.toConfig
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The instrument and the tuning the tuner reads against, opened from the Tuner tab ([InstrumentPicker]). A pick is
 * written at once and closes the sheet, like a choice from a menu, so the sheet has nothing to save and its close button
 * cancels nothing; the tab under it, listening all the while, reads against the new strings as the sheet slides away.
 */
@Composable
internal fun TunerInstrumentSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.TunerInstrument,
) {
    val settings by viewModel.tunerSettings.collectAsStateWithLifecycle()
    val selected = remember(settings) { settings.toConfig() }.tuning
    CampfireBottomSheet(
        title = stringResource(Res.string.tuner_instrument),
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        InstrumentPicker(
            selected = selected,
            contentPadding = contentPadding,
            fadeColor = sheetContainerColor(),
            onSelected = { tuning ->
                viewModel.updateTunerSettings { copy(instrumentId = tuning?.id ?: InstrumentTuning.CHROMATIC_ID) }
                close()
            },
        )
    }
}
