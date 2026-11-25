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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.chords.hasTooManySongChords
import com.pandulapeter.campfire.presentation.ui.components.fadingHorizontalEdges
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.SongStepper

/**
 * What left the song for the screen to hold, under the pages and outside the pager (this package's `CLAUDE.md`): one surface,
 * rounded at its top corners, with no divider or shadow, as tall as [dock] says, the bottom inset included where there
 * is no setlist's pager bar under it to cover that. Its rows are the screen's ([SongDockState.rows]) and their contents
 * the song's that the pager is on or heading for ([targetPage], [SongDockState.contents]), crossfading as a page turns, as the app
 * bar's subtitle does. Read by screen readers after the app bar and before the song.
 */
@Composable
internal fun SongDock(
    modifier: Modifier = Modifier,
    dock: SongDockState,
    contentPadding: PaddingValues,
    targetPage: Int,
) {
    // Kept while the dock leaves, so that it shrinks away as it was rather than as nothing.
    val lastLayout = remember { LastDockLayout() }
    dock.layout?.let { lastLayout.value = it }
    val layout = lastLayout.value ?: return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(dock.height)
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(topStart = DOCK_CORNER, topEnd = DOCK_CORNER))
            .semantics {
                isTraversalGroup = true
                traversalIndex = DOCK_TRAVERSAL_INDEX
            },
    ) {
        val layoutDirection = LocalLayoutDirection.current
        val horizontalPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + DOCK_HORIZONTAL_PADDING,
            end = contentPadding.calculateEndPadding(layoutDirection) + DOCK_HORIZONTAL_PADDING,
        )
        Crossfade(targetState = targetPage) { page ->
            DockRowsOf(
                content = dock.contents[page],
                stepper = dock.steppers[page],
                rows = dock.rows,
                layout = layout,
                horizontalPadding = horizontalPadding,
            )
        }
    }
}

/**
 * The dock's rows for one song's [content], laid out at the dock's full height whatever height it is animating through.
 * In [DockTier.CHORD_CHIPS], one row: what starts the dock ([DockStart]), the click's controls, the chord chips, and the
 * scale chips. Otherwise the diagrams have a row of their own ([DiagramRow]), under a row of the dock's start, the
 * click's controls, and the scale chips where there are scale suggestions, else starting with the dock's start itself
 * and ended by the click's controls.
 * The diagrams follow the page the reader is headed for, which the page's [stepper] says.
 */
@Composable
private fun DockRowsOf(
    content: DockContent?,
    stepper: SongStepper?,
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
    val start = @Composable { if (content != null) DockStart(content = content, height = layout.rowHeight) }
    if (!hasDiagramRow) {
        DockRow(height = layout.rowHeight, horizontalPadding = horizontalPadding) {
            start()
            if (content != null) {
                DockClickControls(content)
                if (rows.hasChordRow) DockChordChips(cells = cells, onChordClicked = onChordClicked)
                DockScaleChips(content)
            }
        }
    } else {
        if (rows.hasScaleRow) {
            DockRow(height = layout.rowHeight, horizontalPadding = horizontalPadding) {
                start()
                if (content != null) {
                    // Here rather than at the diagrams' end while they are the stepper, Tap, and button, which would
                    // leave a phone's diagrams a sliver: the tempo chip that takes their place stands there.
                    DockClickControls(content)
                    DockScaleChips(content)
                }
            }
        }
        val stripScroll = rememberScrollState()
        DiagramRow(
            height = layout.diagramRowHeight,
            horizontalPadding = horizontalPadding,
            scrollState = stripScroll,
            start = start.takeUnless { rows.hasScaleRow },
            end = { if (content != null) DockClickControls(content) },
            hasEnd = !rows.hasScaleRow && content?.shouldShowTempo == true,
        ) {
            if (content != null) {
                val model = content.model
                val names = remember(cells) { cells.mapTo(mutableSetOf()) { it.name } }
                val headedChord by remember(stepper, model, names) {
                    derivedStateOf { stepper?.headedSection?.let { section -> firstChordFrom(model.sections, section, names) } }
                }
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
 * What starts the dock for one song's [content]: the instrument and the tuner where the key chip does not hold them
 * ([DockInstrumentTools]), then the key chip ([DockKeyChip]), [height] tall.
 */
@Composable
private fun DockStart(content: DockContent, height: Dp) {
    val dockKey = remember(content.playingControls, content.model) { dockKeyOf(content.playingControls, content.model) }
    DockInstrumentTools(content = content, keyOpensPlaying = dockKey?.opensPlaying == true)
    if (dockKey != null) {
        DockKeyChip(
            dockKey = dockKey,
            height = height,
            onClick = content.playingControls?.onPlayingClicked?.takeIf { dockKey.opensPlaying },
        )
    }
}

/**
 * The row of the song's diagrams, [height] tall: [start] and [end] stand still at its two ends ([hasEnd] where [end]
 * draws anything), and the [strip] between them scrolls sideways ([scrollState]), fading out where it meets either of
 * them and running to the screen's edge, padded in from it, where nothing stands there.
 */
@Composable
private fun DiagramRow(
    height: Dp,
    horizontalPadding: PaddingValues,
    scrollState: ScrollState,
    start: (@Composable () -> Unit)?,
    end: @Composable () -> Unit,
    hasEnd: Boolean,
    strip: @Composable RowScope.() -> Unit,
) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .height(height),
    verticalAlignment = Alignment.CenterVertically,
) {
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = horizontalPadding.calculateStartPadding(layoutDirection)
    val endPadding = horizontalPadding.calculateEndPadding(layoutDirection)
    if (start != null) {
        Row(
            modifier = Modifier.padding(start = startPadding),
            horizontalArrangement = Arrangement.spacedBy(DOCK_ITEM_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            start()
        }
    }
    Row(
        modifier = Modifier
            .weight(1f)
            .fadingHorizontalEdges(
                scrolledFromStart = if (start == null) null else { { scrollState.value } },
                scrolledFromEnd = if (hasEnd) { { scrollState.maxValue - scrollState.value } } else null,
                backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
            )
            .horizontalScroll(scrollState)
            .padding(start = if (start == null) startPadding else DOCK_ITEM_GAP, end = if (hasEnd) DOCK_ITEM_GAP else endPadding),
        horizontalArrangement = Arrangement.spacedBy(DOCK_ITEM_GAP),
        verticalAlignment = Alignment.CenterVertically,
        content = strip,
    )
    if (hasEnd) {
        Row(
            modifier = Modifier.padding(end = endPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            end()
        }
    }
}

/**
 * One row of the dock, [height] tall, scrolled sideways by hand only. It runs to the screen's edges, its content padded
 * in from them by [horizontalPadding], so it fades nowhere: a fade belongs only where a scrolling row meets something
 * that does not scroll.
 */
@Composable
private fun DockRow(
    height: Dp,
    horizontalPadding: PaddingValues,
    content: @Composable RowScope.() -> Unit,
) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .height(height)
        .horizontalScroll(rememberScrollState())
        .padding(horizontalPadding),
    horizontalArrangement = Arrangement.spacedBy(DOCK_ITEM_GAP),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
)

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

private val DOCK_CORNER = 16.dp
private val DOCK_HORIZONTAL_PADDING = 16.dp
private val DOCK_ITEM_GAP = 8.dp

/** After the app bar's, which is the screen's first, and before the song's pages, which come after it. */
private const val DOCK_TRAVERSAL_INDEX = 1f
