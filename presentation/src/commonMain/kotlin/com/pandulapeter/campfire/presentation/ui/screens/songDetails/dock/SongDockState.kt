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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.platform.LocalLayoutDirection
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
 * The song details screen's dock: its [rows], and either its [layout] as rows under the song or its [panelLayout] as a
 * side panel at the song's end (null for no dock), beside which [layout] is the one row of scale suggestions under the
 * song, the rows under it being [rowsUnder], the [height] the rows take from the bottom of the screen (the bottom
 * inset included where they cover it) and the [panelWidth] the panel takes from its end (the end inset included), each
 * followed as it animates to a new one, which the pages' grid waits for ([isSettled]) as it does for the app bar. Each
 * page composed puts what the dock draws for it in [contents], and its stepper, which says where its reader is headed,
 * in [steppers], by its index.
 */
internal class SongDockState(
    val rows: DockRows,
    val rowsUnder: DockRows,
    val layout: DockLayout?,
    val panelLayout: DockLayout?,
    private val target: Dp,
    animatedHeight: State<Dp>,
    val panelTarget: Dp,
    animatedPanelWidth: State<Dp>,
    val contents: SnapshotStateMap<Int, DockContent>,
    val steppers: SnapshotStateMap<Int, SongStepper>,
) {
    val height by animatedHeight
    val panelWidth by animatedPanelWidth
    val isSidePanel get() = panelLayout != null
    val isSettled get() = height == target && panelWidth == panelTarget
}

/**
 * The dock as the switches in [userPreferences] and the window decide it, never the song: its rows (the playing controls
 * with the chords or the metronome on, the chords with [chordDiagrams] drawn on an instrument with shapes, the scale
 * suggestions), and whether it is a side panel ([isDockSidePanel], which the reading mode weighs too) or rows under the song, which get as much room as the
 * reading mode and a setlist's pager bar ([hasPagerBar]) leave them ([dockBudgetOf]) in a window that is not short and
 * outside an import preview ([isImportPreview]), whose card takes the room of a second row. Rows without the pager bar cover the bottom inset of
 * [contentPadding] too, and the panel its end inset.
 */
@Composable
internal fun rememberSongDock(
    userPreferences: UserPreferences?,
    chordDiagrams: ChordDiagrams?,
    hasPagerBar: Boolean,
    isImportPreview: Boolean,
    contentPadding: PaddingValues,
): SongDockState {
    val isMetronomeShown = userPreferences?.isMetronomeEnabled != false && userPreferences?.isDockMetronomeEnabled != false
    val rows = DockRows(
        hasPlayingRow = userPreferences?.areChordsEnabled != false || isMetronomeShown,
        hasChordRow = chordDiagrams?.tuning != null,
        hasScaleRow = SongSections.of(userPreferences).areScaleSuggestionsShown,
        isMetronomeOnly = userPreferences?.areChordsEnabled == false && isMetronomeShown,
    )
    val turnsByColumn = userPreferences?.songLayout != SongLayout.CONTINUOUS && userPreferences?.pageTurn == PageTurn.COLUMN
    val windowSize = LocalWindowInfo.current.containerDpSize
    val layoutDirection = LocalLayoutDirection.current
    val endInset = contentPadding.calculateEndPadding(layoutDirection)
    val panelWidth = dockPanelWidthOf(tempoChipWidth = dockTempoChipWidth())
    val isSidePanel = isDockSidePanel(
        windowWidth = windowSize.width,
        sideInsets = contentPadding.calculateStartPadding(layoutDirection) + endInset,
        panelWidth = panelWidth,
        isContinuous = userPreferences?.songLayout == SongLayout.CONTINUOUS,
    )
    // The controls are drawn at the interface's text size, which a large system text size grows past a chip's.
    val rowHeight = maxOf(DOCK_ROW_HEIGHT, songControlHeight(MaterialTheme.typography.titleSmall))
    val bottomPadding = dockBottomPaddingOf(bottomInset = contentPadding.calculateBottomPadding())
    // Beside a side panel the rows under the song are the scale suggestions alone, which the panel has no width for.
    val rowsUnder = if (isSidePanel) DockRows(hasPlayingRow = false, hasChordRow = false, hasScaleRow = rows.hasScaleRow) else rows
    val layout = remember(rows, windowSize.height, turnsByColumn, hasPagerBar, isImportPreview, rowHeight, isSidePanel, bottomPadding) {
        if (isSidePanel) {
            dockLayoutOf(rows = rowsUnder, budget = 0.dp, isOneRow = true, rowHeight = rowHeight, bottomPadding = bottomPadding)
        } else {
            dockLayoutOf(
                rows = rows,
                budget = dockBudgetOf(windowHeight = windowSize.height, turnsByColumn = turnsByColumn, hasPagerBar = hasPagerBar),
                isOneRow = windowSize.height < SHORT_WINDOW_HEIGHT || isImportPreview,
                rowHeight = rowHeight,
                bottomPadding = bottomPadding,
            )
        }
    }
    val panelLayout = remember(rows, rowHeight, isSidePanel) { if (isSidePanel) dockPanelLayoutOf(rows, rowHeight) else null }
    // A setlist's pager bar covers the bottom inset under the rows, but beside a side panel it is the panel's last row.
    val target = layout?.let { it.height + if (hasPagerBar && !isSidePanel) 0.dp else contentPadding.calculateBottomPadding() } ?: 0.dp
    val panelTarget = if (panelLayout == null) 0.dp else panelWidth + endInset
    return SongDockState(
        rows = rows,
        rowsUnder = rowsUnder,
        layout = layout,
        panelLayout = panelLayout,
        target = target,
        animatedHeight = animateDpAsState(target),
        panelTarget = panelTarget,
        animatedPanelWidth = animateDpAsState(panelTarget),
        contents = remember { mutableStateMapOf() },
        steppers = remember { mutableStateMapOf() },
    )
}
