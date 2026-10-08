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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import com.pandulapeter.campfire.chordpro.edit.ChordProTabWrapper
import kotlin.math.roundToInt

/**
 * One piece of a run of `{start_of_tab}` lines, which is cut into as many rows as it takes to fit the width: the row at
 * [slot], or every row from there on where it [isLastSlot], and nothing at all where the run has no row there at this
 * width. A run is drawn as several of these (see [runSlotCount]) so that a section may be cut between its rows, which
 * are only known once the width is. A run with a staff in it is
 * tablature, cut the way a tab book breaks a staff into systems, its rows a blank line apart so that the last string of
 * one is never read as the first string of the next; one with no staff in it is preformatted text - chord names over
 * lyrics most often - cut between words, each line of chord names together with the lyrics under it, and read like
 * wrapped text with no gap between its rows (see [ChordProTabWrapper] for both). Neither scrolls sideways, since a song
 * may be read with nothing but a pedal that scrolls it up and down.
 *
 * The text is drawn rather than composed, because where the cuts fall depends on the width the block is measured
 * at, and the section this block is in is measured at several widths before one wins (see [SongSectionsLayout]):
 * a layout with no children answers every intrinsic measurement by running its measure block, so the height it
 * reports for a candidate width already counts the rows the tab would wrap into at that width.
 */
@Composable
internal fun SongTabBlock(
    modifier: Modifier = Modifier,
    lines: List<String>,
    rows: TabRows,
    slot: Int,
    isLastSlot: Boolean,
) {
    val color = MaterialTheme.colorScheme.onSurface
    // The rows of the slot at a width, and whether the run goes on after them, which is what leaves the gap that keeps
    // the last string of one system from being read as the first string of the next under them.
    fun slotRows(width: Int): Pair<List<List<TextLayoutResult>>, Boolean> {
        val all = rows.at(width)
        val mine = if (isLastSlot) all.drop(slot) else listOfNotNull(all.getOrNull(slot))
        return mine to (slot + mine.size < all.size)
    }
    Layout(
        // Drawn rather than composed (see above), so the lines are handed to a screen reader here, as they stand in the
        // file - by the first piece of the run, for all of it.
        modifier = modifier
            .then(if (slot == 0) Modifier.semantics { contentDescription = lines.joinToString(separator = "\n") } else Modifier)
            .drawBehind {
                var y = 0f
                slotRows(size.width.roundToInt()).first.forEach { row ->
                    row.forEach { line ->
                        drawText(textLayoutResult = line, color = color, topLeft = Offset(0f, y))
                        y += line.size.height
                    }
                    y += rows.rowGap
                }
            },
    ) { _, constraints ->
        val width = constraints.maxWidth
        val (rowsAtWidth, goesOn) = slotRows(width)
        val height = rowsAtWidth.sumOf { row -> row.sumOf { it.size.height } } +
            rows.rowGap * (rowsAtWidth.size - 1).coerceAtLeast(0) +
            if (goesOn && rowsAtWidth.isNotEmpty()) rows.rowGap else 0
        // Asked how wide it would be, every piece answers for the whole run, unwrapped: the layout narrows a column to
        // the widest of what it holds, and a column narrowed to what one piece of a run needs would wrap the run's other
        // pieces into rows of fewer bars - taller than the rows the page was planned with, and different ones.
        layout(
            width = if (width == Constraints.Infinity) rows.at(width).maxOfOrNull { row -> row.maxOf { it.size.width } } ?: 0 else width,
            height = height.coerceIn(constraints.minHeight, constraints.maxHeight),
        ) {}
    }
}

/**
 * The laid out rows of one run of tablature, per width. A run is measured at every width the column layout tries
 * and drawn at the one that won, so the widths it has been asked about most recently are kept: the same few come
 * back on every pass, and a row's text is laid out once rather than once per measurement. Only the last few
 * ([MAX_TAB_WIDTHS]), since a window being resized asks about a new width on every frame and none of those comes back.
 */
internal class TabRows(
    private val lines: List<String>,
    private val style: TextStyle,
    private val textMeasurer: TextMeasurer,
) {

    private val isTablature = ChordProTabWrapper.isTablature(lines)

    /** The height of a blank line in the tab's own font between the systems of tablature, and nothing elsewhere. */
    val rowGap = if (isTablature) textMeasurer.measure(AnnotatedString(LINE_HEIGHT_SAMPLE), style).size.height else 0

    // A monospace font, so the width of one character is the width of many divided by their count, measured with
    // enough of them for the rounding of the total not to matter.
    private val characterWidth = textMeasurer.measure(AnnotatedString(LINE_HEIGHT_SAMPLE.repeat(CHARACTER_WIDTH_SAMPLE_LENGTH)), style).size.width /
            CHARACTER_WIDTH_SAMPLE_LENGTH.toFloat()
    // The width the widest line takes whole, which is also the minimum intrinsic width of the block. A block given that
    // much must not wrap: the character width above is an estimate a fraction of a pixel off, and counting characters
    // with it at exactly this width can come out one short and cut the last bar off onto a staff of its own.
    private val naturalWidth by lazy(LazyThreadSafetyMode.NONE) {
        lines.maxOfOrNull { line -> textMeasurer.measure(AnnotatedString(line), style, softWrap = false).size.width } ?: 0
    }
    private val rowsByWidth = mutableMapOf<Int, List<List<TextLayoutResult>>>()
    private val recentWidths = ArrayDeque<Int>()

    fun at(width: Int): List<List<TextLayoutResult>> {
        recentWidths.remove(width)
        recentWidths.addLast(width)
        if (recentWidths.size > MAX_TAB_WIDTHS) rowsByWidth.remove(recentWidths.removeFirst())
        return rowsByWidth.getOrPut(width) {
            val maxColumns = if (width >= naturalWidth || characterWidth <= 0f) Int.MAX_VALUE else (width / characterWidth).toInt()
            val rows = if (isTablature) ChordProTabWrapper.wrap(lines, maxColumns) else ChordProTabWrapper.wrapPreformatted(lines, maxColumns)
            rows.map { row ->
                row.map { line -> textMeasurer.measure(AnnotatedString(line), style, softWrap = false) }
            }
        }
    }
}

private const val CHARACTER_WIDTH_SAMPLE_LENGTH = 64
private const val MAX_TAB_WIDTHS = 8
