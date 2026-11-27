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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The dock's instrument card at the start of the setlist's pager bar, where nothing would stand beside it in the dock
 * ([DockControlsPlace.PAGER_BAR]): one line, as in the one row, its tuner at the bar's edge, fading and growing in and
 * out as the dock stages the change ([DockPart.PAGER_BAR_CARD]). For the page the pager is on or heading for
 * ([targetPage]).
 */
@Composable
internal fun DockPagerBarCard(dock: SongDockState, targetPage: Int) {
    val chordDiagrams = rememberLastOf(dock.contents[targetPage]?.chordDiagrams)
    val layout = dock.layout
    if (DockPart.PAGER_BAR_CARD !in dock.parts || chordDiagrams == null || layout == null) return
    DockRowEnd(isShown = DockPart.PAGER_BAR_CARD in dock.visibleParts, expandFrom = Alignment.Start) {
        Box(modifier = Modifier.padding(start = PAGER_BAR_END_PADDING)) {
            DockInstrumentPill(chordDiagrams = chordDiagrams, height = layout.rowHeight, isTall = false, isCompact = false, onTunerClicked = dock.onTunerClicked)
        }
    }
}

/**
 * The dock's tempo chip at the end of the setlist's pager bar, around whose controls it and the instrument card fit
 * ([DockControlsPlace.PAGER_BAR]): chip-high, as in the one row, fading and growing in and out as the dock stages the
 * change ([DockPart.PAGER_BAR_TEMPO]). For the page the pager is on or heading for ([targetPage]); the click is the
 * screen's ([metronome]).
 */
@Composable
internal fun DockPagerBarTempo(dock: SongDockState, metronome: DockMetronome, targetPage: Int) {
    val tempo = rememberLastOf(dock.contents[targetPage]?.tempo)
    val layout = dock.layout
    if (DockPart.PAGER_BAR_TEMPO !in dock.parts || tempo == null || layout == null) return
    DockRowEnd(isShown = DockPart.PAGER_BAR_TEMPO in dock.visibleParts, expandFrom = Alignment.End) {
        Box(modifier = Modifier.padding(end = PAGER_BAR_END_PADDING)) {
            DockTempoChip(tempo = tempo, metronome = metronome, height = layout.rowHeight)
        }
    }
}

/** Past the pager bar's own padding, so that its controls are as far from the screen's edges as the dock's rows'. */
private val PAGER_BAR_END_PADDING = 4.dp
