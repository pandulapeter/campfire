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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.pandulapeter.campfire.presentation.resources.tuner
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentLabel
import com.pandulapeter.campfire.presentation.ui.instrument.isStandard
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams
import com.pandulapeter.campfire.presentation.ui.tuner.tuningLabel
import org.jetbrains.compose.resources.painterResource

/**
 * The start of the diagram row, as tall as the diagrams ([height]): the key chip ([DockKeyChip], opening the Playing
 * sheet) over a pill of the tuner ([DockTunerPill]), two controls of one width each with a container of its own, rather
 * than a key set large in a card as tall as the row, which wasted its room on one letter. The tuner pill names the tuning
 * the strings are tuned to, which the diagrams beside it are drawn for. As wide as the wider of the two, unless
 * [modifier] sets a width (the side panel's).
 */
@Composable
internal fun DockKeyAndTuner(
    modifier: Modifier = Modifier,
    dockKey: DockKey,
    chordDiagrams: ChordDiagrams,
    height: Dp,
    onPlayingClicked: () -> Unit,
    onTunerClicked: () -> Unit,
) = Column(
    modifier = modifier.height(height).width(IntrinsicSize.Max),
    verticalArrangement = Arrangement.spacedBy(KEY_AND_TUNER_GAP, Alignment.CenterVertically),
) {
    val pillHeight = minOf((height - KEY_AND_TUNER_GAP) / 2, MAX_PILL_HEIGHT)
    DockKeyChip(modifier = Modifier.fillMaxWidth(), dockKey = dockKey, height = pillHeight, onClick = onPlayingClicked)
    DockTunerPill(modifier = Modifier.fillMaxWidth(), chordDiagrams = chordDiagrams, height = pillHeight, onClick = onTunerClicked)
}

/** The tuner as a pill naming the tuning of the instrument in the player's hands, or the instrument where it has none. */
@Composable
private fun DockTunerPill(
    modifier: Modifier,
    chordDiagrams: ChordDiagrams,
    height: Dp,
    onClick: () -> Unit,
) = DockControlPill(
    modifier = modifier.height(height),
    onClickLabel = stringResource(Res.string.tuner),
    onClick = onClick,
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

internal val KEY_AND_TUNER_GAP = 6.dp

/** As tall as a pill reads as one in the full-size diagram row, rather than as a card. */
private val MAX_PILL_HEIGHT = 40.dp
private val TUNER_PILL_PADDING = 12.dp
private val TUNER_ICON_GAP = 6.dp
private val TUNER_ICON_SIZE = 18.dp
