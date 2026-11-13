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

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import com.pandulapeter.campfire.presentation.ui.songLayout.GridEdge
import com.pandulapeter.campfire.presentation.ui.songLayout.GridLinePlacement
import com.pandulapeter.campfire.presentation.ui.songLayout.GridMetrics
import com.pandulapeter.campfire.presentation.ui.songLayout.GridSegment
import com.pandulapeter.campfire.presentation.ui.songLayout.PlacedGridBar
import com.pandulapeter.campfire.presentation.ui.songLayout.gridSegmentLabel
import com.pandulapeter.campfire.presentation.ui.songLayout.repeatSpan

/**
 * The colours of a drawn grid, all from the theme's scheme so that both themes and every theme colour work: the [box]
 * outline on its [fill], the [divider]s between a bar's chords, the [chord] names and their [hold] line, the [sign]s
 * (edges, dots, repeats, voltas), and the [text] around the bars.
 */
internal data class GridColors(
    val box: Color,
    val fill: Color,
    val divider: Color,
    val chord: Color,
    val hold: Color,
    val sign: Color,
    val text: Color,
)

/** What a drawn grid's texts are laid out as: a [chord] name or sign, a margin or trailing [text], a [volta] number. */
internal class GridTextLayouts(
    val chord: (String) -> TextLayoutResult,
    val text: (String) -> TextLayoutResult,
    val volta: (String) -> TextLayoutResult,
)

/** Draws one placed grid line ([GridLinePlacement]) left to right from the start of the scope, whatever its direction. */
internal fun DrawScope.drawGridLine(placement: GridLinePlacement, metrics: GridMetrics, texts: GridTextLayouts, colors: GridColors) {
    val stroke = maxOf(metrics.strokeWidth, 1f)
    placement.margin?.let { drawCentred(texts.text(it), x = 0f, top = placement.rowTops.first(), metrics, colors.text) }
    placement.bars.forEachIndexed { index, placed ->
        val top = placement.rowTops[placed.row]
        drawRoundRect(colors.fill, Offset(placed.x, top), Size(placed.width, metrics.boxHeight), CornerRadius(metrics.cornerRadius))
        placed.segments.drop(1).forEach { segment ->
            drawLine(colors.divider, Offset(segment.x, top + stroke), Offset(segment.x, top + metrics.boxHeight - stroke), stroke)
        }
        placed.segments.forEach { segment ->
            when (val drawn = segment.segment) {
                is GridSegment.Chord -> drawCentred(texts.chord(gridSegmentLabel(drawn)!!), segment.x + metrics.segmentPadding, top, metrics, colors.chord)
                is GridSegment.Hold -> {
                    val y = top + metrics.boxHeight / 2
                    val end = segment.x + segment.width - metrics.segmentPadding
                    if (end > segment.x + metrics.segmentPadding) {
                        drawLine(colors.hold, Offset(segment.x + metrics.segmentPadding, y), Offset(end, y), metrics.heavyWidth * 0.6f)
                    }
                }
                is GridSegment.Repeat -> {
                    val layout = texts.chord(gridSegmentLabel(drawn)!!)
                    val span = placement.repeatSpan(index, segment)
                    drawCentred(layout, (span.start + span.endInclusive - layout.size.width) / 2, top, metrics, colors.sign)
                }
            }
        }
        drawEdges(placed, top, metrics, colors.sign)
        drawRoundRect(colors.box, Offset(placed.x, top), Size(placed.width, metrics.boxHeight), CornerRadius(metrics.cornerRadius), style = Stroke(stroke))
        placed.bar.volta?.let { drawVolta(texts.volta(it), placed, top, metrics, colors.sign, stroke) }
    }
    placement.trailing?.let { drawCentred(texts.text(it.text), it.x, placement.rowTops[it.row], metrics, colors.text) }
}

private fun DrawScope.drawCentred(layout: TextLayoutResult, x: Float, top: Float, metrics: GridMetrics, color: Color) =
    drawText(layout, color = color, topLeft = Offset(x, top + (metrics.boxHeight - layout.size.height) / 2))

/**
 * A heavy edge as a bar standing inside the box's side, and a repeat's two dots inside that; drawn under the outline, so
 * that the rounded corners stay the box's.
 */
private fun DrawScope.drawEdges(placed: PlacedGridBar, top: Float, metrics: GridMetrics, color: Color) {
    fun edge(edge: GridEdge, side: Float, inwards: Float) {
        if (edge == GridEdge.PLAIN) return
        val inset = metrics.cornerRadius * 0.5f
        val barX = if (inwards > 0) side else side - metrics.heavyWidth
        drawRect(color, Offset(barX, top + inset), Size(metrics.heavyWidth, metrics.boxHeight - inset * 2))
        if (edge == GridEdge.REPEAT) {
            val dotX = side + inwards * (metrics.heavyWidth + metrics.dotRadius * 2.2f)
            drawCircle(color, metrics.dotRadius, Offset(dotX, top + metrics.boxHeight / 3))
            drawCircle(color, metrics.dotRadius, Offset(dotX, top + metrics.boxHeight * 2 / 3))
        }
    }
    edge(placed.bar.opening, side = placed.x, inwards = 1f)
    edge(placed.bar.closing, side = placed.x + placed.width, inwards = -1f)
}

/** A volta's bracket over its bar, a line along its top with a tick down at its start, numbered under the line. */
private fun DrawScope.drawVolta(label: TextLayoutResult, placed: PlacedGridBar, top: Float, metrics: GridMetrics, color: Color, stroke: Float) {
    val y = top - metrics.voltaHeight + stroke
    val bottom = top - metrics.rowGap / 2
    drawLine(color, Offset(placed.x, y), Offset(placed.x + placed.width, y), stroke)
    drawLine(color, Offset(placed.x, y), Offset(placed.x, bottom), stroke)
    drawText(label, color = color, topLeft = Offset(placed.x + metrics.segmentPadding, y + (bottom - y - label.size.height) / 2))
}
