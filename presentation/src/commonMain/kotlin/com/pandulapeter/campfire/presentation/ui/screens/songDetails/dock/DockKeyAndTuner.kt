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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams

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
    // The two fill the row, so that their edges line up with the tempo chip's at its end.
    val pillHeight = (height - KEY_AND_TUNER_GAP) / 2
    DockKeyChip(modifier = Modifier.fillMaxWidth(), dockKey = dockKey, height = pillHeight, onClick = onPlayingClicked)
    DockTunerPill(modifier = Modifier.fillMaxWidth(), chordDiagrams = chordDiagrams, height = pillHeight, onClick = onTunerClicked)
}

internal val KEY_AND_TUNER_GAP = 6.dp
