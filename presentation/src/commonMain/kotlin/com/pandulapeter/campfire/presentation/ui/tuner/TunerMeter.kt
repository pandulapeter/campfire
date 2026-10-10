/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_flat
import com.pandulapeter.campfire.presentation.resources.tuner_sharp
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * The cents meter: a scale of ticks from −50 to +50 between the words for its two ends, a band marking the in-tune
 * window around its centre, a notch at the centre and a needle that moves along it on a spring. A scale rather than a
 * track with a knob on it, since a track with a knob is what a slider looks like and this is read, never dragged. A
 * reading further off than the scale reaches (a preset's string more than a semitone away) pins the needle at the end it
 * is off towards. With nothing heard the needle is gone and the scale rests. In tune the needle gives way to the notch
 * closing into a check, so that being in tune is never said by color alone.
 *
 * @param accentColor What the check is drawn in, the needle's color being the text's; the window is always tinted with
 * the second accent color, the color of what is played.
 */
@Composable
internal fun TunerMeter(
    modifier: Modifier = Modifier,
    cents: Float?,
    isInTune: Boolean,
    accentColor: Color,
    height: Dp,
) = Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(END_LABEL_GAP),
) {
    val position = animateFloatAsState(
        targetValue = ((cents ?: 0f) / MAX_CENTS).coerceIn(-1f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "tunerNeedle",
    )
    val needleAlpha = animateFloatAsState(if (cents == null || isInTune) 0f else 1f, label = "tunerNeedleAlpha")
    val check = animateFloatAsState(if (isInTune) 1f else 0f, label = "tunerCheck")
    val checkPath = remember { Path() }
    // The window is tinted with the color of what is played rather than filled with a surface, since a filled box with
    // the check drawn in it reads as a checkbox.
    val bandColor = LocalSecondAccentColor.current.copy(alpha = BAND_ALPHA)
    val minorTickColor = MaterialTheme.colorScheme.outlineVariant
    val majorTickColor = MaterialTheme.colorScheme.outline
    val needleColor = MaterialTheme.colorScheme.onSurface
    val notchColor = animateColorAsState(if (isInTune) accentColor else MaterialTheme.colorScheme.onSurfaceVariant, label = "tunerNotch").value
    EndLabel(text = stringResource(Res.string.tuner_flat))
    Canvas(modifier = Modifier.weight(1f).height(height)) {
        val needleWidth = NEEDLE_WIDTH.toPx()
        val usable = size.width - needleWidth
        fun xOf(fraction: Float) = needleWidth / 2 + usable * (fraction + 1) / 2
        val centerY = size.height / 2
        val bandHalfHeight = size.height * BAND_HEIGHT / 2
        drawRoundRect(
            color = bandColor,
            topLeft = Offset(xOf(-IN_TUNE_CENTS / MAX_CENTS), centerY - bandHalfHeight),
            size = Size(xOf(IN_TUNE_CENTS / MAX_CENTS) - xOf(-IN_TUNE_CENTS / MAX_CENTS), bandHalfHeight * 2),
            cornerRadius = CornerRadius(BAND_CORNER_RADIUS.toPx()),
        )
        // A tick every five cents, taller every ten, and none at the centre, which the notch marks. Where the scale is
        // too narrow for twenty of them to read as separate lines, the five-cent ticks are left out.
        val hasMinorTicks = usable / (TICK_COUNT * 2) >= MIN_TICK_SPACING.toPx()
        for (tick in -TICK_COUNT..TICK_COUNT) {
            if (tick == 0) continue
            val x = xOf(tick / TICK_COUNT.toFloat())
            if (tick % 2 == 0) {
                drawTick(x, centerY, size.height * MAJOR_TICK_HEIGHT, MAJOR_TICK_WIDTH.toPx(), majorTickColor)
            } else if (hasMinorTicks) {
                drawTick(x, centerY, size.height * MINOR_TICK_HEIGHT, MINOR_TICK_WIDTH.toPx(), minorTickColor)
            }
        }
        val centerX = xOf(0f)
        val notchHeight = size.height * NOTCH_HEIGHT
        if (check.value < 1f) {
            drawTick(centerX, centerY, notchHeight, NOTCH_WIDTH.toPx(), notchColor.copy(alpha = 1f - check.value))
        }
        if (check.value > 0f) {
            val unit = notchHeight / 4
            checkPath.reset()
            checkPath.moveTo(centerX - unit * 1.4f, centerY + unit * 0.1f)
            checkPath.lineTo(centerX - unit * 0.3f, centerY + unit * 1.2f)
            checkPath.lineTo(centerX + unit * 1.6f, centerY - unit * 1.2f)
            drawPath(
                path = checkPath,
                color = notchColor.copy(alpha = check.value),
                style = Stroke(width = CHECK_WIDTH.toPx(), cap = StrokeCap.Round),
            )
        }
        if (needleAlpha.value > 0f) {
            drawTick(xOf(position.value), centerY, size.height * NEEDLE_HEIGHT, needleWidth, needleColor.copy(alpha = needleAlpha.value))
        }
    }
    EndLabel(text = stringResource(Res.string.tuner_sharp))
}

/** One of the words at the meter's ends. */
@Composable
private fun RowScope.EndLabel(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1,
)

/** A vertical line of the scale, [height] tall and centred on ([x], [centerY]), with round ends. */
private fun DrawScope.drawTick(x: Float, centerY: Float, height: Float, width: Float, color: Color) = drawLine(
    color = color,
    start = Offset(x, centerY - height / 2),
    end = Offset(x, centerY + height / 2),
    strokeWidth = width,
    cap = StrokeCap.Round,
)

private const val MAX_CENTS = 50f

/** The window the band marks, half the ten cents between two tall ticks: what the tracker calls in tune lies inside it. */
private const val IN_TUNE_CENTS = 5f

/** Ticks on each side of the centre, one every five cents. */
private const val TICK_COUNT = 10

/** The heights of what the meter draws, as a share of its own, tallest first: the needle over the notch over the band and the ticks. */
private const val NEEDLE_HEIGHT = 0.9f
private const val NOTCH_HEIGHT = 0.7f
private const val BAND_HEIGHT = 0.5f
private const val MAJOR_TICK_HEIGHT = 0.4f
private const val MINOR_TICK_HEIGHT = 0.2f

private const val BAND_ALPHA = 0.18f

private val NEEDLE_WIDTH = 3.dp
private val NOTCH_WIDTH = 2.dp
private val CHECK_WIDTH = 3.dp
private val MAJOR_TICK_WIDTH = 1.5.dp
private val MINOR_TICK_WIDTH = 1.dp
private val BAND_CORNER_RADIUS = 4.dp
private val MIN_TICK_SPACING = 7.dp
private val END_LABEL_GAP = 12.dp
