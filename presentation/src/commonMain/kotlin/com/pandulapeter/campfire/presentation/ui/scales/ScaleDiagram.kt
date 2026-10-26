/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.scales

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.pandulapeter.campfire.presentation.ui.chords.drawChordDiagram
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import kotlin.math.min

/**
 * A scale on the player's instrument: a neck with a dot on every note of it, or a keyboard with its keys filled, drawn
 * as a chord diagram is ([com.pandulapeter.campfire.presentation.ui.chords.ChordDiagram]) with the root in the second
 * accent color. It is one node to a screen reader, [description] saying what a dot per note would only list.
 *
 * @param labels What each of the scale's notes is labelled with inside its dot, by its place in the scale (its degree
 *   or its name), or null for none, as the small diagrams of the Scales tab are drawn. Fret numbers come with them.
 */
@Composable
internal fun ScaleDiagram(
    modifier: Modifier = Modifier,
    geometry: ScaleDiagramGeometry,
    labels: List<String>?,
    description: String,
) {
    // A capo moved slides the dimmed frets with it rather than redrawing them, starting where it is on the first frame.
    val capo by animateFloatAsState(
        targetValue = (geometry as? ScaleDiagramGeometry.Fretboard)?.capo?.toFloat() ?: 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    )
    val lineColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rootColor = LocalSecondAccentColor.current
    val backgroundColor = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()
    val fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
    Spacer(
        modifier = modifier
            .semantics { contentDescription = description }
            .drawBehind {
                when (geometry) {
                    is ScaleDiagramGeometry.Fretboard -> {
                        drawFretboard(geometry, labels, lineColor, mutedColor, rootColor, backgroundColor, textMeasurer, fontFamily)
                        drawBelowCapo(geometry, capo, backgroundColor)
                    }
                    is ScaleDiagramGeometry.Keyboard -> drawChordDiagram(
                        geometry = geometry.keyboard,
                        area = size,
                        lineColor = lineColor,
                        mutedColor = mutedColor,
                        rootColor = rootColor,
                        backgroundColor = backgroundColor,
                        textMeasurer = textMeasurer,
                        fontFamily = fontFamily,
                    )
                }
            },
    )
}

/**
 * The neck: a column of open notes left of the nut, as a chord diagram marks its open strings above it, then the frets,
 * the strings across them a little thicker the lower they sound, and faint inlays behind them. A note is a filled dot,
 * an open string's a ring, so that what is played without a finger reads apart. The labels are measured once per text and
 * size rather than once per dot, since a label of a scale is on every string, and share the size at which the widest of
 * them fits its dot.
 */
private fun DrawScope.drawFretboard(
    geometry: ScaleDiagramGeometry.Fretboard,
    labels: List<String>?,
    lineColor: Color,
    mutedColor: Color,
    rootColor: Color,
    backgroundColor: Color,
    textMeasurer: TextMeasurer,
    fontFamily: FontFamily?,
) {
    val fretGap = size.width / (geometry.fretCount + OPEN_COLUMN)
    // The fret numbers are sized from the frets rather than from the height, so that they are as legible on a four
    // string neck as on an eight string one, and the band they stand in is no taller than they are.
    val numbersBand = if (labels == null) 0f else fretGap * NUMBERS_BAND
    val rowGap = (size.height - numbersBand) / geometry.strings
    val nut = fretGap * OPEN_COLUMN
    val thin = (min(rowGap, fretGap) * LINE_WIDTH).coerceAtLeast(1f)
    val radius = min(fretGap * DOT_RADIUS, rowGap * DOT_ROW_RADIUS)
    val boardBottom = size.height - numbersBand
    fun y(row: Int) = (row + 0.5f) * rowGap
    fun fretLine(fret: Int) = nut + fret * fretGap
    fun center(fret: Int) = if (fret == 0) nut / 2f else fretLine(fret) - fretGap / 2f
    // A fretless neck has no inlays; its markers only number the positions under it.
    if (!geometry.isFretless) geometry.markers.forEach { fret ->
        val markerColor = mutedColor.copy(alpha = INLAY_ALPHA)
        if (fret % 12 == 0) {
            drawCircle(markerColor, radius = radius * INLAY_RADIUS, center = Offset(center(fret), boardBottom * 0.25f))
            drawCircle(markerColor, radius = radius * INLAY_RADIUS, center = Offset(center(fret), boardBottom * 0.75f))
        } else {
            drawCircle(markerColor, radius = radius * INLAY_RADIUS, center = Offset(center(fret), boardBottom / 2f))
        }
    }
    val dashes = if (geometry.isFretless) PathEffect.dashPathEffect(floatArrayOf(thin * 3, thin * 3)) else null
    val top = y(0)
    val bottom = y(geometry.strings - 1)
    (0..geometry.fretCount).forEach { fret ->
        val isNut = fret == 0 && !geometry.isFretless
        drawLine(
            color = if (geometry.isFretless && fret > 0) mutedColor else lineColor,
            start = Offset(fretLine(fret), top),
            end = Offset(fretLine(fret), bottom),
            strokeWidth = if (isNut) thin * NUT_WIDTH else thin,
            pathEffect = if (fret > 0) dashes else null,
        )
    }
    (0 until geometry.strings).forEach { row ->
        val start = geometry.stringStarts[row]
        drawLine(
            color = lineColor,
            // A string starting up the neck (the banjo's drone) starts at the ring of its open note.
            start = Offset(if (start == 0) fretLine(0) else center(start) + radius, y(row)),
            end = Offset(fretLine(geometry.fretCount), y(row)),
            strokeWidth = thin * (1f + geometry.stringWeights[row] * STRING_WEIGHT),
        )
    }
    // Measured with a style of its own rather than through label(), whose cache would keep it at this first size.
    val widestLabel = labels.orEmpty().distinct().maxOfOrNull { text ->
        val style = TextStyle(fontSize = (radius * LABEL_TEXT / density / fontScale).sp, fontWeight = FontWeight.Bold, fontFamily = fontFamily)
        textMeasurer.measure(text, style, density = this).size.width.toFloat()
    } ?: 0f
    val labelSize = (scaleLabelFontSize(radius, widestLabel) / density / fontScale).sp
    val numberSize = (fretGap * NUMBER_TEXT / density / fontScale).sp
    val measured = HashMap<String, TextLayoutResult>()
    fun label(text: String, color: Color, isNumber: Boolean = false) = measured.getOrPut("$text|${color.value}|$isNumber") {
        val style = TextStyle(
            fontSize = if (isNumber) numberSize else labelSize,
            fontWeight = if (isNumber) FontWeight.Medium else FontWeight.Bold,
            color = color,
            fontFamily = fontFamily,
        )
        textMeasurer.measure(text, style, density = this)
    }
    geometry.notes.forEach { note ->
        val dotCenter = Offset(center(note.fret), y(note.row))
        val color = if (note.isRoot) rootColor else lineColor
        val isOpen = note.fret == geometry.stringStarts[note.row]
        if (isOpen) {
            drawCircle(color, radius = radius - thin / 2f, center = dotCenter, style = Stroke(thin * RING_WIDTH))
        } else {
            drawCircle(color, radius = radius, center = dotCenter)
        }
        labels?.getOrNull(note.degree)?.let { text ->
            val layout = label(text, if (isOpen) color else backgroundColor)
            drawText(layout, topLeft = Offset(dotCenter.x - layout.size.width / 2f, dotCenter.y - layout.size.height / 2f))
        }
    }
    if (labels != null) {
        geometry.markers.forEach { fret ->
            val layout = label(fret.toString(), mutedColor, isNumber = true)
            drawText(layout, topLeft = Offset(center(fret) - layout.size.width / 2f, size.height - numbersBand / 2f - layout.size.height / 2f))
        }
    }
}

/**
 * The frets below [capo], which may be between two while it moves, veiled in [backgroundColor], their numbers too: the
 * column of open notes for a capo on the first fret, and every fret up to the one under the capo further up, whose notes
 * are the strings ringing at the capo and stay clear.
 */
private fun DrawScope.drawBelowCapo(geometry: ScaleDiagramGeometry.Fretboard, capo: Float, backgroundColor: Color) {
    if (capo <= 0f) return
    val fretGap = size.width / (geometry.fretCount + OPEN_COLUMN)
    val nut = fretGap * OPEN_COLUMN
    val end = if (capo <= 1f) nut * capo else nut + (capo - 1f) * fretGap
    drawRect(backgroundColor.copy(alpha = BELOW_CAPO_VEIL), size = Size(end, size.height))
}

/** How wide the column of open notes left of the nut is, in frets: room for a ring, and no more. */
private const val OPEN_COLUMN = 0.62f
/** How tall the band of fret numbers under the neck is, in frets. */
private const val NUMBERS_BAND = 0.62f
private const val NUMBER_TEXT = 0.44f
private const val LINE_WIDTH = 0.06f
private const val NUT_WIDTH = 3f
private const val STRING_WEIGHT = 0.8f
private const val RING_WIDTH = 1.5f
private const val DOT_RADIUS = 0.42f
private const val DOT_ROW_RADIUS = 0.46f
private const val INLAY_RADIUS = 0.3f
private const val INLAY_ALPHA = 0.18f
private const val BELOW_CAPO_VEIL = 0.7f
