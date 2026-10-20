/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.scales

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.pandulapeter.campfire.chordpro.scales.Scale
import com.pandulapeter.campfire.presentation.ui.scales.SCALE_NOTE_LEAD_MILLIS
import com.pandulapeter.campfire.presentation.ui.scales.displayedDegree

/**
 * The degrees in one line. While the scale plays, a circle stands behind [playingDegree] and slides from degree to
 * degree, each note sounding as the circle reaches it, the degree under it turning to the colour drawn on the circle; it
 * grows and fades in where the run starts and shrinks and fades out where it ends. Every circle is as wide as the widest
 * degree needs, so it does not swell and shrink on its way.
 */
@Composable
internal fun ScaleDegrees(
    modifier: Modifier = Modifier,
    scale: Scale,
    playingDegree: Int?,
) {
    val degreesText = remember(scale) { scaleDegreesTextOf(scale.degrees.map(::displayedDegree)) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // Where the circle fades out is the degree it last stood behind, not the first one it would jump back to.
    var lastDegree by remember(scale) { mutableIntStateOf(0) }
    SideEffect { if (playingDegree != null) lastDegree = playingDegree }
    val target = layout?.let { degreesText.ranges.getOrNull(playingDegree ?: lastDegree)?.let(it::centerOf) }
    val circleCenter = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val circleAlpha = remember { Animatable(0f) }
    // Declared before the fade, so that a run starting reads the circle still hidden and puts it in place without a
    // slide from wherever the last one stopped.
    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        if (circleAlpha.value == 0f) circleCenter.snapTo(target) else circleCenter.animateTo(target, circleMotion())
    }
    LaunchedEffect(playingDegree != null) {
        circleAlpha.animateTo(if (playingDegree != null) 1f else 0f, circleMotion())
    }
    val circleColor = MaterialTheme.colorScheme.primary
    // One animation per degree: a scale's sheet always has as many, so the calls keep their places between frames.
    val colors = degreesText.ranges.indices.map { index ->
        animateColorAsState(
            targetValue = if (index == playingDegree) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            animationSpec = circleMotion(),
            label = "scaleDegreeColor",
        ).value
    }
    Text(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val shownLayout = layout ?: return@drawBehind
                if (circleAlpha.value == 0f) return@drawBehind
                drawCircle(
                    color = circleColor,
                    // It grows into place as it fades in, and shrinks away as it fades out.
                    radius = (shownLayout.circleDiameterOf(degreesText.ranges) + CIRCLE_PADDING.toPx()) / 2 *
                        lerp(CIRCLE_HIDDEN_SCALE, 1f, circleAlpha.value),
                    center = circleCenter.value,
                    alpha = circleAlpha.value,
                )
            },
        text = buildAnnotatedString {
            append(degreesText.text)
            degreesText.ranges.forEachIndexed { index, range -> addStyle(SpanStyle(color = colors[index]), range.first, range.last + 1) }
        },
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        onTextLayout = { layout = it },
    )
}

/** The degrees joined into the line the sheet shows, with where each of them stands in it. */
internal class ScaleDegreesText(
    val text: String,
    val ranges: List<IntRange>,
)

internal fun scaleDegreesTextOf(degrees: List<String>): ScaleDegreesText {
    val ranges = mutableListOf<IntRange>()
    val text = buildString {
        degrees.forEachIndexed { index, degree ->
            if (index > 0) append(DEGREE_SEPARATOR)
            ranges += length until length + degree.length
            append(degree)
        }
    }
    return ScaleDegreesText(text, ranges)
}

/** The middle of the characters of [range], across them and down their line. */
private fun TextLayoutResult.centerOf(range: IntRange): Offset {
    val line = getLineForOffset(range.first)
    return Offset(
        x = (getBoundingBox(range.first).left + getBoundingBox(range.last).right) / 2,
        y = (getLineTop(line) + getLineBottom(line)) / 2,
    )
}

/** As wide as the widest of [ranges] and as tall as a line, whichever is more. */
private fun TextLayoutResult.circleDiameterOf(ranges: List<IntRange>) = ranges.maxOfOrNull { range ->
    val line = getLineForOffset(range.first)
    maxOf(getBoundingBox(range.last).right - getBoundingBox(range.first).left, getLineBottom(line) - getLineTop(line))
} ?: 0f

private const val DEGREE_SEPARATOR = "  "

/**
 * How the circle and the colours under it move: in the time the note waits for them ([SCALE_NOTE_LEAD_MILLIS]), so that
 * the note sounds as the circle settles, and at full speed from the first frame, so that the move reads as starting the
 * moment the last note's time is up.
 */
private fun <T> circleMotion() = tween<T>(durationMillis = SCALE_NOTE_LEAD_MILLIS, easing = LinearOutSlowInEasing)
private val CIRCLE_PADDING = 6.dp
private const val CIRCLE_HIDDEN_SCALE = 0.4f
