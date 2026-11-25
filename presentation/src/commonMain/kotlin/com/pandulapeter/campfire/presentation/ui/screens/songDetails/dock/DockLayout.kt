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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How the song's chords are shown in the dock, from the most room to the least: diagrams at the size the song's Chords
 * section draws them, smaller diagrams, or a chip per chord naming it, in the row the playing controls are in.
 */
internal enum class DockTier { FULL_DIAGRAMS, SMALL_DIAGRAMS, CHORD_CHIPS }

/**
 * The rows the dock has, decided by the screen's switches and never by the song, so that its height stays the same as a
 * setlist's pages turn: the playing controls ([hasPlayingRow]: the chords or the metronome on), the song's chords
 * ([hasChordRow]: chord diagrams on, on an instrument with shapes), and its scale suggestions ([hasScaleRow]).
 */
@Immutable
internal data class DockRows(
    val hasPlayingRow: Boolean,
    val hasChordRow: Boolean,
    val hasScaleRow: Boolean,
) {
    val isEmpty get() = !hasPlayingRow && !hasChordRow && !hasScaleRow
}

/**
 * How the dock is laid out: its [tier], how tall it is above whatever inset it covers, how tall its row of chips and
 * controls is ([rowHeight]), and its row of diagrams ([diagramRowHeight], none in [DockTier.CHORD_CHIPS]).
 */
@Immutable
internal data class DockLayout(
    val tier: DockTier,
    val height: Dp,
    val rowHeight: Dp,
    val diagramRowHeight: Dp = 0.dp,
)

/**
 * How much of a window [windowHeight] tall the dock may take: the room the song's first sections took from its first page
 * spent instead on every page, which costs no page turn up to a share of the window measured on the smallest phone
 * (`PageCountTest`, 360 × 640 dp: 80 dp turned by page, 60 dp turned by column, 48 dp and 40 dp over a setlist's pager
 * bar, whose row leaves the song less room to spare). A taller window takes the same share, its pages being as much
 * taller.
 */
internal fun dockBudgetOf(windowHeight: Dp, turnsByColumn: Boolean, hasPagerBar: Boolean): Dp = windowHeight * when {
    turnsByColumn && hasPagerBar -> 40f
    turnsByColumn -> 60f
    hasPagerBar -> 48f
    else -> 80f
} / MEASURED_WINDOW_HEIGHT

/**
 * The dock for [rows] within [budget], its row of chips and controls [rowHeight] tall (taller than a chip at a large
 * system text size): the richest tier whose height fits, else the one-row tier however little room there is, since what
 * it holds has nowhere else to be. Null where there is no row at all, which is no dock.
 */
internal fun dockLayoutOf(rows: DockRows, budget: Dp, rowHeight: Dp = DOCK_ROW_HEIGHT): DockLayout? {
    if (rows.isEmpty) return null
    val oneRow = DockLayout(DockTier.CHORD_CHIPS, height = DOCK_VERTICAL_PADDING * 2 + rowHeight, rowHeight = rowHeight)
    // The playing controls and the scale chips share the first row, so only the chords can ask for a second.
    if (!rows.hasChordRow) return oneRow
    val firstRow = if (rows.hasPlayingRow || rows.hasScaleRow) rowHeight + DOCK_ROW_GAP else 0.dp
    return listOf(DockTier.FULL_DIAGRAMS to FULL_DIAGRAMS_PAST_ROW, DockTier.SMALL_DIAGRAMS to SMALL_DIAGRAMS_PAST_ROW).map { (tier, pastRow) ->
        // A row of diagrams is a line of names, as tall as the row's text, over the diagrams.
        val diagramRowHeight = rowHeight + pastRow
        DockLayout(tier, height = DOCK_VERTICAL_PADDING * 2 + firstRow + diagramRowHeight, rowHeight = rowHeight, diagramRowHeight = diagramRowHeight)
    }.firstOrNull { it.height <= budget } ?: oneRow
}

/** A row of chips or playing controls at the interface's own text size: as tall as a chip, and as the controls. */
internal val DOCK_ROW_HEIGHT = 32.dp
internal val DOCK_ROW_GAP = 8.dp

/** Only as much as keeps a chip off the dock's edge: one row of them is all a phone turned by column has room for. */
internal val DOCK_VERTICAL_PADDING = 4.dp

/**
 * How much taller than a row of chips a row of diagrams at their full size is: a chord's name over a diagram as large as
 * the Chords section draws one (70 dp), less the padding of a chip around its name.
 */
private val FULL_DIAGRAMS_PAST_ROW = 60.dp

/** The same of a row of smaller diagrams (50 dp). */
private val SMALL_DIAGRAMS_PAST_ROW = 40.dp

/** The height of the window the budgets were measured in, the smallest phone's. */
private const val MEASURED_WINDOW_HEIGHT = 640f
