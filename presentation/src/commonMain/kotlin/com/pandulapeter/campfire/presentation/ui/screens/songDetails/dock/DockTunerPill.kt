/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_tuner
import com.pandulapeter.campfire.presentation.resources.instrument_tuning_standard
import com.pandulapeter.campfire.presentation.resources.song_details_dock_open_tuner
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentLabel
import com.pandulapeter.campfire.presentation.ui.instrument.isStandard
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams
import com.pandulapeter.campfire.presentation.ui.tuner.tuningLabel
import org.jetbrains.compose.resources.painterResource

/**
 * The tuner as a pill naming the tuning of the instrument in the player's hands, or the instrument where it has none:
 * under the key chip at the start of the diagram row ([DockKeyAndTuner]), and in the side panel's column of controls in
 * read only mode, where it stands for the round tuner button the row under the song has.
 */
@Composable
internal fun DockTunerPill(
    modifier: Modifier = Modifier,
    chordDiagrams: ChordDiagrams,
    height: Dp,
    onClick: () -> Unit,
) = DockControlPill(
    modifier = modifier.height(height),
    onClickLabel = stringResource(Res.string.song_details_dock_open_tuner),
    onClick = {
        println("Dock: tuner pill")
        onClick()
    },
) {
    val tuning = chordDiagrams.instrumentTuning
    val label = when {
        tuning == null -> instrumentLabel(chordDiagrams.instrument)
        tuning.isStandard -> stringResource(Res.string.instrument_tuning_standard)
        else -> tuningLabel(tuning, chordDiagrams.notation)
    }
    // One description for the whole pill ("Tuner, Drop D"): the clickable merges its children, and the mark's alone
    // would hide the tuning from a screen reader.
    val description = listOf(stringResource(Res.string.tuner), label).joinToString(", ")
    Row(
        modifier = Modifier
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = TUNER_PILL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(TUNER_ICON_GAP, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(TUNER_ICON_SIZE),
            painter = painterResource(Res.drawable.ic_tuner),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val TUNER_PILL_PADDING = 12.dp
private val TUNER_ICON_GAP = 6.dp
private val TUNER_ICON_SIZE = 18.dp
