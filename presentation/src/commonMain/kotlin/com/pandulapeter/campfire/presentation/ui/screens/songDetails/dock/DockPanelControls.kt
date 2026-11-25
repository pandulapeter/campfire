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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The top of the side panel for one song's [content], which stays put while what is under it scrolls
 * ([DockPanelRows]): what starts the dock ([DockStart]) over the tempo chip ([DockTempoChip]), centred. The key chip
 * stands over the tuner's pill naming the tuning, as at the start of the rows' diagram row ([DockKeyAndTuner]), each a
 * row tall and stretched to the panel's content width ([dockPanelContentWidthOf]) with the tempo chip, so the three read
 * as one column of controls.
 */
@Composable
internal fun DockPanelControls(
    modifier: Modifier = Modifier,
    content: DockContent?,
    metronome: DockMetronome,
    layout: DockLayout,
) = Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(KEY_AND_TUNER_GAP),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    if (content == null) return@Column
    val pillWidth = dockPanelContentWidthOf(tempoChipWidth = dockTempoChipWidth())
    val dockKey = remember(content.playingControls, content.model) { dockKeyOf(content.playingControls, content.model) }
    if (dockKey != null || content.hasInstrumentTools(keyOpensPlaying = dockKey?.opensPlaying == true, keyHoldsTuner = false)) {
        DockStart(
            modifier = Modifier.fillMaxWidth(),
            content = content,
            dockKey = dockKey,
            height = layout.rowHeight,
            tallHeight = layout.rowHeight * 2 + KEY_AND_TUNER_GAP,
            pillWidth = pillWidth,
        )
    }
    content.tempo?.let { tempo ->
        DockTempoChip(modifier = Modifier.width(pillWidth), tempo = tempo, metronome = metronome, height = layout.rowHeight, isStretched = true)
    }
}
