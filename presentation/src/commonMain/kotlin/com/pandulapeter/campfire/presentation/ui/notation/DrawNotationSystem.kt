/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.drawText

/**
 * Draws [system] with its top left corner at [top], [scale] pixels to the SVG unit. Everything is drawn in [color] but
 * chord symbols, which are drawn in [chordColor], as the chords over the lyrics are; the colours are only given here,
 * so a theme cross-fade draws again without anything being laid out again.
 *
 * Words were laid out at [unitSize] pixels to the unit ([NotationPainter.unitSize]), so they are scaled by what is left
 * of [scale] once that is taken out.
 */
internal fun DrawScope.drawNotationSystem(system: NotationSystemPaint, top: Float, scale: Float, unitSize: Float, color: Color, chordColor: Color) =
    translate(top = top) {
        scale(scale = scale, pivot = Offset.Zero) {
            drawPath(path = system.fills, color = color)
            system.strokes.forEach { stroke ->
                drawPath(
                    path = stroke.path,
                    color = color,
                    style = Stroke(width = stroke.width, pathEffect = stroke.dashes?.let { PathEffect.dashPathEffect(it.toFloatArray()) }),
                )
            }
            system.texts.forEach { text ->
                translate(left = text.x, top = text.baseline) {
                    scale(scale = 1f / unitSize, pivot = Offset.Zero) {
                        drawText(
                            textLayoutResult = text.layout,
                            color = if (text.isChord) chordColor else color,
                            topLeft = Offset(0f, -text.layout.firstBaseline),
                        )
                    }
                }
            }
        }
    }
