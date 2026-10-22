/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.sync

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.LayoutDirection

/**
 * A short, faint segment that keeps leaving the end of [SyncProgressBar]'s fill and running to the window's edge, so
 * that a run moving a large file, whose fill may not visibly grow for many seconds, still shows that it is working.
 * It fades out as the unfilled room shrinks below [FADE_ROOM_HEIGHTS] bar heights, instead of vanishing at a threshold.
 */
@Composable
internal fun SyncProgressPulse(
    modifier: Modifier = Modifier,
    fraction: () -> Float,
) {
    val phase by rememberInfiniteTransition(label = "syncPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = CYCLE_MILLIS, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "syncPulsePhase",
    )
    val color = ProgressIndicatorDefaults.linearColor
    Canvas(modifier = modifier) {
        val capRadius = size.height / 2
        val span = syncPulseSpan(width = size.width, fillEnd = size.width * fraction(), capRadius = capRadius, phase = phase) ?: return@Canvas
        val alpha = PULSE_ALPHA * (span.room / (size.height * FADE_ROOM_HEIGHTS)).coerceIn(0f, 1f)
        // LinearProgressIndicator fills from the end in a right-to-left layout, so the pulse has to mirror with it.
        scale(scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f, scaleY = 1f) {
            drawLine(
                color = color.copy(alpha = color.alpha * alpha),
                start = Offset(span.start, center.y),
                end = Offset(span.end, center.y),
                strokeWidth = size.height,
                cap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
        }
    }
}

/**
 * Where the pulse's line runs at [phase] (0 to 1) of its cycle, its round caps ([capRadius]) excluded, or null while it
 * is out of sight. The room it travels starts one cap's gap after the fill's own cap and ends a cap short of [width], and
 * the segment enters it from the fill and leaves it at the edge, shortened by both rather than drawn over them.
 */
internal fun syncPulseSpan(
    width: Float,
    fillEnd: Float,
    capRadius: Float,
    phase: Float,
): SyncPulseSpan? {
    val roomStart = fillEnd + capRadius * 3
    val roomEnd = width - capRadius
    val room = roomEnd - roomStart
    if (room <= 0f) return null
    val length = minOf(width * SEGMENT_WIDTH_SHARE, room / 2)
    val head = roomStart + (room + length) * phase
    val start = (head - length).coerceIn(roomStart, roomEnd)
    val end = head.coerceIn(roomStart, roomEnd)
    return if (end > start) SyncPulseSpan(start = start, end = end, room = room) else null
}

/** A [syncPulseSpan] result: the line's ends, and the length of the room it travels. */
internal data class SyncPulseSpan(
    val start: Float,
    val end: Float,
    val room: Float,
)

private const val CYCLE_MILLIS = 1400
private const val PULSE_ALPHA = 0.4f
private const val SEGMENT_WIDTH_SHARE = 0.12f
private const val FADE_ROOM_HEIGHTS = 12f
