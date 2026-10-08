/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.chords.ChordCell
import com.pandulapeter.campfire.presentation.ui.chords.chordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.emptyChordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.songLayout.chordRowStarts
import com.pandulapeter.campfire.presentation.ui.songLayout.chordSlotRows

/**
 * The cells of a Chords section as its slots measure and draw them: every name laid out once, every cell's width and
 * height, and the rows they wrap into at the last few widths the layout asked about ([MAX_CHORD_ROW_WIDTHS], as
 * `TabRows` keeps them), shared by every slot of the section so that a width is broken into rows once. Everything is
 * measured as the composed cell was: the names in [chordStyle] on one line, the second one 4dp after the first, over a
 * diagram growing with [fontScale], the cells [CELL_GAP] apart.
 *
 * None of this is state: it is filled in by whoever asks first, and the answers never change.
 */
internal class ChordCellLayouts(
    val cells: List<ChordCell>,
    chordStyle: TextStyle,
    textMeasurer: TextMeasurer,
    density: Density,
    fontScale: Float,
) {

    val names by lazy(LazyThreadSafetyMode.NONE) {
        cells.map { textMeasurer.measure(AnnotatedString(it.name), chordStyle, maxLines = 1, softWrap = false) }
    }
    val secondNames by lazy(LazyThreadSafetyMode.NONE) {
        cells.map { cell ->
            (cell.soundingName ?: cell.letterName)?.let { textMeasurer.measure(AnnotatedString(it), chordStyle, maxLines = 1, softWrap = false) }
        }
    }
    val nameGap = with(density) { NAME_GAP.roundToPx() }
    val gap = with(density) { (CELL_GAP * fontScale).roundToPx() }
    val diagramSizes = with(density) {
        cells.map { cell ->
            IntSize(
                width = ((if (cell.instrument.isFretted) FRETTED_WIDTH else KEYBOARD_WIDTH) * fontScale).roundToPx(),
                height = ((if (cell.instrument.isFretted) FRETTED_HEIGHT else KEYBOARD_HEIGHT) * fontScale).roundToPx(),
            )
        }
    }

    /** How wide the names of each cell are together, the second one after its gap where there is one. */
    val nameRowWidths by lazy(LazyThreadSafetyMode.NONE) {
        IntArray(cells.size) { cell -> names[cell].size.width + (secondNames[cell]?.let { nameGap + it.size.width } ?: 0) }
    }

    /** How tall the names of each cell are, the taller of the two, which share a style. */
    val nameRowHeights by lazy(LazyThreadSafetyMode.NONE) {
        IntArray(cells.size) { cell -> maxOf(names[cell].size.height, secondNames[cell]?.size?.height ?: 0) }
    }
    val cellWidths by lazy(LazyThreadSafetyMode.NONE) { IntArray(cells.size) { cell -> maxOf(diagramSizes[cell].width, nameRowWidths[cell]) } }
    val cellHeights by lazy(LazyThreadSafetyMode.NONE) { IntArray(cells.size) { cell -> nameRowHeights[cell] + diagramSizes[cell].height } }

    /** Every cell on one line, which is what a slot answers for the widest it would be. */
    val lineWidth by lazy(LazyThreadSafetyMode.NONE) { cellWidths.sum() + gap * (cells.size - 1).coerceAtLeast(0) }
    val widestCell by lazy(LazyThreadSafetyMode.NONE) { cellWidths.maxOrNull() ?: 0 }
    val geometries by lazy(LazyThreadSafetyMode.NONE) {
        cells.map { cell ->
            cell.selection.shape?.let { chordDiagramGeometryOf(it, cell.instrument, cell.root) } ?: emptyChordDiagramGeometryOf(cell.instrument)
        }
    }
    private val rowStartsByWidth = mutableMapOf<Int, IntArray>()
    private val recentWidths = ArrayDeque<Int>()

    /** Where each row starts at [width] (see [chordRowStarts]); an unbounded width is one row. */
    fun rowStartsAt(width: Int): IntArray {
        recentWidths.remove(width)
        recentWidths.addLast(width)
        if (recentWidths.size > MAX_CHORD_ROW_WIDTHS) rowStartsByWidth.remove(recentWidths.removeFirst())
        return rowStartsByWidth.getOrPut(width) { chordRowStarts(cellWidths, gap, width) }
    }

    /** The cells of row [row] when the rows start at [starts]. */
    fun cellsOfRow(starts: IntArray, row: Int) = starts[row] until (starts.getOrNull(row + 1) ?: cells.size)

    /** How tall row [row] is: its tallest cell, since the editor preview's definitions may mix a fretted and a keyboard one. */
    fun rowHeight(starts: IntArray, row: Int) = cellsOfRow(starts, row).maxOf { cellHeights[it] }

    /**
     * How tall the slots from [firstSlot] to [lastSlot] are at [width]: their rows [gap] apart, and one more [gap] after
     * the last of them where a row follows, so that the slots stacked uncut are exactly as tall as the rows were in one
     * piece, and a cut leaves the gap at the bottom of a page rather than at the top of the next.
     */
    fun height(width: Int, firstSlot: Int, lastSlot: Int, isLastSlot: Boolean): Int {
        val starts = rowStartsAt(width)
        val rows = chordSlotRows(starts.size, firstSlot, lastSlot, isLastSlot)
        if (rows.isEmpty()) return 0
        return rows.sumOf { rowHeight(starts, it) } + gap * (rows.last - rows.first) + if (rows.last < starts.size - 1) gap else 0
    }

    /** The cells the slots from [firstSlot] to [lastSlot] show at [width], which a screen reader is told about. */
    fun cellsAt(width: Int, firstSlot: Int, lastSlot: Int, isLastSlot: Boolean): IntRange {
        val starts = rowStartsAt(width)
        val rows = chordSlotRows(starts.size, firstSlot, lastSlot, isLastSlot)
        return if (rows.isEmpty()) IntRange.EMPTY else starts[rows.first] until (starts.getOrNull(rows.last + 1) ?: cells.size)
    }
}

private val CELL_GAP = 6.dp
private val NAME_GAP = 4.dp

private val FRETTED_WIDTH = 56.dp
private val FRETTED_HEIGHT = 70.dp
private val KEYBOARD_WIDTH = 76.dp
private val KEYBOARD_HEIGHT = 40.dp

/** How many widths a section's rows are kept for, see [ChordCellLayouts.rowStartsAt]. */
private const val MAX_CHORD_ROW_WIDTHS = 8
