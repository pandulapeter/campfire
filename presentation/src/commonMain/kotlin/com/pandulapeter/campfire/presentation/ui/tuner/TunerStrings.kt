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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_string
import com.pandulapeter.campfire.presentation.ui.components.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.components.SelectableChip
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning

/**
 * One chip per string of [tuning], in the order the tuning is written and numbered down to the first, each named by its
 * note. A tap plays the string's tone, a second tap or another chip ends it; the chip of the tone sounding is selected,
 * and so is the one being heard while none sounds, so the hand finds the string the tuner is reading.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TunerStrings(
    tuning: InstrumentTuning,
    notation: ChordNotation,
    tone: Int?,
    heardNote: Int?,
    onToggleTone: (Int) -> Unit,
) = FlowRow(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
    verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
) {
    tuning.strings.forEachIndexed { index, note ->
        val name = noteNameWithOctave(note, notation)
        val description = stringResource(Res.string.tuner_string, tuning.strings.size - index, spokenNoteName(note, notation))
        SelectableChip(
            modifier = Modifier.semantics { contentDescription = description },
            isSelected = if (tone != null) tone == note else heardNote == note,
            role = Role.Button,
            onClick = { onToggleTone(note) },
        ) { contentColor ->
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (tone == note) FontWeight.Bold else null,
                color = contentColor,
            )
        }
    }
}
