/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.pandulapeter.campfire.presentation.ui.chords.ChordDiagramGeometry
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import kotlin.math.min

/**
 * A chord diagram: a fretted shape as strings and frets with a dot on every stopped string, or a keyboard with the
 * pressed keys marked. The root is in the second accent color, the color the chords are written in. It is drawn
 * rather than composed, at whatever size [modifier] gives it, and it takes no press: a tap on it is a tap on the song.
 *
 * @param showsFingers Whether the dots carry the finger that holds them, where the shape says: legible at the Chord
 *   shapes sheet's size, and only clutter at the section's.
 */
@Composable
internal fun ChordDiagram(
    modifier: Modifier = Modifier,
    geometry: ChordDiagramGeometry,
    description: String,
    showsFingers: Boolean = false,
) {
    val lineColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val rootColor = LocalSecondAccentColor.current
    val backgroundColor = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()
    Spacer(
        modifier = modifier
            .semantics { contentDescription = description }
            .drawBehind {
                when (geometry) {
                    is ChordDiagramGeometry.Fretted -> drawFretted(geometry, lineColor, mutedColor, rootColor, backgroundColor, textMeasurer, showsFingers)
                    is ChordDiagramGeometry.Keyboard -> drawKeyboard(geometry, lineColor, rootColor, backgroundColor)
                }
            },
    )
}

private fun DrawScope.drawFretted(
    geometry: ChordDiagramGeometry.Fretted,
    lineColor: Color,
    mutedColor: Color,
    rootColor: Color,
    backgroundColor: Color,
    textMeasurer: TextMeasurer,
    showsFingers: Boolean,
) {
    // The left band is kept whether or not a base fret is named in it, so that every diagram of a row lines up.
    val left = size.width * SIDE_BAND
    val right = size.width - size.width * END_BAND
    val top = size.height * MARKER_BAND
    val bottom = size.height - size.height * END_BAND
    val stringGap = (right - left) / (geometry.strings - 1).coerceAtLeast(1)
    val fretGap = (bottom - top) / geometry.fretCount
    val thin = (min(stringGap, fretGap) * LINE_WIDTH).coerceAtLeast(1f)
    val radius = min(stringGap * DOT_RADIUS, fretGap * DOT_ROW_RADIUS)
    fun x(string: Int) = left + string * stringGap
    fun y(row: Int) = top + (row + 0.5f) * fretGap
    (0..geometry.fretCount).forEach { fret ->
        val isNut = fret == 0 && geometry.baseFret == 1
        drawLine(lineColor, Offset(left, top + fret * fretGap), Offset(right, top + fret * fretGap), strokeWidth = if (isNut) thin * NUT_WIDTH else thin, cap = StrokeCap.Square)
    }
    (0 until geometry.strings).forEach { string -> drawLine(lineColor, Offset(x(string), top), Offset(x(string), bottom), strokeWidth = thin) }
    if (geometry.baseFret > 1) {
        val label = textMeasurer.measure(geometry.baseFret.toString(), TextStyle(fontSize = (fretGap * BASE_FRET_TEXT / density / fontScale).sp, color = mutedColor))
        // Right of it the first string's dot reaches out by its radius, a barre's end included.
        drawText(label, topLeft = Offset((left - radius - thin * 2 - label.size.width).coerceAtLeast(0f), y(0) - label.size.height / 2f))
    }
    val markerY = top / 2f
    val markerSize = min(top * 0.32f, stringGap * 0.32f)
    geometry.markers.forEachIndexed { string, marker ->
        when (marker) {
            ChordDiagramGeometry.Fretted.Marker.MUTED -> {
                drawLine(mutedColor, Offset(x(string) - markerSize, markerY - markerSize), Offset(x(string) + markerSize, markerY + markerSize), strokeWidth = thin)
                drawLine(mutedColor, Offset(x(string) - markerSize, markerY + markerSize), Offset(x(string) + markerSize, markerY - markerSize), strokeWidth = thin)
            }
            ChordDiagramGeometry.Fretted.Marker.OPEN -> drawCircle(lineColor, radius = markerSize, center = Offset(x(string), markerY), style = Stroke(thin))
            ChordDiagramGeometry.Fretted.Marker.OPEN_ROOT -> drawCircle(rootColor, radius = markerSize, center = Offset(x(string), markerY), style = Stroke(thin * ROOT_RING_WIDTH))
            null -> Unit
        }
    }
    geometry.barres.forEach { barre ->
        drawRoundRect(
            color = lineColor,
            topLeft = Offset(x(barre.fromString) - radius, y(barre.row) - radius),
            size = Size(x(barre.toString) - x(barre.fromString) + radius * 2, radius * 2),
            cornerRadius = CornerRadius(radius, radius),
        )
        // A root at the barre's end is a dot of its own, which carries the finger instead.
        val isUnderDot = geometry.dots.any { it.string == barre.fromString && it.row == barre.row }
        if (showsFingers && barre.finger != null && !isUnderDot) drawFinger(textMeasurer, barre.finger, Offset(x(barre.fromString), y(barre.row)), radius, backgroundColor)
    }
    geometry.dots.forEach { dot ->
        val center = Offset(x(dot.string), y(dot.row))
        drawCircle(if (dot.isRoot) rootColor else lineColor, radius = radius, center = center)
        if (showsFingers && dot.finger != null) drawFinger(textMeasurer, dot.finger, center, radius, backgroundColor)
    }
}

private fun DrawScope.drawFinger(textMeasurer: TextMeasurer, finger: Int, center: Offset, radius: Float, color: Color) {
    val label = textMeasurer.measure(finger.toString(), TextStyle(fontSize = (radius * FINGER_TEXT / density / fontScale).sp, fontWeight = FontWeight.Bold, color = color))
    drawText(label, topLeft = Offset(center.x - label.size.width / 2f, center.y - label.size.height / 2f))
}

/**
 * A keyboard is drawn as one in either theme, its white keys the lighter of [lineColor] and [backgroundColor] and its
 * black keys the darker, since a keyboard with dark white keys reads as a negative of one.
 */
private fun DrawScope.drawKeyboard(
    geometry: ChordDiagramGeometry.Keyboard,
    lineColor: Color,
    rootColor: Color,
    backgroundColor: Color,
) {
    val isDarkTheme = backgroundColor.luminance() < lineColor.luminance()
    val light = if (isDarkTheme) lineColor else backgroundColor
    val dark = if (isDarkTheme) backgroundColor else lineColor
    val whiteKeys = geometry.octaves * WHITE_KEYS_PER_OCTAVE
    val keyWidth = size.width / whiteKeys
    val thin = (keyWidth * KEY_LINE_WIDTH).coerceAtLeast(1f)
    val blackHeight = size.height * BLACK_KEY_HEIGHT
    val radius = keyWidth * KEY_DOT_RADIUS
    drawRect(light)
    (0..whiteKeys).forEach { key -> drawLine(dark, Offset(key * keyWidth, 0f), Offset(key * keyWidth, size.height), strokeWidth = thin) }
    drawRect(dark, style = Stroke(thin))
    (0 until geometry.octaves * 12).filter { it % 12 in blackKeys }.forEach { note ->
        val center = blackKeyCenter(note) * keyWidth
        drawRect(dark, topLeft = Offset(center - keyWidth * BLACK_KEY_WIDTH / 2, 0f), size = Size(keyWidth * BLACK_KEY_WIDTH, blackHeight))
    }
    (geometry.keys + listOfNotNull(geometry.bass)).forEach { note ->
        val isBlack = note % 12 in blackKeys
        val center = Offset(
            x = if (isBlack) blackKeyCenter(note) * keyWidth else (whiteKeyIndex(note) + 0.5f) * keyWidth,
            y = if (isBlack) blackHeight - radius * 1.6f else size.height - radius * 1.8f,
        )
        val color = when {
            note in geometry.roots -> rootColor
            isBlack -> light
            else -> dark
        }
        if (note == geometry.bass && note !in geometry.keys) {
            drawCircle(color, radius = radius, center = center, style = Stroke(thin * 1.5f))
        } else {
            drawCircle(color, radius = radius, center = center)
        }
    }
}

/** Where the white key holding [note] is, counted in white keys from the diagram's first C. */
private fun whiteKeyIndex(note: Int) = note / 12 * WHITE_KEYS_PER_OCTAVE + whiteKeyOrder.indexOf(note % 12)

/** The middle of the black key holding [note], in white key widths from the diagram's first C: on the line between the two white keys it sits over. */
private fun blackKeyCenter(note: Int) = whiteKeyIndex(note + 1).toFloat()

private val whiteKeyOrder = listOf(0, 2, 4, 5, 7, 9, 11)
private val blackKeys = setOf(1, 3, 6, 8, 10)
private const val WHITE_KEYS_PER_OCTAVE = 7
private const val SIDE_BAND = 0.22f
private const val END_BAND = 0.08f
private const val MARKER_BAND = 0.18f
private const val LINE_WIDTH = 0.08f
private const val NUT_WIDTH = 3f
private const val ROOT_RING_WIDTH = 1.5f
private const val DOT_RADIUS = 0.46f
private const val DOT_ROW_RADIUS = 0.36f
private const val BASE_FRET_TEXT = 0.7f
private const val FINGER_TEXT = 1.2f
private const val BLACK_KEY_HEIGHT = 0.6f
private const val BLACK_KEY_WIDTH = 0.6f
private const val KEY_DOT_RADIUS = 0.36f
private const val KEY_LINE_WIDTH = 0.12f
