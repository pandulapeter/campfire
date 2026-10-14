/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.instrument

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.instrument
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSubsection
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The instrument in the player's hands as a settings row: titled Instrument, and holding the [InstrumentChipRow] that
 * opens the instrument's sheet and the tuning's. The Tuner tab and Settings each show it, so that the instrument is
 * changed where its effect is seen.
 */
@Composable
internal fun InstrumentSetting(
    modifier: Modifier = Modifier,
    instrument: UserPreferences.Instrument,
    tuning: InstrumentTuning?,
    notation: ChordNotation,
    description: String? = null,
    isEnabled: Boolean = true,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
) = SettingsSubsection(
    modifier = modifier,
    title = stringResource(Res.string.instrument),
    description = description,
    isEnabled = isEnabled,
) {
    InstrumentChipRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        instrument = instrument,
        tuning = tuning,
        notation = notation,
        isEnabled = isEnabled,
        onInstrumentClicked = onInstrumentClicked,
        onTuningClicked = onTuningClicked,
    )
}
