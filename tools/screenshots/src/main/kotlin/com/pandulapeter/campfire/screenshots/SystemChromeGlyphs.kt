/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The three arcs and the dot of a full Wi-Fi signal, opening upwards from the bottom middle of the area. */
internal fun DrawScope.drawWifi(color: Color) {
    val stroke = size.height * 0.16f
    val center = Offset(size.width / 2, size.height * 0.95f)
    listOf(0.95f, 0.62f, 0.3f).forEach { fraction ->
        val radius = size.height * fraction - stroke / 2
        drawArc(
            color = color,
            startAngle = 225f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
    drawCircle(color = color, radius = stroke * 0.8f, center = Offset(center.x, center.y - stroke * 0.6f))
}

/** Four bars of a full cellular signal, each taller than the last. */
internal fun DrawScope.drawCellular(color: Color) {
    val gap = size.width * 0.1f
    val barWidth = (size.width - gap * 3) / 4
    repeat(4) { index ->
        val height = size.height * (0.4f + 0.2f * index)
        drawRoundRect(
            color = color,
            topLeft = Offset(index * (barWidth + gap), size.height - height),
            size = Size(barWidth, height),
            cornerRadius = CornerRadius(barWidth / 3),
        )
    }
}

/** A full battery lying on its side, with the terminal at its end where the platform draws one. */
internal fun DrawScope.drawBattery(color: Color, hasNub: Boolean) {
    val nub = if (hasNub) size.width * 0.08f else 0f
    val body = Size(size.width - nub, size.height)
    val stroke = size.height * 0.09f
    drawRoundRect(
        color = color.copy(alpha = 0.4f),
        size = body,
        cornerRadius = CornerRadius(size.height * 0.3f),
        style = Stroke(stroke),
    )
    val inset = stroke * 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(body.width - inset * 2, body.height - inset * 2),
        cornerRadius = CornerRadius(size.height * 0.18f),
    )
    if (hasNub) {
        drawRoundRect(
            color = color.copy(alpha = 0.4f),
            topLeft = Offset(body.width + nub * 0.25f, size.height * 0.33f),
            size = Size(nub * 0.75f, size.height * 0.34f),
            cornerRadius = CornerRadius(nub / 2),
        )
    }
}

/**
 * iOS 27's status ring, the iPhone Duo's status bar when it stands on its side: a full battery as an arc around the
 * area, open at the bottom, where four dots of a full cellular signal sit, and Wi-Fi in the middle.
 */
internal fun DrawScope.drawStatusRing(color: Color) {
    val stroke = size.minDimension * 0.075f
    val radius = (size.minDimension - stroke) / 2
    drawArc(
        color = color,
        startAngle = 90f + STATUS_RING_OPENING / 2,
        sweepAngle = 360f - STATUS_RING_OPENING,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    listOf(-16.5f, -5.5f, 5.5f, 16.5f).forEach { degrees ->
        val angle = (90.0 + degrees) * PI / 180
        drawCircle(
            color = color,
            radius = stroke * 0.4f,
            center = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat()),
        )
    }
    val wifiWidth = size.width * 0.4f
    val wifiHeight = wifiWidth * 0.72f
    val left = center.x - wifiWidth / 2
    val top = center.y - wifiHeight * 0.6f
    inset(left = left, top = top, right = size.width - left - wifiWidth, bottom = size.height - top - wifiHeight) { drawWifi(color) }
}

/** How much of the status ring, in degrees, is left open at its bottom for the cellular dots. */
private const val STATUS_RING_OPENING = 70f

/** A magnifier, its lens at the top left and its handle running to the bottom right. */
internal fun DrawScope.drawMagnifier(color: Color) {
    val stroke = size.minDimension * 0.12f
    val radius = size.minDimension * 0.33f
    val center = Offset(radius + stroke / 2, radius + stroke / 2)
    drawCircle(color = color, radius = radius, center = center, style = Stroke(stroke))
    val start = center + Offset(radius * 0.72f, radius * 0.72f)
    drawLine(color, start, Offset(size.width - stroke / 2, size.height - stroke / 2), stroke, cap = StrokeCap.Round)
}

/** macOS' Control Center: two switches, one over the other. */
internal fun DrawScope.drawControlCenter(color: Color) {
    val stroke = size.height * 0.09f
    val pill = Size(size.width - stroke, size.height * 0.42f - stroke)
    listOf(0f, size.height * 0.58f).forEachIndexed { index, top ->
        drawRoundRect(color, topLeft = Offset(stroke / 2, top + stroke / 2), size = pill, cornerRadius = CornerRadius(pill.height / 2), style = Stroke(stroke))
        val knob = Offset(if (index == 0) stroke / 2 + pill.height / 2 else size.width - stroke / 2 - pill.height / 2, top + stroke / 2 + pill.height / 2)
        drawCircle(color, radius = pill.height / 2 - stroke, center = knob)
    }
}

/** The Windows 11 logo, four panes in the system's light blue gradient. */
internal fun DrawScope.drawWindowsLogo(isDark: Boolean) {
    val gap = size.width * 0.06f
    val pane = (size.width - gap) / 2
    val top = if (isDark) Color(0xFF6DD3FF) else Color(0xFF3CC4FF)
    val bottom = if (isDark) Color(0xFF2C9BF0) else Color(0xFF0078D4)
    listOf(Offset.Zero, Offset(pane + gap, 0f), Offset(0f, pane + gap), Offset(pane + gap, pane + gap)).forEach { origin ->
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(top, bottom), startY = 0f, endY = size.height),
            topLeft = origin,
            size = Size(pane, pane),
            cornerRadius = CornerRadius(size.width * 0.03f),
        )
    }
}

/** The chevron that opens the hidden icons of the system tray. */
internal fun DrawScope.drawChevronUp(color: Color) {
    val stroke = 1.dp.toPx()
    val half = size.height * 0.3f
    val center = Offset(size.width / 2, size.height / 2)
    drawLine(color, center + Offset(-half, half / 2), center + Offset(0f, -half / 2), stroke, cap = StrokeCap.Round)
    drawLine(color, center + Offset(0f, -half / 2), center + Offset(half, half / 2), stroke, cap = StrokeCap.Round)
}

/** A speaker at full volume. */
internal fun DrawScope.drawSpeaker(color: Color) {
    val stroke = size.width * 0.07f
    val path = Path().apply {
        moveTo(size.width * 0.08f, size.height * 0.38f)
        lineTo(size.width * 0.26f, size.height * 0.38f)
        lineTo(size.width * 0.48f, size.height * 0.18f)
        lineTo(size.width * 0.48f, size.height * 0.82f)
        lineTo(size.width * 0.26f, size.height * 0.62f)
        lineTo(size.width * 0.08f, size.height * 0.62f)
        close()
    }
    drawPath(path, color, style = Stroke(stroke))
    listOf(0.16f, 0.3f).forEach { radius ->
        val r = size.width * radius
        drawArc(color, -45f, 90f, false, topLeft = Offset(size.width * 0.52f - r, size.height / 2 - r), size = Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** The notification bell at the end of the Windows 11 taskbar. */
internal fun DrawScope.drawBell(color: Color) {
    val stroke = size.width * 0.07f
    val path = Path().apply {
        moveTo(size.width * 0.18f, size.height * 0.72f)
        lineTo(size.width * 0.26f, size.height * 0.6f)
        lineTo(size.width * 0.26f, size.height * 0.42f)
        cubicTo(size.width * 0.26f, size.height * 0.1f, size.width * 0.74f, size.height * 0.1f, size.width * 0.74f, size.height * 0.42f)
        lineTo(size.width * 0.74f, size.height * 0.6f)
        lineTo(size.width * 0.82f, size.height * 0.72f)
        close()
    }
    drawPath(path, color, style = Stroke(stroke))
    drawArc(color, 0f, 180f, false, topLeft = Offset(size.width * 0.4f, size.height * 0.72f), size = Size(size.width * 0.2f, size.height * 0.16f), style = Stroke(stroke))
}
