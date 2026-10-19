/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.scales

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.scales.ScaleFamily
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.chords_key
import com.pandulapeter.campfire.presentation.resources.scale_family_common
import com.pandulapeter.campfire.presentation.resources.scales
import com.pandulapeter.campfire.presentation.ui.chrome.TOOL_PAGE_PADDING
import com.pandulapeter.campfire.presentation.ui.components.bleedHorizontally
import com.pandulapeter.campfire.presentation.ui.components.controls.ChoiceChipRow
import com.pandulapeter.campfire.presentation.ui.components.controls.SideBySideWhenFits
import com.pandulapeter.campfire.presentation.ui.instrument.InstrumentSetting
import com.pandulapeter.campfire.presentation.ui.scales.label
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSubsection
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * What the Scales tab lists: the instrument in the player's hands and its tuning, through the chips every screen opens
 * their sheets with and written at once as everywhere, the key, beside them where the window has room for both, and the
 * family, the common scales first. It reaches past the page's side padding for the reason `ChordLibraryControls` gives.
 */
@Composable
internal fun ScaleLibraryControls(
    modifier: Modifier = Modifier,
    instrument: UserPreferences.Instrument,
    instrumentTuning: InstrumentTuning?,
    notation: ChordNotation,
    keys: List<String>,
    selectedKeyIndex: Int,
    selectedFamilyIndex: Int,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
    onKeySelected: (Int) -> Unit,
    onFamilySelected: (Int) -> Unit,
) = Column(
    modifier = modifier.bleedHorizontally(TOOL_PAGE_PADDING),
) {
    SideBySideWhenFits(
        first = {
            InstrumentSetting(
                instrument = instrument,
                tuning = instrumentTuning,
                notation = notation,
                onInstrumentClicked = onInstrumentClicked,
                onTuningClicked = onTuningClicked,
            )
        },
        second = {
            SettingsSubsection(title = stringResource(Res.string.chords_key)) {
                ChoiceChipRow(
                    labels = keys.map { ChordProNotation.shownName(it, notation) },
                    selectedIndex = selectedKeyIndex,
                    onSelected = onKeySelected,
                )
            }
        },
    )
    SettingsSubsection(title = stringResource(Res.string.scales)) {
        ChoiceChipRow(
            labels = listOf(stringResource(Res.string.scale_family_common)) + ScaleFamily.entries.map { stringResource(it.label) },
            selectedIndex = selectedFamilyIndex,
            onSelected = onFamilySelected,
        )
    }
}
