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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageZoomTest {

    private val viewport = Size(400f, 800f)
    private val fitted = Size(400f, 300f)

    @Test
    fun `a fitted picture cannot be zoomed out or moved`() {
        assertEquals(ImageZoom(), ImageZoom().zoomedBy(zoom = 0.5f, pan = Offset(50f, 50f), centroid = Offset(200f, 400f), viewport = viewport, fitted = fitted))
    }

    @Test
    fun `zooming keeps the point under the fingers where it was`() {
        val zoomed = ImageZoom().zoomedBy(zoom = 2f, pan = Offset.Zero, centroid = Offset(300f, 400f), viewport = viewport, fitted = fitted)
        assertEquals(2f, zoomed.scale)
        assertEquals(Offset(-100f, 0f), zoomed.offset)
    }

    @Test
    fun `a zoomed picture is never moved past its edge`() {
        val zoomed = ImageZoom(scale = 2f).zoomedBy(zoom = 1f, pan = Offset(1000f, 1000f), centroid = Offset(200f, 400f), viewport = viewport, fitted = fitted)
        // 800 wide in a 400 wide viewport reaches 200 each way; 600 high in 800 reaches nowhere.
        assertEquals(Offset(200f, 0f), zoomed.offset)
    }

    @Test
    fun `zooming stops at the most it allows`() {
        assertEquals(ImageZoom.MAX_SCALE, ImageZoom(scale = 4f).zoomedBy(zoom = 3f, pan = Offset.Zero, centroid = Offset(200f, 400f), viewport = viewport, fitted = fitted).scale)
    }

    @Test
    fun `toggling zooms in, and out again from any zoom`() {
        val zoomedIn = ImageZoom().toggled(Offset(200f, 400f), viewport, fitted)
        assertEquals(ImageZoom(ImageZoom.TOGGLE_SCALE, Offset.Zero), zoomedIn)
        assertEquals(ImageZoom(), ImageZoom(scale = 1.3f, offset = Offset(10f, 0f)).toggled(Offset.Zero, viewport, fitted))
    }
}
