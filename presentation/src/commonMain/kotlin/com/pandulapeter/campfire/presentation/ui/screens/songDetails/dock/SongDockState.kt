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

import androidx.compose.animation.core.animateDpAsState
import com.pandulapeter.campfire.presentation.ui.songView.songControlHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.PageTurn
import com.pandulapeter.campfire.data.model.preferences.SongLayout
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.preferences.SongSections
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.SongStepper
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams

/**
 * The song details screen's dock: its [rows] and [layout] (null for no dock), and the [height] it takes from the bottom
 * of the screen, the bottom inset included where it covers it, followed as it animates to a new one, which the pages'
 * grid waits for ([isSettled]) as it does for the app bar. Each page composed puts what the dock draws for it in
 * [contents], and its stepper, which says where its reader is headed, in [steppers], by its index.
 */
internal class SongDockState(
    val rows: DockRows,
    val layout: DockLayout?,
    private val target: Dp,
    animatedHeight: State<Dp>,
    val contents: SnapshotStateMap<Int, DockContent>,
    val steppers: SnapshotStateMap<Int, SongStepper>,
) {
    val height by animatedHeight
    val isSettled get() = height == target
}

/**
 * The dock as the switches in [userPreferences] and the window decide it, never the song: its rows (the playing controls
 * with the chords or the metronome on, the chords with [chordDiagrams] drawn on an instrument with shapes, the scale
 * suggestions), and how much room the reading mode and a setlist's pager bar ([hasPagerBar]) leave it
 * ([dockBudgetOf]) in a window that is not short. Without the pager bar it covers the [bottomInset] too.
 */
@Composable
internal fun rememberSongDock(
    userPreferences: UserPreferences?,
    chordDiagrams: ChordDiagrams?,
    hasPagerBar: Boolean,
    bottomInset: Dp,
): SongDockState {
    val rows = DockRows(
        hasPlayingRow = userPreferences?.areChordsEnabled != false || userPreferences.isMetronomeEnabled,
        hasChordRow = chordDiagrams?.tuning != null,
        hasScaleRow = SongSections.of(userPreferences).areScaleSuggestionsShown,
    )
    val turnsByColumn = userPreferences?.songLayout != SongLayout.CONTINUOUS && userPreferences?.pageTurn == PageTurn.COLUMN
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    // The controls are drawn at the interface's text size, which a large system text size grows past a chip's.
    val rowHeight = maxOf(DOCK_ROW_HEIGHT, songControlHeight(MaterialTheme.typography.titleSmall))
    val layout = remember(rows, windowHeight, turnsByColumn, hasPagerBar, rowHeight) {
        dockLayoutOf(
            rows = rows,
            budget = dockBudgetOf(windowHeight = windowHeight, turnsByColumn = turnsByColumn, hasPagerBar = hasPagerBar),
            isShortWindow = windowHeight < SHORT_WINDOW_HEIGHT,
            rowHeight = rowHeight,
        )
    }
    val target = layout?.let { it.height + if (hasPagerBar) 0.dp else bottomInset } ?: 0.dp
    return SongDockState(
        rows = rows,
        layout = layout,
        target = target,
        animatedHeight = animateDpAsState(target),
        contents = remember { mutableStateMapOf() },
        steppers = remember { mutableStateMapOf() },
    )
}
