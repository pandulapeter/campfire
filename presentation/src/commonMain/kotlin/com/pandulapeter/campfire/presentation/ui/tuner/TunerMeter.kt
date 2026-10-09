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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_flat
import com.pandulapeter.campfire.presentation.resources.tuner_sharp

/**
 * The cents meter: a track from −50 to +50 with a notch at its centre, and a marker that moves along it on a spring. A
 * reading further off than the track reaches (a preset's string more than a semitone away) pins the marker at the end it
 * is off towards. With nothing heard the marker rests at the centre, faded. In tune the notch closes into a check.
 */
@Composable
internal fun TunerMeter(
    modifier: Modifier = Modifier,
    cents: Float?,
    isInTune: Boolean,
    markerColor: Color,
) = Column(modifier = modifier) {
    val position = animateFloatAsState(
        targetValue = ((cents ?: 0f) / MAX_CENTS).coerceIn(-1f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "tunerMarker",
    )
    val markerAlpha = animateFloatAsState(if (cents == null) 0.3f else 1f, label = "tunerMarkerAlpha")
    val check = animateFloatAsState(if (isInTune) 1f else 0f, label = "tunerCheck")
    val checkPath = remember { Path() }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val tickColor = MaterialTheme.colorScheme.outline
    val notchColor = animateColorAsState(if (isInTune) markerColor else MaterialTheme.colorScheme.onSurfaceVariant, label = "tunerNotch").value
    Canvas(modifier = Modifier.fillMaxWidth().height(METER_HEIGHT)) {
        val trackHeight = 8.dp.toPx()
        val centerY = size.height / 2
        val radius = 6.dp.toPx()
        val usable = size.width - radius * 2
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, centerY - trackHeight / 2),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2),
        )
        // A tick every ten cents, the in-tune range being inside the two either side of the centre.
        for (tick in -4..4) {
            if (tick == 0) continue
            val x = radius + usable * (tick + 5) / 10f
            drawLine(tickColor, Offset(x, centerY - trackHeight), Offset(x, centerY + trackHeight), strokeWidth = 1.dp.toPx())
        }
        val centerX = size.width / 2
        val notchHeight = size.height * 0.8f
        if (check.value < 1f) {
            drawLine(
                color = notchColor.copy(alpha = 1f - check.value),
                start = Offset(centerX, centerY - notchHeight / 2),
                end = Offset(centerX, centerY + notchHeight / 2),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        if (check.value > 0f) {
            val unit = notchHeight / 4
            checkPath.reset()
            checkPath.moveTo(centerX - unit, centerY - unit * 1.6f)
            checkPath.lineTo(centerX - unit * 0.2f, centerY - unit * 0.8f)
            checkPath.lineTo(centerX + unit * 1.2f, centerY - unit * 2.4f)
            drawPath(
                path = checkPath,
                color = notchColor.copy(alpha = check.value),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        drawCircle(
            color = markerColor.copy(alpha = markerAlpha.value),
            radius = radius * 1.4f,
            center = Offset(radius + usable * (position.value + 1) / 2, centerY),
        )
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(Res.string.tuner_flat),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(Res.string.tuner_sharp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val MAX_CENTS = 50f
private val METER_HEIGHT = 40.dp
