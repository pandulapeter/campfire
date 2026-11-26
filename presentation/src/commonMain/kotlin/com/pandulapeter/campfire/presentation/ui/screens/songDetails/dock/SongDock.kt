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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
    // Beside the side panel the row meets the panel's own surface, the two making one.
    val endCorner by animateDpAsState(if (dock.isSidePanel) 0.dp else DOCK_CORNER)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = corner, topEnd = endCorner))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .semantics {
                isTraversalGroup = true
                traversalIndex = DOCK_TRAVERSAL_INDEX
            },
    ) {
        DockRowsBox(dock = dock, metronome = metronome, contentPadding = contentPadding, targetPage = targetPage)
    }
}

/** The dock's rows, as tall as [dock] says, for the page the pager is on or heading for ([targetPage]). */
@Composable
private fun DockRowsBox(dock: SongDockState, metronome: DockMetronome, contentPadding: PaddingValues, targetPage: Int) {
    // Rows the dock loses (the window widened past the side panel's breakpoint, a switch turned off, the dock leaving)
    // are kept, laid out as they were, until the height has shrunk to the new one, so that they shrink away as they were
    // rather than vanishing at once over an emptied surface. Rows it gains, or the same rows laid out anew, are drawn at
    // once, their full height clipped to the height growing into it.
    val shown = remember { ShownDockRows() }
    dock.layout?.let { layout ->
        val shownLayout = shown.layout
        if (shownLayout == null || shown.rows == dock.rowsUnder || layout.height >= shownLayout.height || dock.isHeightSettled) {
            shown.rows = dock.rowsUnder
            shown.layout = layout
        }
    }
    // Gone once it has shrunk away, so that nothing of it is still read or found under the pages.
    if (dock.layout == null && dock.height == 0.dp) return
    val rows = shown.rows ?: return
    shown.layout?.let { layout ->
        Box(modifier = Modifier.fillMaxWidth().height(dock.height).clipToBounds()) {
            val layoutDirection = LocalLayoutDirection.current
            val horizontalPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + PAGE_HORIZONTAL_PADDING,
                end = contentPadding.calculateEndPadding(layoutDirection) + PAGE_HORIZONTAL_PADDING,
            )
            Crossfade(targetState = targetPage) { page ->
                DockRowsOf(
                    content = dock.contents[page],
                    stepper = dock.steppers[page],
                    metronome = metronome,
                    onTunerClicked = dock.onTunerClicked,
                    rows = rows,
                    layout = layout,
                    horizontalPadding = horizontalPadding,
                    isBesidePanel = dock.isSidePanel,
                )
            }
        }
    }
}

/**
 * The dock's rows for one song's [content], laid out at the dock's full height whatever height it is animating through.
 * With the metronome all it holds, one row that is the metronome panel ([DockMetronomePanel]), after the one-line
 * instrument card of an instrument without shapes. In [DockTier.CHORD_CHIPS] (a short window's and an import preview's),
 * or without the diagrams, one row: the chord chips and the scale chips scrolling up to the chip-high tempo chip
 * ([DockTempoChip]) at its end, after a one-line instrument card ([DockInstrumentPill]) where there are chord chips or
 * an instrument without shapes. Otherwise the diagrams have a row of their own between the tall
 * instrument pill and the tall tempo chip, under a row of the scale chips alone where there are scale suggestions,
 * so that the tempo chip always stands in the dock's bottom end corner. Whatever scrolls fades only where it meets what
 * stands still. The click is the screen's ([metronome]), whichever page the rows are drawn for. The diagrams fade back
 * the chords not on the screen the page's [stepper] shows; the rows move only under the reader's finger. Beside the side
 * panel ([isBesidePanel]) the one row is the scale chips alone, fading where they scroll under the panel.
 */
@Composable
private fun DockRowsOf(
    content: DockContent?,
    stepper: SongStepper?,
    metronome: DockMetronome,
    onTunerClicked: (() -> Unit)?,
    rows: DockRows,
    layout: DockLayout,
    horizontalPadding: PaddingValues,
    isBesidePanel: Boolean,
) = Column(
    modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(align = Alignment.Top, unbounded = true)
        .padding(top = DOCK_VERTICAL_PADDING, bottom = layout.bottomPadding),
    verticalArrangement = Arrangement.spacedBy(DOCK_ROW_GAP),
) {
    // The first row's in either layout, so that the scale chips keep their place as the rows switch between one row and
    // two (the side panel coming or going).
    val rowScroll = rememberScrollState()
    val cells = content?.let { rememberChordCells(it.model, it.chordDiagrams) }.orEmpty()
    val onChordClicked = content?.chordDiagrams?.onChordClicked
    val hasDiagramRow = rows.hasChordRow && layout.tier != DockTier.CHORD_CHIPS
    val tempo = content?.tempo?.takeIf { rows.hasTempo }
    val chordDiagrams = content?.chordDiagrams?.takeIf { rows.hasInstrumentCard }
    val oneLineCard = chordDiagrams?.let {
        @Composable { DockInstrumentPill(chordDiagrams = it, height = layout.rowHeight, isTall = false, isCompact = false, onTunerClicked = onTunerClicked) }
    }
    if (rows.isMetronomeOnly) {
        if (tempo != null) DockMetronomePanel(tempo = tempo, metronome = metronome, height = layout.rowHeight, horizontalPadding = horizontalPadding, start = oneLineCard)
        return@Column
    }
    if (!hasDiagramRow) {
        DockScrollingRow(
            height = layout.rowHeight,
            horizontalPadding = horizontalPadding,
            scrollState = rowScroll,
            start = oneLineCard,
            end = tempo?.let { @Composable { DockTempoChip(tempo = it, metronome = metronome, height = layout.rowHeight) } },
            fadesAtEnd = isBesidePanel,
            // A one-line card of long names would take the whole row from the chips it is there for.
            minScrollingRoom = DOCK_MIN_SCROLLING_ROOM,
        ) {
            if (content != null) {
                if (rows.hasChordRow) DockChordChips(cells = cells, onChordClicked = onChordClicked)
                DockScaleChips(content = content, stepper = stepper)
            }
        }
    } else {
        if (rows.hasScaleRow) {
            DockScrollingRow(height = layout.rowHeight, horizontalPadding = horizontalPadding, scrollState = rowScroll, end = null) {
                if (content != null) DockScaleChips(content = content, stepper = stepper)
            }
        }
        val stripScroll = rememberScrollState()
        DockScrollingRow(
            height = layout.diagramRowHeight,
            horizontalPadding = horizontalPadding,
            scrollState = stripScroll,
            start = chordDiagrams?.let {
                @Composable {
                    DockInstrumentPill(
                        chordDiagrams = it,
                        height = layout.tallTempoChipHeight,
                        isTall = true,
                        isCompact = layout.areControlsCompact,
                        onTunerClicked = onTunerClicked,
                    )
                }
            },
            end = tempo?.let {
                @Composable {
                    DockTempoChip(tempo = it, metronome = metronome, height = layout.tallTempoChipHeight, isTall = true, isCompact = layout.areControlsCompact)
                }
            },
        ) {
            if (content != null) {
                val model = content.model
                val names = remember(cells) { cells.mapTo(mutableSetOf()) { it.name } }
                DockDiagramStrip(
                    cells = cells,
                    isSmall = layout.tier == DockTier.SMALL_DIAGRAMS,
                    rowHeight = layout.diagramRowHeight,
                    markedChords = rememberChordsOnScreen(stepper, model.sections, names),
                    onChordClicked = onChordClicked,
                )
            }
        }
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

/** The dock's rows and their layout as [SongDock] last drew them. Not a state. */
private class ShownDockRows(var rows: DockRows? = null, var layout: DockLayout? = null)

internal val DOCK_CORNER = 16.dp

/** After the app bar's, which is the screen's first, and before the song's pages, which come after it. */
internal const val DOCK_TRAVERSAL_INDEX = 1f
