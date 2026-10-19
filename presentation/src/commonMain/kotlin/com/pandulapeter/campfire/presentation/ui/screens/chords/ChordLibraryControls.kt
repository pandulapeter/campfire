/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.chords

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.chords_key
import com.pandulapeter.campfire.presentation.ui.chords.shownChordName
import com.pandulapeter.campfire.presentation.ui.components.controls.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.instrument.InstrumentSetting
import com.pandulapeter.campfire.presentation.ui.components.controls.SelectableChip
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSubsection
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * What the Chords tab lists: the instrument in the player's hands and its tuning, through the chips every screen opens
 * their sheets with, and the key; the search under them is [chordLibrarySearchField]. The instrument is the app's one,
 * which the songs' diagrams are drawn for too, so a pick here is written at once and changes them as well.
 *
 * It stands in the page's grid, inside the page's side padding, while the instrument is a shared row that pads itself as
 * a sheet's does; so it reaches past that padding by as much ([CHORDS_PAGE_PADDING]) and lines its own rows up with it.
 */
@Composable
internal fun ChordLibraryControls(
    modifier: Modifier = Modifier,
    instrument: UserPreferences.Instrument,
    instrumentTuning: InstrumentTuning?,
    notation: ChordNotation,
    keys: List<String>,
    selectedKeyIndex: Int,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
    onKeySelected: (Int) -> Unit,
) = Column(
    modifier = modifier.bleedHorizontally(CHORDS_PAGE_PADDING),
) {
    // Titled the way the Tuner tab and Settings title their instrument and every other control set under its name.
    InstrumentSetting(
        instrument = instrument,
        tuning = instrumentTuning,
        notation = notation,
        onInstrumentClicked = onInstrumentClicked,
        onTuningClicked = onTuningClicked,
    )
    SettingsSubsection(title = stringResource(Res.string.chords_key)) {
        ChipRow {
            keys.forEachIndexed { index, key ->
                ChoiceChip(
                    text = shownChordName(key, notation),
                    isSelected = index == selectedKeyIndex,
                    onClick = { onKeySelected(index) },
                )
            }
        }
    }
}

/** A wrapping row of chips that is a radio group, padded as the page's headings are. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) = FlowRow(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
    verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
) {
    content()
}

@Composable
private fun ChoiceChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) = SelectableChip(
    isSelected = isSelected,
    role = Role.RadioButton,
    onClick = onClick,
) { contentColor ->
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = contentColor,
    )
}

/** The Chords tab's padding at its sides, which the shared rows of [ChordLibraryControls] bring with them. */
internal val CHORDS_PAGE_PADDING = 16.dp
