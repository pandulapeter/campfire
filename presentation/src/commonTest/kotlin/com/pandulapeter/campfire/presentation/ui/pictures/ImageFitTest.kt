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
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageFitTest {

    private val plain = ChordProBlock.Image(src = "a.jpg")

    @Test
    fun `a picture wider than the column is fitted to its width - keeping its proportions`() {
        assertEquals(FittedImage(400f, 300f), fittedImageSize(plain, pixelWidth = 4000, pixelHeight = 3000, unit = 1f, columnWidth = 400f))
    }

    @Test
    fun `a picture taller than the room is scaled down to it so it is never cut`() {
        assertEquals(FittedImage(300f, 400f), fittedImageSize(plain, pixelWidth = 3000, pixelHeight = 4000, unit = 1f, columnWidth = 400f, maxHeight = 400f))
    }

    @Test
    fun `a small picture is never blown up past a unit a pixel`() {
        assertEquals(FittedImage(200f, 100f), fittedImageSize(plain, pixelWidth = 100, pixelHeight = 50, unit = 2f, columnWidth = 800f, maxHeight = 1000f))
    }

    @Test
    fun `a requested width in points narrows the picture`() {
        val image = plain.copy(width = ImageLength.Points(100.0))
        assertEquals(FittedImage(200f, 100f), fittedImageSize(image, pixelWidth = 1000, pixelHeight = 500, unit = 2f, columnWidth = 800f))
    }

    @Test
    fun `a requested width never widens the picture past the column or its own size`() {
        val image = plain.copy(width = ImageLength.Points(5000.0))
        assertEquals(FittedImage(400f, 200f), fittedImageSize(image, pixelWidth = 1000, pixelHeight = 500, unit = 1f, columnWidth = 400f))
        assertEquals(FittedImage(100f, 50f), fittedImageSize(image, pixelWidth = 100, pixelHeight = 50, unit = 1f, columnWidth = 400f))
    }

    @Test
    fun `a width in percent is a share of the column`() {
        val image = plain.copy(width = ImageLength.Percent(50.0))
        assertEquals(FittedImage(200f, 100f), fittedImageSize(image, pixelWidth = 1000, pixelHeight = 500, unit = 1f, columnWidth = 400f))
    }

    @Test
    fun `a height in percent is a share of the room`() {
        val image = plain.copy(height = ImageLength.Percent(25.0))
        assertEquals(FittedImage(200f, 100f), fittedImageSize(image, pixelWidth = 1000, pixelHeight = 500, unit = 1f, columnWidth = 400f, maxHeight = 400f))
    }

    @Test
    fun `width and height together keep the proportions - taking the smaller`() {
        val image = plain.copy(width = ImageLength.Points(300.0), height = ImageLength.Points(50.0))
        assertEquals(FittedImage(100f, 50f), fittedImageSize(image, pixelWidth = 1000, pixelHeight = 500, unit = 1f, columnWidth = 400f))
    }

    @Test
    fun `a scale narrows it further and a scale above one widens nothing`() {
        assertEquals(FittedImage(200f, 150f), fittedImageSize(plain.copy(scale = 0.5), pixelWidth = 4000, pixelHeight = 3000, unit = 1f, columnWidth = 400f))
        assertEquals(FittedImage(100f, 50f), fittedImageSize(plain.copy(scale = 3.0), pixelWidth = 100, pixelHeight = 50, unit = 1f, columnWidth = 400f))
    }

    @Test
    fun `a picture with no size takes no room`() {
        assertEquals(FittedImage(0f, 0f), fittedImageSize(plain, pixelWidth = 0, pixelHeight = 50, unit = 1f, columnWidth = 400f))
    }

    @Test
    fun `a narrower picture stands where its alignment says - centred by default`() {
        assertEquals(0f, imageLeftEdge(ImageAlignment.LEFT, columnWidth = 400f, imageWidth = 100f))
        assertEquals(150f, imageLeftEdge(null, columnWidth = 400f, imageWidth = 100f))
        assertEquals(300f, imageLeftEdge(ImageAlignment.RIGHT, columnWidth = 400f, imageWidth = 100f))
        assertEquals(0f, imageLeftEdge(ImageAlignment.RIGHT, columnWidth = 400f, imageWidth = 400f))
    }
}
