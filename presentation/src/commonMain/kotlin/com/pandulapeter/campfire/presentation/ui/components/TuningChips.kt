/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
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
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.instrument.tunings
import com.pandulapeter.campfire.presentation.ui.tuner.tuningLabel
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * The tunings of [instrument], as a wrapping row of chips that is a radio group, standard first: each named
 * the way the tuner names it, its note in the reader's chord [notation], and none needing the instrument in its name,
 * since that is the one picked right above it. The chips crossfade as one instrument's tunings follow another's.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TuningChips(
    modifier: Modifier = Modifier,
    instrument: UserPreferences.Instrument,
    selected: InstrumentTuning,
    notation: ChordNotation,
    onSelected: (InstrumentTuning) -> Unit,
) = AnimatedContent(
    modifier = modifier,
    // The tunings rather than the instrument, so that the guitar and the twelve-string, which share them, keep the chips still.
    targetState = instrument.tunings,
    transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = false) },
    contentAlignment = Alignment.TopStart,
    label = "tunings",
) { shownTunings ->
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        shownTunings.forEach { tuning ->
            SelectableChip(
                isSelected = tuning == selected,
                role = Role.RadioButton,
                onClick = { if (tuning != selected) onSelected(tuning) },
            ) { contentColor ->
                Text(
                    text = tuningLabel(tuning, notation),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
            }
        }
    }
}
