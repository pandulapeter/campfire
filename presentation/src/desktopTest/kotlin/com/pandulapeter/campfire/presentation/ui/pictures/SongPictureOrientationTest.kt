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

import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import coil3.BitmapImage
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.SuccessResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A JPEG whose EXIF orientation turns it by a quarter, decoded the way a song's picture is: upright, in the size its
 * header reads as, so it never draws sideways in the box laid out for it.
 */
internal class SongPictureOrientationTest {

    /** 32 × 16 pixels stored, red on the left and blue on the right, to be shown turned clockwise: red on top. */
    private val photo = ByteArrayOutputStream().also { output ->
        val image = BufferedImage(32, 16, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 16) for (x in 0 until 32) image.setRGB(x, y, if (x < 16) Color.RED.rgb else Color.BLUE.rgb)
        ImageIO.write(image, "jpg", output)
    }.toByteArray().withOrientation(6)

    private class Pictures(private val bytes: ByteArray) : SongImageLoader {
        override val cacheKey = "orientation"
        override val sizes = MutableStateFlow(emptyMap<String, SongImageSize>())
        override suspend fun loadSizes(names: Set<String>) = Unit
        override suspend fun loadImage(name: String) = bytes
    }

    @Test fun `a picture its EXIF orientation turns is decoded upright`() = runBlocking {
        val context = PlatformContext.INSTANCE
        val result = SingletonImageLoader.get(context).execute(songPictureRequest(context, Pictures(photo), "photo.jpg"))
        val bitmap = ((result as SuccessResult).image as BitmapImage).bitmap.asComposeImageBitmap()
        assertEquals(16 to 32, bitmap.width to bitmap.height)
        val pixels = bitmap.toPixelMap()
        assertTrue(pixels[8, 4].red > 0.8f && pixels[8, 4].blue < 0.2f, "The top is ${pixels[8, 4]}, not red.")
        assertTrue(pixels[8, 28].blue > 0.8f && pixels[8, 28].red < 0.2f, "The bottom is ${pixels[8, 28]}, not blue.")
    }

    /** This JPEG with an EXIF segment saying [orientation] right after its start marker. */
    private fun ByteArray.withOrientation(orientation: Int): ByteArray {
        val tiff = byteArrayOf(
            'M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 8,
            0, 1,
            0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation.toByte(), 0, 0,
            0, 0, 0, 0,
        )
        val payload = "Exif".encodeToByteArray() + byteArrayOf(0, 0) + tiff
        val length = payload.size + 2
        val segment = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        return copyOfRange(0, 2) + segment + copyOfRange(2, size)
    }
}
