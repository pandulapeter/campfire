/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.withTexts
import com.pandulapeter.campfire.presentation.ui.chords.ChordCell
import com.pandulapeter.campfire.presentation.ui.chords.chordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.emptyChordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.songLayout.chordCellAt
import com.pandulapeter.campfire.presentation.ui.songLayout.chordRowStarts
import com.pandulapeter.campfire.presentation.ui.songLayout.slotRows

/**
 * The cells of a Chords section as its slots measure and draw them: every name laid out once, every cell's width and
 * height, and the rows they wrap into at the last few widths the layout asked about ([MAX_CHORD_ROW_WIDTHS], as
 * `TabRows` keeps them), shared by every slot of the section so that a width is broken into rows once. Everything is
 * measured as the composed cell was: the name in [chordStyle] under its second name, set smaller in the normal weight
 * on a line of its own, over a diagram growing with [fontScale], the cells [CELL_GAP] apart. Where the chords are in
 * more than one key, each key's chords (`ChordCell.keyName`) start a row of their own under a heading naming it,
 * [keyHeading] (a template taking the key as its `%1$s`) in the second names' weight, so that it reads as a label
 * rather than as one more chord.
 *
 * None of this is state: it is filled in by whoever asks first, and the answers never change.
 */
internal class ChordCellLayouts(
    val cells: List<ChordCell>,
    chordStyle: TextStyle,
    keyHeading: String,
    textMeasurer: TextMeasurer,
    density: Density,
    fontScale: Float,
) {

    val names by lazy(LazyThreadSafetyMode.NONE) {
        cells.map { textMeasurer.measure(AnnotatedString(it.name), chordStyle, maxLines = 1, softWrap = false) }
    }
    val secondNames by lazy(LazyThreadSafetyMode.NONE) {
        // The second name, letters after a step or a degree after letters, only glosses the name, so it is set smaller, in
        // the normal weight, whichever of the two it is.
        val secondNameStyle = chordStyle.copy(fontSize = chordStyle.fontSize * SECOND_NAME_SIZE, fontWeight = FontWeight.Normal)
        cells.map { cell ->
            (cell.letterName ?: cell.degree)?.let { textMeasurer.measure(AnnotatedString(it), secondNameStyle, maxLines = 1, softWrap = false) }
        }
    }
    /** Where each key's chords start, the home key's (or the only key's) at 0, see [chordRowStarts]. */
    private val groupStarts = cells.indices.filter { it == 0 || cells[it].keyName != cells[it - 1].keyName }.toIntArray()

    /** The heading over each cell starting a key's chords, null over every other cell and where there is one key. */
    val headings by lazy(LazyThreadSafetyMode.NONE) {
        // A sentence of the interface's around the key's name, so it reads in its own text's direction, not the chords'.
        val headingStyle = chordStyle.copy(fontWeight = FontWeight.Normal, textDirection = TextDirection.Content)
        cells.mapIndexed { index, cell ->
            cell.keyName?.takeIf { index in groupStarts }?.let { key ->
                textMeasurer.measure(AnnotatedString(keyHeading.withTexts(key)), headingStyle, maxLines = 1, softWrap = false)
            }
        }
    }
    private val secondLineGap = with(density) { (SECOND_LINE_GAP * fontScale).roundToPx() }
    val gap = with(density) { (CELL_GAP * fontScale).roundToPx() }
    val diagramSizes = with(density) {
        cells.map { cell ->
            IntSize(
                width = ((if (cell.tuning.instrument.isFretted) FRETTED_WIDTH else KEYBOARD_WIDTH) * fontScale).roundToPx(),
                height = ((if (cell.tuning.instrument.isFretted) FRETTED_HEIGHT else KEYBOARD_HEIGHT) * fontScale).roundToPx(),
            )
        }
    }

    /**
     * How tall the line over the names is, with its gap: the tallest second name's, kept by every cell of a section where
     * any has one, so that the names of a row stand on one line whichever chords the key counts.
     */
    val secondLineHeight by lazy(LazyThreadSafetyMode.NONE) {
        secondNames.maxOfOrNull { it?.size?.height ?: 0 }?.takeIf { it > 0 }?.let { it + secondLineGap } ?: 0
    }

    /** How wide the names of each cell are: the wider of the name and the second name over it. */
    val nameRowWidths by lazy(LazyThreadSafetyMode.NONE) {
        IntArray(cells.size) { cell -> maxOf(names[cell].size.width, secondNames[cell]?.size?.width ?: 0) }
    }

    /** How tall the names of each cell are: the name under the section's line of second names. */
    val nameRowHeights by lazy(LazyThreadSafetyMode.NONE) { IntArray(cells.size) { cell -> secondLineHeight + names[cell].size.height } }
    val cellWidths by lazy(LazyThreadSafetyMode.NONE) { IntArray(cells.size) { cell -> maxOf(diagramSizes[cell].width, nameRowWidths[cell]) } }
    val cellHeights by lazy(LazyThreadSafetyMode.NONE) { IntArray(cells.size) { cell -> nameRowHeights[cell] + diagramSizes[cell].height } }

    /** Every key's cells on one line, which is what a slot answers for the widest it would be. */
    val lineWidth by lazy(LazyThreadSafetyMode.NONE) {
        groupStarts.indices.maxOfOrNull { group ->
            val cellsOfGroup = groupStarts[group] until (groupStarts.getOrNull(group + 1) ?: cells.size)
            maxOf(cellsOfGroup.sumOf { cellWidths[it] } + gap * (cellsOfGroup.count() - 1), headings[groupStarts[group]]?.size?.width ?: 0)
        } ?: 0
    }
    val widestCell by lazy(LazyThreadSafetyMode.NONE) { maxOf(cellWidths.maxOrNull() ?: 0, headings.maxOf { it?.size?.width ?: 0 }) }
    val geometries by lazy(LazyThreadSafetyMode.NONE) {
        cells.map { cell ->
            cell.selection.shape?.let { chordDiagramGeometryOf(it, cell.tuning, cell.root, cell.capo) } ?: emptyChordDiagramGeometryOf(cell.tuning.instrument)
        }
    }
    private val rowStartsByWidth = mutableMapOf<Int, IntArray>()
    private val recentWidths = ArrayDeque<Int>()

    /** Where each row starts at [width] (see [chordRowStarts]); an unbounded width is one row. */
    fun rowStartsAt(width: Int): IntArray {
        recentWidths.remove(width)
        recentWidths.addLast(width)
        if (recentWidths.size > MAX_CHORD_ROW_WIDTHS) rowStartsByWidth.remove(recentWidths.removeFirst())
        return rowStartsByWidth.getOrPut(width) { chordRowStarts(cellWidths, groupStarts, gap, width) }
    }

    /** How much row [row] is moved down by the heading over it, its gap counted in; 0 for a row under none. */
    fun headingHeight(starts: IntArray, row: Int) = headings[starts[row]]?.let { it.size.height + gap } ?: 0

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
        val rows = slotRows(starts.size, firstSlot, lastSlot, isLastSlot)
        if (rows.isEmpty()) return 0
        return rows.sumOf { headingHeight(starts, it) + rowHeight(starts, it) } + gap * (rows.last - rows.first) + if (rows.last < starts.size - 1) gap else 0
    }

    /** The cell under [x], [y] in the slots from [firstSlot] to [lastSlot] at [width] (see [chordCellAt]), -1 where none. */
    fun cellAt(x: Float, y: Float, width: Int, firstSlot: Int, lastSlot: Int, isLastSlot: Boolean, isRightToLeft: Boolean): Int {
        val starts = rowStartsAt(width)
        val rows = slotRows(starts.size, firstSlot, lastSlot, isLastSlot)
        return chordCellAt(x, y, width, starts, rows, cellWidths, cellHeights, IntArray(starts.size) { headingHeight(starts, it) }, gap, isRightToLeft)
    }

    /** The cells the slots from [firstSlot] to [lastSlot] show at [width], which a screen reader is told about. */
    fun cellsAt(width: Int, firstSlot: Int, lastSlot: Int, isLastSlot: Boolean): IntRange {
        val starts = rowStartsAt(width)
        val rows = slotRows(starts.size, firstSlot, lastSlot, isLastSlot)
        return if (rows.isEmpty()) IntRange.EMPTY else starts[rows.first] until (starts.getOrNull(rows.last + 1) ?: cells.size)
    }
}

private val CELL_GAP = 6.dp

/** A pressed cell's card reaches half a [CELL_GAP] past it, so this leaves as much again between the card and the line over it. */
private val SECOND_LINE_GAP = 6.dp
private const val SECOND_NAME_SIZE = 0.75f

private val FRETTED_WIDTH = 56.dp
private val FRETTED_HEIGHT = 70.dp
private val KEYBOARD_WIDTH = 76.dp
private val KEYBOARD_HEIGHT = 40.dp

/** How many widths a section's rows are kept for, see [ChordCellLayouts.rowStartsAt]. */
private const val MAX_CHORD_ROW_WIDTHS = 8
