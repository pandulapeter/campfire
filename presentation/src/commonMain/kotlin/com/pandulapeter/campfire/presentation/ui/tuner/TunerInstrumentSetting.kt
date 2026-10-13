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

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_chromatic
import com.pandulapeter.campfire.presentation.resources.tuner_chromatic_description
import com.pandulapeter.campfire.presentation.ui.components.InstrumentSetting
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import com.pandulapeter.campfire.presentation.ui.screens.settings.AnimatedSettingsRow
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * What a sound is read against, on the Tuner tab alone (the sheet over a song tunes to whatever is set here): first the
 * Chromatic switch, which reads the nearest note rather than the nearest string, and while it is off the instrument in
 * the player's hands under it, through the chip every screen opens the instrument sheet with. Chromatic, the instrument
 * has nothing to say about what is heard, so it is not offered; an instrument without strings to tune (the keyboard,
 * Other) is read chromatically either way.
 */
@Composable
internal fun ColumnScope.TunerInstrumentSetting(
    instrument: UserPreferences.Instrument,
    tuning: InstrumentTuning?,
    notation: ChordNotation,
    isChromatic: Boolean,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
    onChromaticChanged: (Boolean) -> Unit,
) {
    SwitchListItem(
        title = stringResource(Res.string.tuner_chromatic),
        description = stringResource(Res.string.tuner_chromatic_description),
        isChecked = isChromatic,
        onCheckedChange = onChromaticChanged,
    )
    AnimatedSettingsRow(isVisible = !isChromatic) {
        InstrumentSetting(
            instrument = instrument,
            tuning = tuning,
            notation = notation,
            onInstrumentClicked = onInstrumentClicked,
            onTuningClicked = onTuningClicked,
        )
    }
}
