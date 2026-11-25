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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.derivedStateOf
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
import com.pandulapeter.campfire.presentation.ui.chords.hasTooManySongChords
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.PAGE_HORIZONTAL_PADDING
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.SongStepper

/**
 * What left the song for the screen to hold, under the pages and outside the pager (this package's `CLAUDE.md`): one surface,
 * rounded at its top corners, with no divider or shadow, its rows as tall as [dock] says, the bottom inset included where
 * there is no [lastRow] under them to cover that. Its rows are the screen's ([SongDockState.rows]) and their contents
 * the song's that the pager is on or heading for ([targetPage], [SongDockState.contents]), crossfading as a page turns, as the app
 * bar's subtitle does. [lastRow] is a setlist's pager bar, on the same surface, shrinking away as it leaves for the side
 * panel ([DockSidePanel]), as the rows do. Where an import preview's card stands on
 * the surface above it ([isUnderCard]) its corners square off, the card's room rounding the surface's top instead. Read
 * by screen readers after the app bar and before the song.
 */
@Composable
internal fun SongDock(
    modifier: Modifier = Modifier,
    dock: SongDockState,
    metronome: DockMetronome,
    contentPadding: PaddingValues,
    targetPage: Int,
    isUnderCard: Boolean,
    lastRow: (@Composable () -> Unit)?,
) {
    val corner by animateDpAsState(if (isUnderCard) 0.dp else DOCK_CORNER)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = corner, topEnd = corner))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .semantics {
                isTraversalGroup = true
                traversalIndex = DOCK_TRAVERSAL_INDEX
            },
    ) {
        DockRowsBox(dock = dock, metronome = metronome, contentPadding = contentPadding, targetPage = targetPage)
        // Kept while it leaves for the side panel, so that it shrinks away as it was rather than as nothing.
        val shownLastRow = remember { LastRow() }
        lastRow?.let { shownLastRow.value = it }
        AnimatedVisibility(
            visible = lastRow != null,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            shownLastRow.value?.invoke()
        }
    }
}

/** The dock's rows, as tall as [dock] says, for the page the pager is on or heading for ([targetPage]). */
@Composable
private fun DockRowsBox(dock: SongDockState, metronome: DockMetronome, contentPadding: PaddingValues, targetPage: Int) {
    // Kept while the dock leaves, so that it shrinks away as it was rather than as nothing.
    val lastLayout = remember { LastDockLayout() }
    dock.layout?.let { lastLayout.value = it }
    // Gone once it has shrunk away, so that nothing of it is still read or found under the pages.
    if (dock.layout == null && dock.height == 0.dp) return
    lastLayout.value?.let { layout ->
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
                    rows = dock.rows,
                    layout = layout,
                    horizontalPadding = horizontalPadding,
                )
            }
        }
    }
}

/**
 * The dock's rows for one song's [content], laid out at the dock's full height whatever height it is animating through.
 * With the metronome all it holds, one row that is the metronome panel ([DockMetronomePanel]). In [DockTier.CHORD_CHIPS]
 * (a short window's and an import preview's), or without the diagrams, one row: what starts the dock ([DockStart]), then the chord chips and the scale chips
 * scrolling between it and the tempo chip ([DockTempoChip]). Otherwise the diagrams have a row of their own, ended by
 * the tempo chip at its height (tall, see [DockTempoChip]), under a row of the dock's start and the scale chips where
 * there are scale suggestions, else starting with the dock's start itself. Whatever scrolls fades only where it meets what stands still. The click is the
 * screen's ([metronome]), whichever page the rows are drawn for.
 * The diagrams and the scale chips follow the page the reader is headed for, which the page's [stepper] says.
 */
@Composable
private fun DockRowsOf(
    content: DockContent?,
    stepper: SongStepper?,
    metronome: DockMetronome,
    rows: DockRows,
    layout: DockLayout,
    horizontalPadding: PaddingValues,
) = Column(
    modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight(align = Alignment.Top, unbounded = true)
        .padding(vertical = DOCK_VERTICAL_PADDING),
    verticalArrangement = Arrangement.spacedBy(DOCK_ROW_GAP),
) {
    val cells = content?.let { rememberChordCells(it.model, it.chordDiagrams) }.orEmpty()
    val onChordClicked = content?.chordDiagrams?.onChordClicked
    val hasDiagramRow = rows.hasChordRow && layout.tier != DockTier.CHORD_CHIPS
    val dockKey = remember(content?.playingControls, content?.model) { content?.let { dockKeyOf(it.playingControls, it.model) } }
    val start = content?.takeIf { dockKey != null || it.hasInstrumentTools(keyOpensPlaying = dockKey?.opensPlaying == true) }?.let {
        @Composable { DockStart(content = it, dockKey = dockKey, height = layout.rowHeight) }
    }
    val tempo = content?.tempo
    if (rows.isMetronomeOnly) {
        if (tempo != null) DockMetronomePanel(tempo = tempo, metronome = metronome, height = layout.rowHeight, start = start, horizontalPadding = horizontalPadding)
        return@Column
    }
    val end = tempo?.let { @Composable { DockTempoChip(tempo = it, metronome = metronome, height = layout.rowHeight) } }
    val headedSection by remember(stepper) { derivedStateOf { stepper?.headedSection } }
    val scaleGroups = content?.model?.scaleGroups.orEmpty()
    val headedGroup = remember(scaleGroups, headedSection) { headedSection?.let { scaleGroupAt(scaleGroups, it) } }
    if (!hasDiagramRow) {
        val rowScroll = rememberScrollState()
        DockScrollingRow(height = layout.rowHeight, horizontalPadding = horizontalPadding, scrollState = rowScroll, start = start, end = end) {
            if (content != null) {
                if (rows.hasChordRow) DockChordChips(cells = cells, onChordClicked = onChordClicked)
                // The chord chips before them would throw the groups' places off, and they are a short window's and an import preview's alone.
                DockScaleChips(content = content, scrollState = rowScroll.takeUnless { rows.hasChordRow }, headedGroup = headedGroup)
            }
        }
    } else {
        if (rows.hasScaleRow) {
            val scaleScroll = rememberScrollState()
            DockScrollingRow(height = layout.rowHeight, horizontalPadding = horizontalPadding, scrollState = scaleScroll, start = start, end = null) {
                if (content != null) DockScaleChips(content = content, scrollState = scaleScroll, headedGroup = headedGroup)
            }
        }
        val stripScroll = rememberScrollState()
        DockScrollingRow(
            height = layout.diagramRowHeight,
            horizontalPadding = horizontalPadding,
            scrollState = stripScroll,
            start = start.takeUnless { rows.hasScaleRow },
            end = tempo?.let { @Composable { DockTempoChip(tempo = it, metronome = metronome, height = layout.diagramRowHeight, isTall = true) } },
        ) {
            if (content != null) {
                val model = content.model
                val names = remember(cells) { cells.mapTo(mutableSetOf()) { it.name } }
                val headedChord = remember(model, names, headedSection) { headedSection?.let { section -> firstChordFrom(model.sections, section, names) } }
                DockDiagramStrip(
                    cells = cells,
                    isSmall = layout.tier == DockTier.SMALL_DIAGRAMS,
                    rowHeight = layout.diagramRowHeight,
                    hasTooManyChords = remember(model) { cells.isEmpty() && model.shouldShowChords && hasTooManySongChords(model.song, model.notation) },
                    scrollState = stripScroll,
                    headedChord = headedChord,
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

/** The dock's layout as [SongDock] last drew it. Not a state. */
private class LastDockLayout(var value: DockLayout? = null)

/** The dock's last row as [SongDock] last drew it. Not a state. */
private class LastRow(var value: (@Composable () -> Unit)? = null)

internal val DOCK_CORNER = 16.dp

/** After the app bar's, which is the screen's first, and before the song's pages, which come after it. */
internal const val DOCK_TRAVERSAL_INDEX = 1f
