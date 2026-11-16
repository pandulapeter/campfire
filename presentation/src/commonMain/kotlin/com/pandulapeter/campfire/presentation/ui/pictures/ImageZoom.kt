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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * How the full screen viewer shows a picture: [scale] times the size it fits the screen at, moved by [offset] from the
 * centre. Changed only through [zoomedBy] and [toggled], which keep the picture over the screen.
 */
@Immutable
internal data class ImageZoom(val scale: Float = 1f, val offset: Offset = Offset.Zero) {

    /**
     * This zoom after a gesture that scaled by [zoom] around [centroid] (from the viewport's top left) and moved by
     * [pan], for a picture fitted to [fitted] in a [viewport]: never smaller than fitted, never past [MAX_SCALE], and
     * never moved further than takes its edge to the viewport's, so that it cannot be lost off the screen.
     */
    fun zoomedBy(zoom: Float, pan: Offset, centroid: Offset, viewport: Size, fitted: Size): ImageZoom {
        val newScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
        val applied = newScale / scale
        // The point under the centroid stays under it: the offset is measured from the viewport's centre.
        val fromCentre = centroid - viewport.center
        val moved = (offset - fromCentre) * applied + fromCentre + pan
        return ImageZoom(newScale, moved.clampedFor(newScale, viewport, fitted))
    }

    /** Back to fitted where zoomed in at all, else in to [TOGGLE_SCALE] around [point]: what a double tap or click does. */
    fun toggled(point: Offset, viewport: Size, fitted: Size) =
        if (scale > 1f) ImageZoom() else ImageZoom().zoomedBy(TOGGLE_SCALE, Offset.Zero, point, viewport, fitted)

    private fun Offset.clampedFor(scale: Float, viewport: Size, fitted: Size): Offset {
        val reachX = ((fitted.width * scale - viewport.width) / 2).coerceAtLeast(0f)
        val reachY = ((fitted.height * scale - viewport.height) / 2).coerceAtLeast(0f)
        return Offset(x.coerceIn(-reachX, reachX), y.coerceIn(-reachY, reachY))
    }

    companion object {
        const val MAX_SCALE = 5f
        const val TOGGLE_SCALE = 2.5f
    }
}

private val Size.center get() = Offset(width / 2, height / 2)
