/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

import com.pandulapeter.campfire.chordpro.language.WritingDirection
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.presentation.ui.songLayout.GridBarLayout
import com.pandulapeter.campfire.presentation.ui.songLayout.GridEdge
import com.pandulapeter.campfire.presentation.ui.songLayout.GridLinePlacement
import com.pandulapeter.campfire.presentation.ui.songLayout.GridMetrics
import com.pandulapeter.campfire.presentation.ui.songLayout.GridSegment
import com.pandulapeter.campfire.presentation.ui.songLayout.PlacedGridBar
import com.pandulapeter.campfire.presentation.ui.songLayout.gridBarLinesOf
import com.pandulapeter.campfire.presentation.ui.songLayout.gridSegmentLabel
import com.pandulapeter.campfire.presentation.ui.songLayout.repeatSpan

/**
 * A run of grid lines drawn as the screen draws them, by the same [GridBarLayout] in points: a row per line, its bars
 * boxes in the run's columns, broken after the same bar as every other line of the run where the column is too narrow.
 *
 * None of the drawing is in the text layer: read back, a chord name in a box would be a chord over a lyric. Each line
 * reaches it as ChordPro writes it instead, an invisible text over the drawing, which an import reads back as the grid
 * line it is. A right-to-left song moves the run to its line end as one block ([isRightToLeft]); its insides read left to
 * right.
 */
internal fun printGridRows(
    lines: List<ChordProLine.Grid>,
    lineWidth: Float,
    fontSize: Float,
    isRightToLeft: Boolean,
    measure: (String, PrintStyle) -> Float,
): List<Row> {
    val styles = PrintGridStyles(fontSize)
    val layout = GridBarLayout(
        lines = gridBarLinesOf(lines.map { it.tokens }),
        metrics = GridMetrics(em = fontSize, textHeight = fontSize * TEXT_HEIGHT),
        labelWidth = { measure(it, styles.chord) },
        textWidth = { measure(it, styles.text) },
    )
    val shift = if (isRightToLeft) (lineWidth - layout.width).coerceAtLeast(0f) else 0f
    return lines.indices.map { index ->
        val placement = layout.placeLine(index, lineWidth)
        val written = lines[index].writtenText()
        // The line as written is wider than its drawing, and a text running into the next column would be read with it.
        val writtenStyle = styles.written.let { style -> style.copy(size = style.size * minOf(1f, lineWidth / measure(written, style))) }
        PrintGridLine(placement, layout.metrics, styles, measure).row(written, writtenStyle, shift)
    }
}

private class PrintGridStyles(fontSize: Float) {
    val chord = PrintStyle(size = fontSize, bold = true)
    val sign = PrintStyle(size = fontSize, bold = true, gray = SIGN_GRAY)
    val text = PrintStyle(size = fontSize, gray = TEXT_GRAY)
    val volta = PrintStyle(size = fontSize * VOLTA_TEXT_SCALE, gray = SIGN_GRAY)
    val written = PrintStyle(size = fontSize, monospace = true, direction = WritingDirection.LEFT_TO_RIGHT)
}

/** The parts, rules, and boxes one placed grid line is drawn with, collected into its [row]. */
private class PrintGridLine(
    private val placement: GridLinePlacement,
    private val metrics: GridMetrics,
    private val styles: PrintGridStyles,
    private val measure: (String, PrintStyle) -> Float,
) {
    private val parts = mutableListOf<Part>()
    private val rules = mutableListOf<PrintRule>()
    private val boxes = mutableListOf<PrintBox>()

    fun row(writtenText: String, writtenStyle: PrintStyle, shift: Float): Row {
        parts += Part(writtenText, style = writtenStyle, isDrawn = false)
        placement.margin?.let { drawn(it, x = 0f, top = placement.rowTops.first(), styles.text) }
        placement.bars.forEachIndexed { index, placed -> bar(index, placed) }
        placement.trailing?.let { drawn(it.text, it.x, placement.rowTops[it.row], styles.text) }
        return Row(
            parts = parts.map { it.copy(x = it.x + shift) },
            height = placement.height,
            rules = rules.map { it.copy(x = it.x + shift) },
            boxes = boxes.map { it.copy(x = it.x + shift) },
        )
    }

    private fun bar(index: Int, placed: PlacedGridBar) {
        val top = placement.rowTops[placed.row]
        val stroke = metrics.strokeWidth
        boxes += PrintBox(placed.x, top, placed.width, metrics.boxHeight, metrics.cornerRadius, fillGray = FILL_GRAY)
        placed.segments.drop(1).forEach { segment ->
            rules += PrintRule(segment.x - stroke / 2, top + stroke, stroke, metrics.boxHeight - stroke * 2, gray = DIVIDER_GRAY)
        }
        placed.segments.forEach { segment ->
            when (val drawn = segment.segment) {
                is GridSegment.Chord -> drawn(gridSegmentLabel(drawn)!!, segment.x + metrics.segmentPadding, top, styles.chord)
                is GridSegment.Hold -> {
                    val width = segment.width - metrics.segmentPadding * 2
                    val thickness = metrics.heavyWidth * 0.6f
                    if (width > 0) rules += PrintRule(segment.x + metrics.segmentPadding, top + (metrics.boxHeight - thickness) / 2, width, thickness, gray = HOLD_GRAY)
                }
                is GridSegment.Repeat -> {
                    val label = gridSegmentLabel(drawn)!!
                    val span = placement.repeatSpan(index, segment)
                    drawn(label, (span.start + span.endInclusive - measure(label, styles.sign)) / 2, top, styles.sign)
                }
            }
        }
        edge(placed.bar.opening, side = placed.x, inwards = 1f, top)
        edge(placed.bar.closing, side = placed.x + placed.width, inwards = -1f, top)
        boxes += PrintBox(placed.x, top, placed.width, metrics.boxHeight, metrics.cornerRadius, strokeGray = BOX_GRAY, lineWidth = stroke)
        placed.bar.volta?.let { volta(it, placed, top) }
    }

    private fun edge(edge: GridEdge, side: Float, inwards: Float, top: Float) {
        if (edge == GridEdge.PLAIN) return
        val inset = metrics.cornerRadius * 0.5f
        val x = if (inwards > 0) side else side - metrics.heavyWidth
        rules += PrintRule(x, top + inset, metrics.heavyWidth, metrics.boxHeight - inset * 2, gray = SIGN_GRAY)
        if (edge == GridEdge.REPEAT) {
            val radius = metrics.dotRadius
            val centre = side + inwards * (metrics.heavyWidth + radius * 2.2f)
            listOf(metrics.boxHeight / 3, metrics.boxHeight * 2 / 3).forEach { y ->
                boxes += PrintBox(centre - radius, top + y - radius, radius * 2, radius * 2, cornerRadius = radius, fillGray = SIGN_GRAY)
            }
        }
    }

    private fun volta(label: String, placed: PlacedGridBar, top: Float) {
        val stroke = metrics.strokeWidth
        val y = top - metrics.voltaHeight + stroke
        val bottom = top - metrics.rowGap / 2
        rules += PrintRule(placed.x, y - stroke / 2, placed.width, stroke, gray = SIGN_GRAY)
        rules += PrintRule(placed.x - stroke / 2, y, stroke, bottom - y, gray = SIGN_GRAY)
        val height = styles.volta.size * TEXT_HEIGHT
        parts += Part(label, x = placed.x + metrics.segmentPadding, y = y + (bottom - y - height) / 2, style = styles.volta, isSelectable = false)
    }

    /** A drawn text, centred in the height of a box on the row starting at [top], which only the drawing has. */
    private fun drawn(text: String, x: Float, top: Float, style: PrintStyle) {
        parts += Part(text, x = x, y = top + (metrics.boxHeight - style.size * TEXT_HEIGHT) / 2, style = style, isSelectable = false)
    }
}

/** The line as ChordPro writes it, which the text layer holds in place of the drawing. */
private fun ChordProLine.Grid.writtenText() = tokens.joinToString(" ") { token ->
    when (token) {
        is GridToken.Bar -> token.text
        is GridToken.Chord -> token.name
        GridToken.Beat -> "."
        is GridToken.Repeat -> token.text
        is GridToken.Text -> token.text
    }
}

/** The height of one line of text in ems, which the texts are centred in their boxes by. */
private const val TEXT_HEIGHT = 1.2f
private const val VOLTA_TEXT_SCALE = 0.6f
private const val FILL_GRAY = 245
private const val BOX_GRAY = 160
private const val DIVIDER_GRAY = 215
private const val HOLD_GRAY = 175
private const val SIGN_GRAY = 110
private const val TEXT_GRAY = 90
