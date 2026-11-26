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
import com.pandulapeter.campfire.presentation.ui.components.WindowSize
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.PAGE_HORIZONTAL_PADDING
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.STEP_BUTTON_GAP
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.STEP_BUTTON_SIZE
import com.pandulapeter.campfire.presentation.ui.songView.MIN_COLUMN_WIDTH

/**
 * How the song's chords are shown in the dock, from the most room to the least: diagrams at the size the song's Chords
 * section draws them, smaller diagrams, or a chip per chord naming it, in the one row the scale chips and the tempo chip
 * share (a short window's, and an import preview's, whose card takes the room of a second row).
 */
internal enum class DockTier { FULL_DIAGRAMS, SMALL_DIAGRAMS, CHORD_CHIPS }

/**
 * The rows the dock has: the song's chords ([hasChordRow]: chord diagrams on, on an instrument with shapes), its scale
 * suggestions ([hasScaleRow]), the click's tempo chip ([hasTempo]: the metronome on, never in an import preview), and
 * on an instrument without shapes, which has no diagrams, the one-line instrument card alone ([hasInstrumentChip]: the
 * diagrams on), the way back to an instrument with them, starting the one row. The screen's switches decide them first,
 * then the song ([forSong]). The key chip is the app bar's, so nothing else is the dock's.
 */
@Immutable
internal data class DockRows(
    val hasChordRow: Boolean,
    val hasScaleRow: Boolean,
    val hasTempo: Boolean,
    val hasInstrumentChip: Boolean = false,
) {
    /** Whether the dock shows its instrument card, which holds the tuner where the Dock's Tuner switch puts it there. */
    val hasInstrumentCard get() = hasChordRow || hasInstrumentChip

    /**
     * Where the click is all the dock holds (neither diagrams nor scale suggestions): its one row is then the metronome
     * panel, which has the room for the stepper and the accents a chip has not.
     */
    val isMetronomeOnly get() = hasTempo && !hasChordRow && !hasScaleRow

    /** No row at all: no dock. */
    val isEmpty get() = !hasChordRow && !hasScaleRow && !hasTempo && !hasInstrumentChip

    /**
     * These rows for a song with or without a diagram to draw ([hasChords]) and a scale to suggest ([hasScales]): a row
     * the song has nothing for goes as its switch turned off would take it, rather than saying the song has none, so a
     * song without scales falls back to the diagrams alone or the scale strip to the metronome panel, and one with
     * nothing for any row has no dock. Decided per song, never per screen of it, so stepping through a song never
     * changes the dock. The instrument card stays with a song without chords, one line as on an instrument without
     * shapes: the instrument and its tuning are the player's, not the song's, and the card is where the tuner is.
     */
    fun forSong(hasChords: Boolean, hasScales: Boolean) = copy(
        hasChordRow = hasChordRow && hasChords,
        hasScaleRow = hasScaleRow && hasScales,
        hasInstrumentChip = hasInstrumentChip || (hasChordRow && !hasChords),
    )
}

/**
 * How the dock is laid out: its [tier], how tall it is above whatever inset it covers (nothing for the side panel, which
 * is as tall as the song), how tall its row of chips and controls is ([rowHeight]), its row of diagrams
 * ([diagramRowHeight], none in [DockTier.CHORD_CHIPS]), the room under its last row ([bottomPadding],
 * [dockBottomPaddingOf]), and whether its tall controls are made smaller ([areControlsCompact]: in a window narrower than
 * [DOCK_COMPACT_WINDOW_WIDTH], an upright phone's, where the instrument card and the tempo chip at full size took most of
 * the diagrams' row).
 */
@Immutable
internal data class DockLayout(
    val tier: DockTier,
    val height: Dp,
    val rowHeight: Dp,
    val diagramRowHeight: Dp = 0.dp,
    val bottomPadding: Dp = DOCK_VERTICAL_PADDING,
    val areControlsCompact: Boolean = false,
) {
    /**
     * How tall the tall tempo chip is, beside the diagram row and at the side panel's foot alike: as tall as a row of the
     * smaller diagrams, the least either place has, so that it is one size wherever it stands (centred in a row of
     * full-size diagrams).
     */
    val tallTempoChipHeight get() = rowHeight + SMALL_DIAGRAMS_PAST_ROW
}

/**
 * How much of a window [windowHeight] tall the dock may take: the room the song's first sections took from its first page
 * spent instead on every page, which costs no page turn up to a share of the window measured on the smallest phone
 * (`PageCountTest`, 360 × 640 dp: 80 dp turned by page, 60 dp turned by column, 48 dp and 40 dp over a setlist's pager
 * bar, whose row leaves the song less room to spare). A taller window takes the same share, its pages being as much
 * taller. Only full-size diagrams are held to it: a window that is not short draws smaller ones past it ([dockLayoutOf]).
 */
internal fun dockBudgetOf(windowHeight: Dp, turnsByColumn: Boolean, hasPagerBar: Boolean): Dp = windowHeight * when {
    turnsByColumn && hasPagerBar -> 40f
    turnsByColumn -> 60f
    hasPagerBar -> 48f
    else -> 80f
} / MEASURED_WINDOW_HEIGHT

/**
 * The dock's rows under the song for [rows] within [budget], its row of chips and controls [rowHeight] tall (taller than
 * a chip at a large system text size). [isOneRow] keeps it to one row, its chords as chips: a short window (a phone on
 * its side), whose every line is a page's worth of room, and an import preview, whose card takes the dock's room. Any
 * other gets diagrams: at their full size where they fit the budget, else smaller ones however
 * little room there is, a long song taking up to two pages more for them on an upright phone. Null where there is no row
 * at all: no dock. A window that gives the dock a side panel ([isDockSidePanel]) has none of this.
 */
internal fun dockLayoutOf(
    rows: DockRows,
    budget: Dp,
    isOneRow: Boolean,
    rowHeight: Dp = DOCK_ROW_HEIGHT,
    bottomPadding: Dp = DOCK_VERTICAL_PADDING,
): DockLayout? {
    if (rows.isEmpty) return null
    val padding = DOCK_VERTICAL_PADDING + bottomPadding
    val oneRow = DockLayout(DockTier.CHORD_CHIPS, height = padding + rowHeight, rowHeight = rowHeight, bottomPadding = bottomPadding)
    // The scale chips and the tempo chip share the one row, so only the diagrams can ask for a second.
    if (!rows.hasChordRow || isOneRow) return oneRow
    // Without scale suggestions, the diagram row is the only one, the tempo chip at its end.
    val firstRow = if (rows.hasScaleRow) rowHeight + DOCK_ROW_GAP else 0.dp
    val (full, small) = listOf(DockTier.FULL_DIAGRAMS to FULL_DIAGRAMS_PAST_ROW, DockTier.SMALL_DIAGRAMS to SMALL_DIAGRAMS_PAST_ROW).map { (tier, pastRow) ->
        // A row of diagrams is a line of names, as tall as the row's text, over the diagrams.
        val diagramRowHeight = rowHeight + pastRow
        DockLayout(tier, height = padding + firstRow + diagramRowHeight, rowHeight = rowHeight, diagramRowHeight = diagramRowHeight, bottomPadding = bottomPadding)
    }
    return if (full.height <= budget) full else small
}

/**
 * Whether the dock is a side panel at the end of the song rather than rows under it, never decided by the song.
 *
 * Only where its [rows] have the diagrams ([DockRows.hasChordRow]), which are what fill a panel as tall as the song:
 * without them it would hold only the tempo chip, and with the metronome alone it would lose the stepper and the
 * accents of the metronome panel under the song, so those keep the one row under the song.
 *
 * Laid out in pages, only in a wide window ([WindowSize.EXPANDED], 840 dp and wider, where the editor's split panes
 * start), whatever its height, whose song has width to spare beside the panel and keeps all of its height. A narrower
 * window keeps the rows, a short one (a phone on its side) their one row: the panel would take a width its columns need,
 * and a long-lined song wrapped to it took five pages more on a 640 × 360 phone than under the one row.
 *
 * In one column scrolling by itself ([isContinuous]) the song is read by its height and is never wider than a column, so
 * the panel wins wherever the window, its [sideInsets] left out, still holds the narrowest ordinary column beside a panel
 * [panelWidth] wide, between the step buttons: a phone on its side included, whose rows would take a seventh of its
 * height.
 */
internal fun isDockSidePanel(rows: DockRows, windowWidth: Dp, sideInsets: Dp, panelWidth: Dp, isContinuous: Boolean) = rows.hasChordRow && if (isContinuous) {
    windowWidth - sideInsets - panelWidth >= CONTINUOUS_COLUMN_ROOM
} else {
    WindowSize.fromWidth(windowWidth) == WindowSize.EXPANDED
}

/**
 * The dock as a side panel for [rows], its chips [rowHeight] tall and its diagrams at the Chords section's size, which a
 * panel as tall as the window has room for, as is the tempo chip at its foot; null where there is no row at all, which is
 * no dock.
 */
internal fun dockPanelLayoutOf(rows: DockRows, rowHeight: Dp = DOCK_ROW_HEIGHT): DockLayout? = if (rows.isEmpty) {
    null
} else {
    DockLayout(DockTier.FULL_DIAGRAMS, height = 0.dp, rowHeight = rowHeight, diagramRowHeight = rowHeight + FULL_DIAGRAMS_PAST_ROW)
}

/**
 * How wide the side panel's content is, its padding left out: the tall tempo chip at its foot ([tempoChipWidth],
 * `dockTallTempoChipWidth`) or two diagrams of a fretted instrument at the Chords section's size side by side with room
 * to spare, so that they never wrap onto lines of one for a pixel's rounding, whichever is wider. The tempo chip is
 * stretched to it. No wider than that: a 1024 × 720 window's songs take a page more past 176 dp (`PageCountTest`).
 */
internal fun dockPanelContentWidthOf(tempoChipWidth: Dp) = maxOf(tempoChipWidth, DOCK_DIAGRAM_CELL_WIDTH * 2 + DOCK_ITEM_GAP + DIAGRAMS_SPARE_ROOM)

/** How wide the side panel is, the end inset it covers left out: its content ([dockPanelContentWidthOf]) within its padding. */
internal fun dockPanelWidthOf(tempoChipWidth: Dp) = dockPanelContentWidthOf(tempoChipWidth) + DOCK_PANEL_PADDING * 2

/** How far the side panel's rows are from its sides. */
internal val DOCK_PANEL_PADDING = 8.dp

private val DIAGRAMS_SPARE_ROOM = 8.dp
/** A row of chips at the interface's own text size: as tall as a chip, and as the chip-high tempo chip. */
internal val DOCK_ROW_HEIGHT = 32.dp
internal val DOCK_ROW_GAP = 8.dp

/**
 * Above the dock's first row, and under its last at the window's bottom edge: as far in as its rows are from the
 * window's sides (`PAGE_HORIZONTAL_PADDING`), so the rounded surface frames them evenly.
 */
internal val DOCK_VERTICAL_PADDING = 8.dp

/**
 * The room under the dock's last row: [DOCK_VERTICAL_PADDING] where the window's bottom inset ([bottomInset]) is
 * nothing, so the rows are framed evenly at the window's edge, and half of it over an inset (a navigation bar), whose own
 * room already parts the rows from the edge, so that a phone's rows cost the song as little as they can.
 */
internal fun dockBottomPaddingOf(bottomInset: Dp) = if (bottomInset > 0.dp) DOCK_VERTICAL_PADDING / 2 else DOCK_VERTICAL_PADDING

/**
 * How much taller than a row of chips a row of diagrams at their full size is: a chord's name over a diagram as large as
 * the Chords section draws one (70 dp), less the padding of a chip around its name.
 */
private val FULL_DIAGRAMS_PAST_ROW = 60.dp

/** The same of a row of smaller diagrams (42 dp), as large as keeps a long song within two pages of its count without the dock. */
private val SMALL_DIAGRAMS_PAST_ROW = 32.dp

/**
 * The width a song in one column needs beside the side panel: the narrowest column the grid sets songs in, between the
 * step buttons, and the room past them at the window's edges, all of which the continuous layout keeps.
 */
private val CONTINUOUS_COLUMN_ROOM = MIN_COLUMN_WIDTH + (STEP_BUTTON_SIZE + STEP_BUTTON_GAP + PAGE_HORIZONTAL_PADDING) * 2

/** The height of the window the budgets were measured in, the smallest phone's. */
private const val MEASURED_WINDOW_HEIGHT = 640f
