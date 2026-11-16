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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Decoded by `ImageDecoder`, told the target size in its header callback so that it decodes straight to it (sampling a
 * JPEG in the decoder, never holding the whole picture), into a software bitmap in sRGB. It applies the EXIF orientation
 * and reads HEIF wherever the device does (AVIF from Android 12). `Bitmap.compress` writes no metadata.
 */
internal actual suspend fun reencodePicture(bytes: ByteArray, attempts: List<PictureAttempt>, maxBytes: Long): ByteArray? =
    withContext(Dispatchers.Default) {
        try {
            var sourceWidth = 0
            var sourceHeight = 0
            val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                sourceWidth = info.size.width
                sourceHeight = info.size.height
                val size = reencodedSizeOf(sourceWidth, sourceHeight)
                decoder.setTargetSize(size.width, size.height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            }
            try {
                val decodedSize = reencodedSizeOf(sourceWidth, sourceHeight)
                // The header's size and the target are in the same space, whether that is the picture as stored or as
                // shown, so a bitmap that came out turned a quarter has the attempts' sides swapped to match it.
                val isTurned = decoded.width != decodedSize.width && decoded.width == decodedSize.height
                attempts.firstNotNullOfOrNull { attempt ->
                    val size = reencodedSizeOf(sourceWidth, sourceHeight, attempt.scale)
                    encode(decoded, if (isTurned) size.height else size.width, if (isTurned) size.width else size.height, attempt)
                        .takeIf { it.size <= maxBytes }
                }
            } finally {
                decoded.recycle()
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (error: OutOfMemoryError) {
            println("Could not re-encode a picture: out of memory")
            null
        } catch (exception: Exception) {
            println("Could not re-encode a picture: ${exception::class.simpleName}")
            null
        }
    }

private fun encode(decoded: Bitmap, width: Int, height: Int, attempt: PictureAttempt): ByteArray {
    val isJpeg = attempt.encoding == PictureEncoding.JPEG
    val scaled = if (decoded.width == width && decoded.height == height) decoded else Bitmap.createScaledBitmap(decoded, width, height, true)
    // A JPEG takes no alpha channel, and Bitmap.compress would leave a transparent pixel black.
    val flattened = if (isJpeg && scaled.hasAlpha()) {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { opaque ->
            Canvas(opaque).apply {
                drawColor(Color.WHITE)
                drawBitmap(scaled, 0f, 0f, null)
            }
        }
    } else {
        scaled
    }
    try {
        val output = ByteArrayOutputStream()
        val format = if (isJpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
        check(flattened.compress(format, (attempt.quality * 100).toInt(), output))
        return output.toByteArray()
    } finally {
        if (flattened !== scaled) flattened.recycle()
        if (scaled !== decoded) scaled.recycle()
    }
}
