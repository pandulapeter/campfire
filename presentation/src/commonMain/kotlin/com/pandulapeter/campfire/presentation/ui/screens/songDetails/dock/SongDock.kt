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

import com.pandulapeter.campfire.presentation.ui.songView.rememberChordCells
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.PAGE_HORIZONTAL_PADDING
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.SongStepper

/**
 * What left the song for the screen to hold, under the pages and outside the pager (this package's `CLAUDE.md`): one surface,
 * rounded at its top corners, with no divider or shadow, its rows as tall as [dock] says, the bottom inset included where
 * there is no setlist's pager bar under them to cover that. Its rows are the screen's ([SongDockState.rows]) and their
 * contents the song's that the pager is on or heading for ([targetPage], [SongDockState.contents]), crossfading as a page
 * turns, as the app bar's subtitle does. An import preview's card floats over its rounded top, on no surface of the
 * dock's. Read by screen readers after the app bar and before the song.
 */
@Composable
internal fun SongDock(
    modifier: Modifier = Modifier,
    dock: SongDockState,
    metronome: DockMetronome,
    contentPadding: PaddingValues,
    targetPage: Int,
) {
    val corner = DOCK_CORNER
    val hold = remember { DockHold() }
    // Beside the side panel the row meets the panel's own surface, the two making one.
    val endCorner by animateDpAsState(if (dock.isSidePanel) 0.dp else DOCK_CORNER)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = corner, topEnd = endCorner))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .dockHold(hold)
            .semantics {
                isTraversalGroup = true
                traversalIndex = DOCK_TRAVERSAL_INDEX
            },
    ) {
        DockRowsBox(dock = dock, metronome = metronome, hold = hold, contentPadding = contentPadding, targetPage = targetPage)
    }
}

/**
 * The dock's rows, as tall as [dock] says, for the page the pager is on or heading for ([targetPage]). They stand on the
 * dock's bottom and its top edge moves over them as its height changes, so that a row it gains or loses (a switch in the
 * Preferences) is uncovered or covered at the top while the rows that stay keep their places, gliding where the change moves
 * them; what a change takes away has faded out before the height changes, and what it brings fades in after
 * ([rememberDockStage]).
 */
@Composable
private fun DockRowsBox(dock: SongDockState, metronome: DockMetronome, hold: DockHold, contentPadding: PaddingValues, targetPage: Int) {
    // Gone once it has shrunk away, so that nothing of it is still read or found under the pages.
    if (!dock.hasRowsUnder && dock.height == 0.dp) return
    Box(modifier = Modifier.fillMaxWidth().height(dock.height).clipToBounds()) {
        val layout = dock.layout ?: return@Box
        val layoutDirection = LocalLayoutDirection.current
        val horizontalPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + PAGE_HORIZONTAL_PADDING,
            end = contentPadding.calculateEndPadding(layoutDirection) + PAGE_HORIZONTAL_PADDING,
        )
        Crossfade(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                .padding(bottom = dock.rowsBottomInset)
                .height(layout.height),
            targetState = targetPage,
        ) { page ->
            DockRowsOf(
                content = dock.contents[page],
                stepper = dock.steppers[page],
                metronome = metronome,
                hold = hold,
                onTunerClicked = dock.onTunerClicked,
                rows = dock.rowsUnder,
                layout = layout,
                parts = dock.parts,
                visible = dock.visibleParts,
                horizontalPadding = horizontalPadding,
                isBesidePanel = dock.isSidePanel,
                areScalesMeasured = dock.areScalesMeasured,
                onScalesWidth = { width -> if (dock.scalesWidths[page] != width) dock.scalesWidths[page] = width },
            )
        }
    }
}

/**
 * The dock's rows for one song's [content], at the dock's full height whatever height it is animating through, each
 * standing on the dock's bottom: with the metronome all it holds, one row that is the metronome panel ([DockMetronomePanel]),
 * after the one-line instrument card of an instrument without shapes; otherwise the chip row ([DockChipRow]) and, with
 * the diagrams outside [DockTier.CHORD_CHIPS], the diagram row under it ([DockDiagramRow]), or the controls' own row
 * ([DockControlsRow]), so that the tempo chip always stands in the dock's bottom end corner (where the setlist's pager
 * bar does not hold it). Of their [parts] only the [visible] ones are drawn, the rest fading out or waiting to fade in.
 * The click is the screen's ([metronome]), whichever page the rows are drawn for. The
 * diagrams fade back the chords not on the screen the page's [stepper] shows, and once it has settled the screen's chords
 * and scales come first and the rows scroll back to them ([rememberDockRowsOrder]), never while a hand is on the dock
 * ([hold]). Beside the side panel ([isBesidePanel]) the one row is the scale chips alone, fading where they scroll under
 * the panel.
 */
@Composable
private fun DockRowsOf(
    content: DockContent?,
    stepper: SongStepper?,
    metronome: DockMetronome,
    hold: DockHold,
    onTunerClicked: (() -> Unit)?,
    rows: DockRows,
    layout: DockLayout,
    parts: Set<DockPart>,
    visible: Set<DockPart>,
    horizontalPadding: PaddingValues,
    isBesidePanel: Boolean,
    areScalesMeasured: Boolean,
    onScalesWidth: (Dp) -> Unit,
) = Box(modifier = Modifier.fillMaxSize()) {
    // The chip row's in either layout, so that the scale chips keep their place as the rows switch between one row and
    // two (the side panel coming or going).
    val rowScroll = rememberScrollState()
    val stripScroll = rememberScrollState()
    val order = rememberDockRowsOrder(
        content = content,
        stepper = stepper,
        cells = content?.let { rememberChordCells(it.model, it.chordDiagrams) }.orEmpty(),
        isHeld = { hold.isHeld || rowScroll.isScrollInProgress || stripScroll.isScrollInProgress },
    )
    val hasDiagramRow = DockPart.DIAGRAM_ROW in parts
    // The one row starts with the chord chips, the screen's first; the scale row alone with the first group on the screen.
    val scaleRowTarget = scaleRowTarget(order, content)
    ScrollWithDockOrder(order = order.groupOrder to order.cells, scrollState = rowScroll, target = if (hasDiagramRow || !rows.hasChordRow) scaleRowTarget else ({ 0 }))
    ScrollWithDockOrder(order = order.cells, scrollState = stripScroll, target = { 0 })
    val tempo = rememberLastOf(content?.tempo)?.takeIf { rows.hasTempo }
    val chordDiagrams = rememberLastOf(content?.chordDiagrams)?.takeIf { rows.hasInstrumentCard }
    val panelPart = parts.firstOrNull { it == DockPart.METRONOME_PANEL || it == DockPart.METRONOME_PANEL_WITH_CARD }
    if (panelPart != null) {
        val alpha by dockPartAlpha(panelPart in visible)
        if (tempo != null) {
            DockMetronomePanel(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = layout.bottomPadding)
                    .graphicsLayer { this.alpha = alpha },
                tempo = tempo,
                metronome = metronome,
                height = layout.rowHeight,
                horizontalPadding = horizontalPadding,
                start = chordDiagrams?.let { @Composable { DockLineCard(chordDiagrams = it, height = layout.rowHeight, onTunerClicked = onTunerClicked) } },
            )
        }
        return@Box
    }
    if (DockPart.CHIP_ROW in parts) {
        DockChipRow(
            modifier = Modifier.align(Alignment.BottomStart),
            content = content,
            order = order,
            tempo = tempo,
            chordDiagrams = chordDiagrams,
            metronome = metronome,
            onTunerClicked = onTunerClicked,
            parts = parts,
            visible = visible,
            layout = layout,
            horizontalPadding = horizontalPadding,
            scrollState = rowScroll,
            isBesidePanel = isBesidePanel,
            areScalesMeasured = areScalesMeasured,
            onScalesWidth = onScalesWidth,
        )
    }
    if (DockPart.CONTROLS_ROW in parts) {
        DockControlsRow(
            modifier = Modifier.align(Alignment.BottomStart),
            tempo = tempo,
            chordDiagrams = chordDiagrams,
            metronome = metronome,
            onTunerClicked = onTunerClicked,
            parts = parts,
            visible = visible,
            layout = layout,
            horizontalPadding = horizontalPadding,
        )
    }
    if (hasDiagramRow) {
        DockDiagramRow(
            modifier = Modifier.align(Alignment.BottomStart),
            content = content,
            order = order,
            tempo = tempo,
            chordDiagrams = chordDiagrams,
            metronome = metronome,
            onTunerClicked = onTunerClicked,
            parts = parts,
            visible = visible,
            layout = layout,
            horizontalPadding = horizontalPadding,
            scrollState = stripScroll,
        )
    }
}

/**
 * Puts what it is applied to after the dock in the order a screen reader takes the screen in: the song's pages, which
 * are drawn above the dock but read after it.
 */
internal fun Modifier.readAfterDock() = semantics {
    isTraversalGroup = true
    traversalIndex = DOCK_TRAVERSAL_INDEX + 1f
}

internal val DOCK_CORNER = 16.dp

/** After the app bar's, which is the screen's first, and before the song's pages, which come after it. */
internal const val DOCK_TRAVERSAL_INDEX = 1f
