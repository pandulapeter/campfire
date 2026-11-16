/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import com.pandulapeter.campfire.data.formats.image.ImageFormat
import com.pandulapeter.campfire.data.formats.image.ImageInfo
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.presentation.ui.pictures.pictureAttemptsFor
import kotlinx.coroutines.test.runTest
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** The desktop's re-encoder on real pictures, made here rather than kept as large files. */
class PictureReencoderTest {

    @Test
    fun `a photo of more than 25 megapixels comes out as a JPEG the library takes, at the most pixels allowed`() = runTest(timeout = 2.minutes) {
        val photo = jpegOf(gradient(6_000, 4_500))
        assertTrue(ImageInfo.isOverPixelLimit(photo))

        val copy = assertNotNull(reencodePicture(photo, pictureAttemptsFor(ImageFormat.JPEG), ImportLimits.MAX_IMAGE_FILE_SIZE))

        assertTrue(copy.size <= ImportLimits.MAX_IMAGE_FILE_SIZE)
        assertEquals(ImageInfo(format = ImageFormat.JPEG, width = 4_000, height = 3_000), ImageInfo.of(copy))
    }

    @Test
    fun `a noisy PNG with transparency too large even as a PNG comes out as a JPEG flattened onto white`() = runTest(timeout = 2.minutes) {
        val random = Random(7)
        val image = BufferedImage(2_000, 1_500, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, image.width, image.height, IntArray(image.width * image.height) { random.nextInt() and 0x00FFFFFF }, 0, image.width)
        // A clear block, which a JPEG has no way to keep clear.
        image.setRGB(0, 0, 100, 100, IntArray(100 * 100), 0, 100)
        val picture = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        assertTrue(picture.size > ImportLimits.MAX_IMAGE_FILE_SIZE)

        val copy = assertNotNull(reencodePicture(picture, pictureAttemptsFor(ImageFormat.PNG), ImportLimits.MAX_IMAGE_FILE_SIZE))

        assertTrue(copy.size <= ImportLimits.MAX_IMAGE_FILE_SIZE)
        assertEquals(ImageFormat.JPEG, ImageInfo.of(copy)?.format)
        val decoded = ImageIO.read(ByteArrayInputStream(copy))
        assertTrue(Color(decoded.getRGB(50, 50)).run { red > 240 && green > 240 && blue > 240 })
    }

    @Test
    fun `a photo turned by its EXIF orientation comes out upright, with its sides swapped and no orientation left`() = runTest {
        // Stored landscape, left half red and right half blue; orientation 6 shows it turned a quarter clockwise, which
        // puts the red half on top.
        val stored = BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB)
        stored.createGraphics().apply {
            color = Color.RED
            fillRect(0, 0, 200, 200)
            color = Color.BLUE
            fillRect(200, 0, 200, 200)
            dispose()
        }
        val photo = withOrientation(jpegOf(stored), orientation = 6)
        assertEquals(ImageInfo(format = ImageFormat.JPEG, width = 200, height = 400), ImageInfo.of(photo))

        val copy = assertNotNull(reencodePicture(photo, pictureAttemptsFor(ImageFormat.JPEG), ImportLimits.MAX_IMAGE_FILE_SIZE))

        assertEquals(ImageInfo(format = ImageFormat.JPEG, width = 200, height = 400), ImageInfo.of(copy))
        val decoded = ImageIO.read(ByteArrayInputStream(copy))
        assertEquals(200, decoded.width)
        assertTrue(Color(decoded.getRGB(100, 100)).run { red > 200 && blue < 60 })
        assertTrue(Color(decoded.getRGB(100, 300)).run { blue > 200 && red < 60 })
    }

    @Test
    fun `bytes that are no picture come out as nothing`() = runTest {
        assertNull(reencodePicture("{title: A}".encodeToByteArray(), pictureAttemptsFor(null), ImportLimits.MAX_IMAGE_FILE_SIZE))
    }

    private fun gradient(width: Int, height: Int) = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { image ->
        val row = IntArray(width)
        for (y in 0 until height) {
            for (x in 0 until width) row[x] = (x * 255 / width shl 16) or (y * 255 / height shl 8) or 0x40
            image.setRGB(0, y, width, 1, row, 0, width)
        }
    }

    private fun jpegOf(image: BufferedImage) = ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()

    /** [jpeg] with an EXIF segment naming [orientation] right after its JFIF one. */
    private fun withOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
        val tiff = bytes(
            'M'.code, 'M'.code, 0, 42, 0, 0, 0, 8,
            0, 1,
            0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation, 0, 0,
            0, 0, 0, 0,
        )
        val payload = "Exif".encodeToByteArray() + bytes(0, 0) + tiff
        val segment = bytes(0xFF, 0xE1, (payload.size + 2) shr 8, (payload.size + 2) and 0xFF) + payload
        val jfifEnd = 4 + (((jpeg[4].toInt() and 0xFF) shl 8) or (jpeg[5].toInt() and 0xFF))
        return jpeg.copyOfRange(0, jfifEnd) + segment + jpeg.copyOfRange(jfifEnd, jpeg.size)
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
}
