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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.chords_key
import com.pandulapeter.campfire.presentation.resources.chords_search
import com.pandulapeter.campfire.presentation.resources.instrument
import com.pandulapeter.campfire.presentation.ui.components.controls.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.instrument.InstrumentChipRow
import com.pandulapeter.campfire.presentation.ui.components.SearchField
import com.pandulapeter.campfire.presentation.ui.components.controls.SelectableChip
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * What the Chords tab lists: the instrument in the player's hands and its tuning, through the chips every screen opens
 * their sheets with, the key, and a search for a chord by name. The instrument is the app's one, which the songs' diagrams are
 * drawn for too, so a pick here is written at once and changes them as well.
 *
 * It stands in the page's grid, inside the page's side padding, while the instrument and the search are shared rows that
 * pad themselves as a sheet's do; so it reaches past that padding by as much ([CHORDS_PAGE_PADDING]) and lines its own
 * rows up with theirs.
 */
@Composable
internal fun ChordLibraryControls(
    modifier: Modifier = Modifier,
    instrument: UserPreferences.Instrument,
    instrumentTuning: InstrumentTuning?,
    notation: ChordNotation,
    keys: List<String>,
    selectedKeyIndex: Int,
    query: String,
    searchFieldModifier: Modifier = Modifier,
    onInstrumentClicked: () -> Unit,
    onTuningClicked: () -> Unit,
    onKeySelected: (Int) -> Unit,
    onQueryChanged: (String) -> Unit,
) = Column(
    modifier = modifier
        .bleedHorizontally(CHORDS_PAGE_PADDING)
        .padding(bottom = 8.dp),
) {
    ControlTitle(text = stringResource(Res.string.instrument))
    InstrumentChipRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        instrument = instrument,
        tuning = instrumentTuning,
        notation = notation,
        onInstrumentClicked = onInstrumentClicked,
        onTuningClicked = onTuningClicked,
    )
    ControlTitle(text = stringResource(Res.string.chords_key))
    ChipRow {
        keys.forEachIndexed { index, key ->
            ChoiceChip(
                text = ChordProNotation.shownName(key, notation),
                isSelected = index == selectedKeyIndex,
                onClick = { onKeySelected(index) },
            )
        }
    }
    SearchField(
        modifier = Modifier.padding(top = 8.dp).then(searchFieldModifier),
        query = query,
        placeholder = stringResource(Res.string.chords_search),
        onQueryChange = onQueryChanged,
    )
}

/** The name of a row of controls, a heading as every section title is. */
@Composable
private fun ControlTitle(text: String) = Text(
    modifier = Modifier
        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        .semantics { heading() },
    text = text,
    style = MaterialTheme.typography.titleSmall,
)

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

/** Lays the content out [amount] wider on either side than it is given, over the padding around it. */
private fun Modifier.bleedHorizontally(amount: Dp) = layout { measurable, constraints ->
    val bleed = amount.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + bleed * 2,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + bleed * 2 else constraints.maxWidth,
        ),
    )
    layout(placeable.width - bleed * 2, placeable.height) { placeable.place(-bleed, 0) }
}

/** The Chords tab's padding at its sides, which the shared rows of [ChordLibraryControls] bring with them. */
internal val CHORDS_PAGE_PADDING = 16.dp
