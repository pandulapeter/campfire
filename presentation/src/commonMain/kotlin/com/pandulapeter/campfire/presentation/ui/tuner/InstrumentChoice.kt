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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_instrument
import com.pandulapeter.campfire.presentation.resources.tuner_instrument_description
import com.pandulapeter.campfire.presentation.resources.tuner_tuning
import com.pandulapeter.campfire.presentation.ui.components.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.components.SelectableChip
import com.pandulapeter.campfire.presentation.ui.components.SettingsSubsection
import com.pandulapeter.campfire.presentation.ui.screens.settings.AnimatedSettingsRow
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * What a sound is read against, on the Tuner tab alone (the sheet over a song tunes to whatever is picked here): the
 * instrument as chips, like the Metronome tab's time signatures, chromatic first, and under it the instrument's tunings
 * as chips of their own, where it has more than one. An instrument picks its standard tuning, and a tuning chip needs
 * no instrument in its name, since the instrument is the one picked right above it; its note is in the reader's [notation].
 */
@Composable
internal fun ColumnScope.InstrumentChoice(
    selected: InstrumentTuning?,
    notation: ChordNotation,
    onSelected: (InstrumentTuning?) -> Unit,
) {
    val selectedInstrument = instrumentOf(selected)
    SettingsSubsection(
        title = stringResource(Res.string.tuner_instrument),
        description = stringResource(Res.string.tuner_instrument_description),
    ) {
        Chips(
            options = listOf(null) + TunerInstrument.entries,
            isSelected = { it == selectedInstrument },
            label = { instrumentLabel(it) },
            onClick = { instrument -> if (instrument != selectedInstrument) onSelected(instrument?.tunings?.first()) },
        )
    }
    AnimatedSettingsRow(value = selectedInstrument?.takeIf { it.tunings.size > 1 }) { instrument ->
        SettingsSubsection(title = stringResource(Res.string.tuner_tuning)) {
            // The row stays while one instrument with tunings follows another, so its chips are what change.
            AnimatedContent(
                targetState = instrument,
                transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
                contentAlignment = Alignment.TopStart,
                label = "tunerTunings",
            ) { shownInstrument ->
                Chips(
                    options = shownInstrument.tunings,
                    isSelected = { it == selected },
                    label = { tuningLabel(it, notation) },
                    onClick = onSelected,
                )
            }
        }
    }
}

/** One choice as a wrapping row of chips that is a radio group, set the way the time signatures are. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(
    options: List<T>,
    isSelected: (T) -> Boolean,
    label: @Composable (T) -> String,
    onClick: (T) -> Unit,
) = FlowRow(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
    verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
) {
    options.forEach { option ->
        SelectableChip(
            isSelected = isSelected(option),
            role = Role.RadioButton,
            onClick = { onClick(option) },
        ) { contentColor ->
            Text(
                text = label(option),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}
