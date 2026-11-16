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
import com.pandulapeter.campfire.data.formats.image.ImageFormats
import com.pandulapeter.campfire.data.model.image.ImageIntake
import com.pandulapeter.campfire.data.model.image.ImageRefusal
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.presentation.ui.platform.PictureAttempt
import com.pandulapeter.campfire.presentation.ui.platform.PictureEncoding

/**
 * Makes a picture the user adds themselves (the editor's Image button, a picture opened, shared, or dropped alone, or a
 * missing picture's Add image) one the library takes, where the library would refuse it only for its size or for being a
 * HEIC or AVIF photo: such a picture is re-encoded on the device, and the copy is what the ordinary intake is handed, so
 * that its name is its own content's. A picture the library takes as it is is never touched, and nothing arriving by an
 * archive or by sync comes here.
 *
 * @param examine What the library would make of a picture, writing nothing (`ExamineImageUseCase`).
 * @param reencode The platform's re-encoder (`reencodePicture`), replaced by tests.
 */
internal class PictureFitter(
    private val examine: suspend (bytes: ByteArray) -> ImageIntake,
    private val reencode: suspend (bytes: ByteArray, attempts: List<PictureAttempt>, maxBytes: Long) -> ByteArray?,
) {

    suspend fun fit(bytes: ByteArray): PictureFit {
        val reason = (examine(bytes) as? ImageIntake.Refused)?.reason ?: return PictureFit.Kept(bytes)
        if (!isReencodable(bytes, reason)) return PictureFit.Refused(reason)
        val copy = reencode(bytes, pictureAttemptsFor(ImageFormats.signatureFormatOf(bytes)), ImportLimits.MAX_IMAGE_FILE_SIZE)
        return when {
            copy == null || examine(copy) !is ImageIntake.Accepted -> PictureFit.Refused(reason)
            reason == ImageRefusal.UNSUPPORTED_FORMAT -> PictureFit.Converted(copy)
            else -> PictureFit.Shrunk(copy)
        }
    }

    /**
     * Whether a copy is worth trying: a picture too large in pixels, one too large in bytes that is a picture at all by its
     * signature (an SVG or a large document named as a picture is not), and a HEIC or AVIF photo. An SVG is never drawn
     * into pixels, since its size is whatever it is drawn at.
     */
    private fun isReencodable(bytes: ByteArray, reason: ImageRefusal) = when (reason) {
        ImageRefusal.TOO_MANY_PIXELS -> true
        ImageRefusal.TOO_LARGE -> ImageFormats.signatureFormatOf(bytes) != null || ImageFormats.isHeif(bytes)
        ImageRefusal.UNSUPPORTED_FORMAT -> ImageFormats.isHeif(bytes)
        ImageRefusal.NOT_AN_IMAGE -> false
    }
}

/**
 * The encodings a picture of [source] format is tried in, in order, each tried only where the one before came out over
 * the limit: a PNG first where the picture came as a PNG, GIF, or WebP, which may hold transparency or line art, then a
 * JPEG (a photo's natural format, the PNG's transparency flattened onto white), then smaller and smaller JPEGs. Bounded,
 * so a picture no attempt fits stays refused rather than shrinking to a thumbnail.
 */
internal fun pictureAttemptsFor(source: ImageFormat?): List<PictureAttempt> {
    val jpegs = listOf(
        PictureAttempt(encoding = PictureEncoding.JPEG, quality = JPEG_QUALITY, scale = 1f),
        PictureAttempt(encoding = PictureEncoding.JPEG, quality = JPEG_QUALITY, scale = SMALLER),
        PictureAttempt(encoding = PictureEncoding.JPEG, quality = LOWER_JPEG_QUALITY, scale = SMALLER),
        PictureAttempt(encoding = PictureEncoding.JPEG, quality = LOWER_JPEG_QUALITY, scale = SMALLER * SMALLER),
    )
    val isLossless = source == ImageFormat.PNG || source == ImageFormat.GIF || source == ImageFormat.WEBP
    return if (isLossless) listOf(PictureAttempt(encoding = PictureEncoding.PNG, quality = 1f, scale = 1f)) + jpegs else jpegs
}

private const val JPEG_QUALITY = 0.85f
private const val LOWER_JPEG_QUALITY = 0.8f
private const val SMALLER = 0.75f
