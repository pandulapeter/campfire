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

import com.pandulapeter.campfire.data.formats.image.ImageFormat
import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.image.ImageRefusal
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.presentation.ui.messages.Message
import com.pandulapeter.campfire.presentation.ui.platform.PictureAttempt
import com.pandulapeter.campfire.presentation.ui.platform.PictureEncoding
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PictureFitterTest {

    /** What the library is told about each picture, by its bytes. */
    private val intakes = mutableMapOf<List<Byte>, ImageIntake>()

    /** What the re-encoder was handed, and what it answers. */
    private val reencoded = mutableListOf<Pair<ByteArray, List<PictureAttempt>>>()
    private var copy: ByteArray? = COPY

    private val fitter = PictureFitter(
        examine = { bytes -> intakes[bytes.toList()] ?: ImageIntake.Refused(ImageRefusal.NOT_AN_IMAGE) },
        reencode = { bytes, attempts, maxBytes ->
            assertEquals(ImportLimits.MAX_IMAGE_FILE_SIZE, maxBytes)
            reencoded += bytes to attempts
            copy
        },
        work = PictureWork(),
    )

    init {
        intakes[COPY.toList()] = ImageIntake.Accepted("copy.jpg")
    }

    @Test
    fun `a picture the library takes is kept as it is never re-encoded`() = runTest {
        intakes[JPEG.toList()] = ImageIntake.Accepted("photo.jpg")
        val fit = assertIs<PictureFit.Kept>(fitter.fit(JPEG))
        assertSame(JPEG, fit.bytes)
        assertNull(fit.notice)
        assertTrue(reencoded.isEmpty())
    }

    @Test
    fun `a picture too large in bytes or in pixels is shrunk - said by its notice`() = runTest {
        for (reason in listOf(ImageRefusal.TOO_LARGE, ImageRefusal.TOO_MANY_PIXELS)) {
            intakes[JPEG.toList()] = ImageIntake.Refused(reason)
            val fit = assertIs<PictureFit.Shrunk>(fitter.fit(JPEG), reason.name)
            assertContentEquals(COPY, fit.bytes)
            assertEquals(Message.ImageShrunk, fit.notice)
        }
        assertEquals(PictureEncoding.JPEG, reencoded.first().second.first().encoding)
    }

    @Test
    fun `a HEIC photo is converted - said by its notice`() = runTest {
        intakes[HEIC.toList()] = ImageIntake.Refused(ImageRefusal.UNSUPPORTED_FORMAT)
        val fit = assertIs<PictureFit.Converted>(fitter.fit(HEIC))
        assertContentEquals(COPY, fit.bytes)
        assertEquals(Message.ImageConverted, fit.notice)
    }

    @Test
    fun `a picture the platform cannot re-encode keeps its own refusal`() = runTest {
        copy = null
        intakes[HEIC.toList()] = ImageIntake.Refused(ImageRefusal.UNSUPPORTED_FORMAT)
        intakes[JPEG.toList()] = ImageIntake.Refused(ImageRefusal.TOO_MANY_PIXELS)
        assertEquals(PictureFit.Refused(ImageRefusal.UNSUPPORTED_FORMAT), fitter.fit(HEIC))
        assertEquals(PictureFit.Refused(ImageRefusal.TOO_MANY_PIXELS), fitter.fit(JPEG))
    }

    @Test
    fun `a copy the library would refuse too keeps the picture's own refusal`() = runTest {
        copy = byteArrayOf(9)
        intakes[byteArrayOf(9).toList()] = ImageIntake.Refused(ImageRefusal.TOO_LARGE)
        intakes[HEIC.toList()] = ImageIntake.Refused(ImageRefusal.UNSUPPORTED_FORMAT)
        assertEquals(PictureFit.Refused(ImageRefusal.UNSUPPORTED_FORMAT), fitter.fit(HEIC))
    }

    @Test
    fun `an SVG is never re-encoded - however it is refused`() = runTest {
        for (reason in listOf(ImageRefusal.UNSUPPORTED_FORMAT, ImageRefusal.TOO_LARGE)) {
            intakes[SVG.toList()] = ImageIntake.Refused(reason)
            assertEquals(PictureFit.Refused(reason), fitter.fit(SVG))
        }
        assertTrue(reencoded.isEmpty())
    }

    @Test
    fun `a file that is no picture is never re-encoded`() = runTest {
        val text = "{title: A}".encodeToByteArray()
        assertEquals(PictureFit.Refused(ImageRefusal.NOT_AN_IMAGE), fitter.fit(text))
        intakes[text.toList()] = ImageIntake.Refused(ImageRefusal.TOO_LARGE)
        assertEquals(PictureFit.Refused(ImageRefusal.TOO_LARGE), fitter.fit(text))
        assertTrue(reencoded.isEmpty())
    }

    @Test
    fun `a PNG or GIF or WebP is tried as a PNG first then as ever smaller JPEGs`() {
        for (format in listOf(ImageFormat.PNG, ImageFormat.GIF, ImageFormat.WEBP)) {
            val attempts = pictureAttemptsFor(format)
            assertEquals(PictureEncoding.PNG, attempts.first().encoding, format.name)
            assertEquals(pictureAttemptsFor(ImageFormat.JPEG), attempts.drop(1), format.name)
        }
    }

    @Test
    fun `a photo is tried as JPEGs - each smaller or lower in quality than the one before`() {
        for (attempts in listOf(pictureAttemptsFor(ImageFormat.JPEG), pictureAttemptsFor(null))) {
            assertTrue(attempts.all { it.encoding == PictureEncoding.JPEG })
            assertEquals(PictureAttempt(encoding = PictureEncoding.JPEG, quality = 0.85f, scale = 1f), attempts.first())
            attempts.zipWithNext { previous, next ->
                assertTrue(next.scale < previous.scale || next.quality < previous.quality, "$previous, then $next")
            }
        }
    }

    private companion object {
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val HEIC = byteArrayOf(0, 0, 0, 16) + "ftypheic".encodeToByteArray() + byteArrayOf(0, 0, 0, 0) + ByteArray(16)
        val SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".encodeToByteArray()
        val COPY = byteArrayOf(1, 2, 3)
    }
}
