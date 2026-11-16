/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.image

import com.pandulapeter.campfire.data.formats.image.TestImages.bytes
import com.pandulapeter.campfire.data.formats.image.TestImages.exif
import com.pandulapeter.campfire.data.formats.image.TestImages.segment
import com.pandulapeter.campfire.data.formats.zip.Mutator
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ImageInfoTest {

    @Test
    fun `a PNG is read from its IHDR chunk`() =
        assertEquals(ImageInfo(ImageFormat.PNG, 640, 480), ImageInfo.of(TestImages.png(640, 480).bytes))

    @Test
    fun `a GIF is read from its logical screen`() =
        assertEquals(ImageInfo(ImageFormat.GIF, 320, 200), ImageInfo.of(TestImages.gif(320, 200).bytes))

    @Test
    fun `a lossy WebP is read from its key frame`() =
        assertEquals(ImageInfo(ImageFormat.WEBP, 1024, 768), ImageInfo.of(TestImages.webpLossy(1024, 768).bytes))

    @Test
    fun `a lossless WebP is read from its header`() =
        assertEquals(ImageInfo(ImageFormat.WEBP, 16_384, 3), ImageInfo.of(TestImages.webpLossless(16_384, 3).bytes))

    @Test
    fun `an extended WebP is read from its canvas`() =
        assertEquals(ImageInfo(ImageFormat.WEBP, 30_000, 1), ImageInfo.of(TestImages.webpExtended(30_000, 1).bytes))

    @Test
    fun `a baseline JPEG is read from its frame header`() =
        assertEquals(ImageInfo(ImageFormat.JPEG, 4032, 3024), ImageInfo.of(TestImages.jpeg(4032, 3024).bytes))

    @Test
    fun `a progressive JPEG is read from its frame header`() =
        assertEquals(ImageInfo(ImageFormat.JPEG, 800, 600), ImageInfo.of(TestImages.jpeg(800, 600, frameMarker = 0xC2).bytes))

    @Test
    fun `an EXIF orientation of 1 keeps the sides`() {
        for (isLittleEndian in listOf(true, false)) {
            val jpeg = TestImages.jpeg(4032, 3024, segments = exif(orientation = 1, isLittleEndian = isLittleEndian))
            assertEquals(ImageInfo(ImageFormat.JPEG, 4032, 3024), ImageInfo.of(jpeg.bytes), "Little endian: $isLittleEndian")
        }
    }

    @Test
    fun `an EXIF orientation of 6 or 8 swaps the sides`() {
        for (orientation in listOf(6, 8)) {
            for (isLittleEndian in listOf(true, false)) {
                val jpeg = TestImages.jpeg(4032, 3024, segments = exif(orientation = orientation, isLittleEndian = isLittleEndian))
                assertEquals(ImageInfo(ImageFormat.JPEG, 3024, 4032), ImageInfo.of(jpeg.bytes), "$orientation, little endian: $isLittleEndian")
            }
        }
    }

    @Test
    fun `large segments, padding, and a table before the frame header are walked past`() {
        val segments = segment(0xE0, TestImages.ascii("JFIF") + ByteArray(10)) + exif(orientation = 6, isLittleEndian = false) +
            segment(0xE2, ByteArray(40_000) { it.toByte() }) + bytes(0xFF, 0xFF, 0xFF) + segment(0xC4, ByteArray(30) { 0xC0.toByte() })
        assertEquals(ImageInfo(ImageFormat.JPEG, 600, 800), ImageInfo.of(TestImages.jpeg(800, 600, segments = segments).bytes))
    }

    @Test
    fun `a JPEG whose frame header lies past the first 64 KiB is not read`() {
        val segments = segment(0xE2, ByteArray(65_000)) + segment(0xE3, ByteArray(1_000))
        assertNull(ImageInfo.of(TestImages.jpeg(800, 600, segments = segments).bytes))
    }

    @Test
    fun `a zero or oversized side is refused`() {
        assertNull(ImageInfo.of(TestImages.png(0, 480).bytes))
        assertNull(ImageInfo.of(TestImages.png(ImageInfo.MAX_SIDE + 1, 480).bytes))
        assertNull(ImageInfo.of(TestImages.png(-1, 480).bytes))
        assertNull(ImageInfo.of(TestImages.gif(320, 0).bytes))
        assertNull(ImageInfo.of(TestImages.jpeg(0, 600).bytes))
        assertNull(ImageInfo.of(TestImages.jpeg(800, 0).bytes))
        assertNull(ImageInfo.of(TestImages.webpExtended(ImageInfo.MAX_SIDE + 1, 10).bytes))
        assertEquals(ImageInfo(ImageFormat.PNG, ImageInfo.MAX_SIDE, 1), ImageInfo.of(TestImages.png(ImageInfo.MAX_SIDE, 1).bytes))
    }

    @Test
    fun `a header cut short anywhere is refused, and one cut after it is read`() {
        for (image in validImages()) {
            val full = ImageInfo.of(image.bytes)
            for (length in 0..image.bytes.size) {
                val read = ImageInfo.of(image.bytes.copyOf(length))
                if (length < image.headerEnd) assertNull(read, "$full cut to $length bytes") else assertEquals(full, read, "Cut to $length bytes")
            }
        }
    }

    @Test
    fun `random bytes are no image`() {
        val random = Random(7)
        repeat(5_000) { round ->
            assertNull(ImageInfo.of(random.nextBytes(random.nextInt(0, 256))), "Round $round")
        }
    }

    @Test
    fun `damaged images are read within bounds or refused`() {
        val mutator = Mutator(seed = 11)
        val images = validImages()
        repeat(20_000) { round ->
            val info = ImageInfo.of(mutator.mutate(images[round % images.size].bytes)) ?: return@repeat
            assertTrue(info.width in 1..ImageInfo.MAX_SIDE && info.height in 1..ImageInfo.MAX_SIDE, "Round $round: $info")
        }
    }

    private fun validImages() = listOf(
        TestImages.png(640, 480),
        TestImages.gif(320, 200),
        TestImages.webpLossy(1024, 768),
        TestImages.webpLossless(500, 300),
        TestImages.webpExtended(2000, 1000),
        TestImages.jpeg(800, 600, frameMarker = 0xC2),
        TestImages.jpeg(800, 600, segments = exif(orientation = 8, isLittleEndian = true)),
        TestImages.jpeg(800, 600, segments = exif(orientation = 6, isLittleEndian = false) + bytes(0xFF, 0xFF)),
    )
}
