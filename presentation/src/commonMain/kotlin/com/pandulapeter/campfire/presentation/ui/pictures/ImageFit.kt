/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.pictures

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ImageAlignment
import com.pandulapeter.campfire.chordpro.model.ImageLength
import kotlin.math.floor
import kotlin.math.min

/** The size a picture is drawn at, in the units it was fitted in (pixels on screen, points in the PDF). */
internal data class FittedImage(val width: Float, val height: Float)

/**
 * The size [image], [pixelWidth] by [pixelHeight] pixels, is drawn at in a column [columnWidth] wide that shows at most
 * [maxHeight] of it at once, every length in one unit, of which [unit] is one pixel of the picture and one point of
 * ChordPro's (a dp on screen, a point in the PDF: ChordPro, like a browser, draws a pixel as a point).
 *
 * The picture keeps its proportions and is only ever made smaller, by the least of what limits it: its own size (never
 * blown up past one unit a pixel, which only blurs it), the column's width, [maxHeight] (so it is never cut and is always
 * seen whole on one page, one pedal press), and what the directive asks: `width` and `height` in points or a percentage
 * (of the column's width for a width, of [maxHeight] for a height, or of the column's width where there is no height
 * to take one of), then `scale` of what that leaves, so `scale=0.5` is half of what would be drawn without it. A
 * request can only narrow it, never widen it past any of the others. Whole units, so that a measurement and the drawing
 * agree.
 */
internal fun fittedImageSize(
    image: ChordProBlock.Image,
    pixelWidth: Int,
    pixelHeight: Int,
    unit: Float,
    columnWidth: Float,
    maxHeight: Float = Float.POSITIVE_INFINITY,
): FittedImage {
    if (pixelWidth <= 0 || pixelHeight <= 0 || columnWidth <= 0f) return FittedImage(0f, 0f)
    val naturalWidth = pixelWidth * unit
    val naturalHeight = pixelHeight * unit
    val heightBase = if (maxHeight.isFinite()) maxHeight else columnWidth
    val factor = minOf(
        1f,
        columnWidth / naturalWidth,
        if (maxHeight.isFinite()) maxHeight / naturalHeight else 1f,
        image.width?.let { it.inUnits(unit, columnWidth) / naturalWidth } ?: 1f,
        image.height?.let { it.inUnits(unit, heightBase) / naturalHeight } ?: 1f,
    ).coerceAtLeast(0f) * (image.scale?.toFloat() ?: 1f).coerceAtMost(1f)
    return FittedImage(
        width = min(columnWidth, floor(naturalWidth * factor)),
        height = floor(naturalHeight * factor).let { if (maxHeight.isFinite()) min(it, maxHeight) else it },
    )
}

/** How far from the column's left edge a picture [imageWidth] wide stands in a column [columnWidth] wide, by [alignment]. */
internal fun imageLeftEdge(alignment: ImageAlignment?, columnWidth: Float, imageWidth: Float): Float {
    val room = (columnWidth - imageWidth).coerceAtLeast(0f)
    return when (alignment ?: ImageAlignment.CENTER) {
        ImageAlignment.LEFT -> 0f
        ImageAlignment.CENTER -> floor(room / 2)
        ImageAlignment.RIGHT -> room
    }
}

private fun ImageLength.inUnits(unit: Float, base: Float) = when (this) {
    is ImageLength.Points -> points.toFloat() * unit
    is ImageLength.Percent -> percent.toFloat() / 100f * base
}
