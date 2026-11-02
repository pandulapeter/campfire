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

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * What is drawn in one area of a captured window: its background, the color most of its pixels have, and its ink, the
 * pixel farthest from that background in relative luminance. Anti-aliasing shades a glyph's edges differently on every
 * system, but not the middle of its strokes, which is drawn in the text's own color, so [contrast] reads the same on
 * every system the tests run on.
 */
internal class DrawnColors(val background: Color, val ink: Color) {

    /** The WCAG contrast ratio of the ink against the background, from 1 (the same) to 21 (black on white). */
    val contrast get() = contrastBetween(background, ink)
}

/** The background and the ink of [area] of this image, in its pixels (a dp at the tests' density of one). */
internal fun ImageBitmap.drawnColors(area: Rect): DrawnColors {
    val pixels = pixelsIn(area)
    val background = pixels.groupingBy { it }.eachCount().maxBy { it.value }.key
    val ink = pixels.maxBy { abs(it.luminance() - background.luminance()) }
    return DrawnColors(background = background, ink = ink)
}

/** The pixel of [area] of this image closest to [color], which is [color] itself wherever a stroke drawn in it is. */
internal fun ImageBitmap.closestTo(area: Rect, color: Color) = pixelsIn(area).minBy { pixel ->
    abs(pixel.red - color.red) + abs(pixel.green - color.green) + abs(pixel.blue - color.blue)
}

/** The WCAG contrast ratio of two colors. */
internal fun contrastBetween(first: Color, second: Color): Float {
    val lighter = max(first.luminance(), second.luminance())
    val darker = min(first.luminance(), second.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun ImageBitmap.pixelsIn(area: Rect): List<Color> {
    val left = floor(area.left).toInt().coerceIn(0, width - 1)
    val top = floor(area.top).toInt().coerceIn(0, height - 1)
    val right = area.right.toInt().coerceIn(left + 1, width)
    val bottom = area.bottom.toInt().coerceIn(top + 1, height)
    val map: PixelMap = toPixelMap(startX = left, startY = top, width = right - left, height = bottom - top)
    return (0 until map.height).flatMap { y -> (0 until map.width).map { x -> map[x, y] } }
}
