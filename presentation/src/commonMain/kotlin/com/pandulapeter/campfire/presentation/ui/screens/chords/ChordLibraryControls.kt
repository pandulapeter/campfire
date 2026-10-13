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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.chords_key
import com.pandulapeter.campfire.presentation.resources.chords_search
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_guitar
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_keyboard
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_mandolin
import com.pandulapeter.campfire.presentation.resources.settings_chord_instrument_ukulele
import com.pandulapeter.campfire.presentation.resources.settings_chord_tuning
import com.pandulapeter.campfire.presentation.ui.components.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.components.ChordTuningChips
import com.pandulapeter.campfire.presentation.ui.components.SearchField
import com.pandulapeter.campfire.presentation.ui.components.SelectableChip

/**
 * What the Chords tab lists: the instrument and its tuning, which are the ones Settings → Songs draws the songs'
 * diagrams for and are written there at once, the key, and a search for a chord by name. Other, which draws no diagram,
 * is not offered here, and while it is the instrument picked none of the four is selected and the tunings are gone.
 *
 * It stands in the page's grid, inside the page's side padding, while the tunings and the search are shared rows that pad
 * themselves as a sheet's do; so it reaches past that padding by as much ([CHORDS_PAGE_PADDING]) and lines its own rows up with
 * theirs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChordLibraryControls(
    modifier: Modifier = Modifier,
    instrument: UserPreferences.ChordInstrument?,
    tuning: ChordTuning?,
    notation: ChordNotation,
    keys: List<String>,
    selectedKeyIndex: Int,
    query: String,
    onInstrumentSelected: (UserPreferences.ChordInstrument) -> Unit,
    onTuningSelected: (ChordTuning) -> Unit,
    onKeySelected: (Int) -> Unit,
    onQueryChanged: (String) -> Unit,
) = Column(
    modifier = modifier
        .bleedHorizontally(CHORDS_PAGE_PADDING)
        .padding(bottom = 8.dp),
) {
    ControlTitle(text = stringResource(Res.string.settings_chord_instrument))
    ChipRow {
        listOf(
            UserPreferences.ChordInstrument.GUITAR to Res.string.settings_chord_instrument_guitar,
            UserPreferences.ChordInstrument.UKULELE to Res.string.settings_chord_instrument_ukulele,
            UserPreferences.ChordInstrument.MANDOLIN to Res.string.settings_chord_instrument_mandolin,
            UserPreferences.ChordInstrument.KEYBOARD to Res.string.settings_chord_instrument_keyboard,
        ).forEach { (choice, label) ->
            ChoiceChip(
                text = stringResource(label),
                isSelected = choice == instrument,
                onClick = { if (choice != instrument) onInstrumentSelected(choice) },
            )
        }
    }
    // The tunings stay on screen through their exit, which an instrument of one tuning or Other sets off.
    val lastTuning = remember { mutableStateOf(tuning) }
    SideEffect { if (tuning != null) lastTuning.value = tuning }
    AnimatedVisibility(
        visible = tuning != null && ChordTuning.of(tuning.instrument).size > 1,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column {
            ControlTitle(text = stringResource(Res.string.settings_chord_tuning))
            (tuning ?: lastTuning.value)?.let { ChordTuningChips(selected = it, notation = notation, onSelected = onTuningSelected) }
        }
    }
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
        modifier = Modifier.padding(top = 8.dp),
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

/** A wrapping row of chips that is a radio group, padded as [ChordTuningChips] pads its own. */
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
