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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import com.pandulapeter.campfire.chordpro.tabs.ChordProTabWrapper
import com.pandulapeter.campfire.presentation.ui.songLayout.runSlotCount
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import kotlin.math.ceil
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
 * may be read with nothing but a pedal that scrolls it up and down. Tablature is engraved rather than typed
 * ([TabStaffLines]): the strings and bar lines of a staff are drawn as strokes ([TabStaffGeometry]), so that they join
 * up the way a printed staff does, and its frets are set over them in the lyrics' face.
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
    val chordColor = LocalSecondAccentColor.current
    val staffColor = MaterialTheme.colorScheme.onSurfaceVariant
    // The rows of the slot at a width, and whether the run goes on after them, which is what leaves the gap that keeps
    // the last string of one system from being read as the first string of the next under them.
    fun slotRows(width: Int): Pair<List<TabRow>, Boolean> {
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
                    drawStaff(row = row, top = y, columnWidth = rows.columnWidth, stringOffset = rows.stringOffset, color = staffColor)
                    row.lines.forEach { line ->
                        line.texts.forEach { text -> drawText(textLayoutResult = text.layout, color = if (text.isChord) chordColor else color, topLeft = Offset(text.x, y + text.y)) }
                        y += line.height
                    }
                    y += rows.rowGap
                }
            },
    ) { _, constraints ->
        val width = constraints.maxWidth
        val (rowsAtWidth, goesOn) = slotRows(width)
        val height = rowsAtWidth.sumOf { row -> row.lines.sumOf { it.height } } +
            rows.rowGap * (rowsAtWidth.size - 1).coerceAtLeast(0) +
            if (goesOn && rowsAtWidth.isNotEmpty()) rows.rowGap else 0
        // Asked how wide it would be, every piece answers for the whole run, unwrapped: the layout narrows a column to
        // the widest of what it holds, and a column narrowed to what one piece of a run needs would wrap the run's other
        // pieces into rows of fewer bars - taller than the rows the page was planned with, and different ones.
        layout(
            width = if (width == Constraints.Infinity) rows.naturalWidth else width,
            height = height.coerceIn(constraints.minHeight, constraints.maxHeight),
        ) {}
    }
}

/**
 * The strings and bar lines of [row], drawn from [top]. A bar line on a string with no bar above or under it stops short
 * of the lines next to it, so that it is not read as joining the staff of another system.
 */
private fun DrawScope.drawStaff(row: TabRow, top: Float, columnWidth: Float, stringOffset: Float, color: Color) {
    val tops = row.lines.runningFold(top) { lineTop, line -> lineTop + line.height }
    val strokeWidth = (columnWidth * TabEngraving.STROKE_WIDTH_PER_COLUMN).coerceAtLeast(1f)
    row.staff.strings.forEach { string ->
        val y = tops[string.line] + stringOffset
        drawLine(color = color, start = Offset(string.from * columnWidth, y), end = Offset(string.to * columnWidth, y), strokeWidth = strokeWidth)
    }
    row.staff.bars.forEach { bar ->
        val x = (bar.column + HALF_COLUMN) * columnWidth
        val (from, to) = if (bar.fromLine == bar.toLine) {
            val inset = (tops[bar.fromLine + 1] - tops[bar.fromLine]) * TabEngraving.LONE_BAR_INSET
            tops[bar.fromLine] + inset to tops[bar.fromLine + 1] - inset
        } else {
            tops[bar.fromLine] + stringOffset to tops[bar.toLine] + stringOffset
        }
        // Square caps reach half the stroke past the outer strings, which closes the corner where a string meets a bar.
        drawLine(color = color, start = Offset(x, from), end = Offset(x, to), strokeWidth = strokeWidth, cap = StrokeCap.Square)
    }
}

/** One row of a run as it is drawn: its lines laid out in order, and the strokes of its staff under them. */
internal class TabRow(val lines: List<TabLine>, val staff: TabStaffGeometry)

/**
 * The laid out rows of one run of tablature, per width, or of verbatim notation where [isPreformatted]: that is
 * preformatted text even where a line of it would pass for a string of a staff. Tablature is engraved in the lyrics' and
 * chords' face ([TabStaffLines]); other text in the monospaced one, its columns being all it has to line up with. A run
 * is measured at every width the column layout tries
 * and drawn at the one that won, so the widths it has been asked about most recently are kept: the same few come
 * back on every pass, and a row's text is laid out once rather than once per measurement. Only the last few
 * ([MAX_TAB_WIDTHS]), since a window being resized asks about a new width on every frame and none of those comes back.
 * One instance is shared by every slot of the run ([SongTextMeasurements.tabRows]), which is why the same few widths
 * asked about by each of its slots cost one wrap per width rather than one per slot.
 */
internal class TabRows(
    private val lines: List<String>,
    private val styles: RunStyles,
    private val textMeasurer: TextMeasurer,
    private val isPreformatted: Boolean,
) {

    private val staffLines = if (!isPreformatted && ChordProTabWrapper.isTablature(lines)) TabStaffLines(styles, textMeasurer) else null

    /** The gap between the systems of tablature, and nothing elsewhere. */
    val rowGap = staffLines?.stringSpacing ?: 0

    // A monospace font, so the width of one character is the width of many divided by their count, measured with
    // enough of them for the rounding of the total not to matter.
    private val characterWidth by lazy(LazyThreadSafetyMode.NONE) {
        textMeasurer.measure(AnnotatedString(LINE_HEIGHT_SAMPLE.repeat(CHARACTER_WIDTH_SAMPLE_LENGTH)), styles.monospaceText).size.width /
            CHARACTER_WIDTH_SAMPLE_LENGTH.toFloat()
    }

    /** The width of a column, where the strokes of a staff are drawn. */
    val columnWidth get() = staffLines?.columnWidth ?: characterWidth

    /** How far below the top of a staff line its string runs. */
    val stringOffset = staffLines?.stringOffset ?: 0f

    /**
     * The width the widest line takes whole, which is also the minimum intrinsic width of the block. A block given that
     * much must not wrap: the column width is an estimate a fraction of a pixel off, and counting columns with it at
     * exactly this width can come out one short and cut the last bar off onto a staff of its own.
     */
    val naturalWidth by lazy(LazyThreadSafetyMode.NONE) {
        if (staffLines == null) {
            lines.maxOfOrNull { line -> textMeasurer.measure(AnnotatedString(line), styles.monospaceText, softWrap = false).size.width } ?: 0
        } else {
            ceil(ChordProTabWrapper.wrapLines(lines, Int.MAX_VALUE).flatten().maxOfOrNull { staffLines.line(it).width } ?: 0f).toInt()
        }
    }
    private val rowsByWidth = mutableMapOf<Int, List<TabRow>>()
    private val recentWidths = ArrayDeque<Int>()

    fun at(width: Int): List<TabRow> {
        recentWidths.remove(width)
        recentWidths.addLast(width)
        if (recentWidths.size > MAX_TAB_WIDTHS) rowsByWidth.remove(recentWidths.removeFirst())
        return rowsByWidth.getOrPut(width) {
            val maxColumns = if (width >= naturalWidth || columnWidth <= 0f) Int.MAX_VALUE else (width / columnWidth).toInt()
            if (staffLines != null) {
                ChordProTabWrapper.wrapLines(lines, maxColumns).map { row ->
                    TabRow(lines = row.map(staffLines::line), staff = tabStaffGeometryOf(row))
                }
            } else {
                ChordProTabWrapper.wrapPreformatted(lines, maxColumns).map { row ->
                    TabRow(
                        lines = row.map(::preformattedLine),
                        staff = TabStaffGeometry(strings = emptyList(), bars = emptyList()),
                    )
                }
            }
        }
    }

    /**
     * A line of a run with no staff, drawn whole: a line of chord names over lyrics in a tab run is drawn as the page's
     * chords are, while another program's notation is only text.
     */
    private fun preformattedLine(line: String): TabLine {
        val isChord = !isPreformatted && ChordProTabWrapper.isChordLine(line)
        val style = if (isChord) styles.monospaceChord else styles.monospaceText
        return TabLine.of(textMeasurer.measure(AnnotatedString(line), style, softWrap = false), isChord = isChord)
    }
}

private const val CHARACTER_WIDTH_SAMPLE_LENGTH = 64
private const val MAX_TAB_WIDTHS = 8
private const val HALF_COLUMN = 0.5f
