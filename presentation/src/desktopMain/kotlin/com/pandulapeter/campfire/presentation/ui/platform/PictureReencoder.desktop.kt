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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedOrigin
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOException
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageInputStream
import javax.imageio.stream.MemoryCacheImageOutputStream

/**
 * Decoded by `javax.imageio` with source subsampling, which reads only every n-th pixel of each n-th row, so a photo of a
 * hundred megapixels costs about what its copy does; then scaled to the exact size and turned upright by its EXIF
 * orientation, which Skia's codec reads from the header alone. Where ImageIO cannot read the picture (a WebP, a CMYK
 * JPEG), Skia decodes it whole, as it would to draw it, but only up to [MAX_WHOLE_DECODE_PIXELS]. HEIC is neither's.
 */
internal actual suspend fun reencodePicture(bytes: ByteArray, attempts: List<PictureAttempt>, maxBytes: Long): ByteArray? =
    withContext(Dispatchers.Default) {
        try {
            val picture = decodeWithImageIo(bytes) ?: decodeWithSkia(bytes) ?: return@withContext null
            attempts.firstNotNullOfOrNull { attempt ->
                val size = reencodedSizeOf(picture.width, picture.height, attempt.scale)
                encode(scaled(picture.image, size.width, size.height), attempt).takeIf { it.size <= maxBytes }
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

/** [image] upright at the size the first attempt asks for, and the size of the picture it came from, upright too. */
private class DecodedPicture(val image: BufferedImage, val width: Int, val height: Int)

/**
 * The most pixels a picture ImageIO cannot read is decoded whole for: four bytes each, about 256 MB at most for the moment
 * it is held, which a desktop's heap has room for.
 */
private const val MAX_WHOLE_DECODE_PIXELS = 64_000_000L

private fun decodeWithImageIo(bytes: ByteArray): DecodedPicture? = MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
    val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return null
    try {
        reader.setInput(input, true, true)
        val storedWidth = reader.getWidth(0)
        val storedHeight = reader.getHeight(0)
        val origin = encodedOriginOf(bytes)
        val isTurned = origin.isTurned
        val upright = reencodedSizeOf(width = if (isTurned) storedHeight else storedWidth, height = if (isTurned) storedWidth else storedHeight)
        val targetWidth = if (isTurned) upright.height else upright.width
        val targetHeight = if (isTurned) upright.width else upright.height
        // Never subsampled below the target, so the scaling after it only ever shrinks, and by less than half.
        val subsampling = maxOf(1, minOf(storedWidth / targetWidth, storedHeight / targetHeight))
        val parameters = reader.defaultReadParam.apply { setSourceSubsampling(subsampling, subsampling, 0, 0) }
        val stored = try {
            reader.read(0, parameters)
        } catch (_: IIOException) {
            // A JPEG in CMYK or YCCK, which ImageIO does not convert: Skia does.
            return null
        }
        DecodedPicture(
            image = turnedUpright(scaled(stored, targetWidth, targetHeight), origin),
            width = if (isTurned) storedHeight else storedWidth,
            height = if (isTurned) storedWidth else storedHeight,
        )
    } finally {
        reader.dispose()
    }
}

private fun encodedOriginOf(bytes: ByteArray): EncodedOrigin = try {
    Data.makeFromBytes(bytes).use { data -> Codec.makeFromData(data).use { it.encodedOrigin } }
} catch (_: Exception) {
    EncodedOrigin.TOP_LEFT
}

private val EncodedOrigin.isTurned get() = ordinal >= EncodedOrigin.LEFT_TOP.ordinal

/** Skia's image applies the EXIF orientation itself, its size included, as it does when a song draws the picture. */
private fun decodeWithSkia(bytes: ByteArray): DecodedPicture? {
    val image = try {
        Image.makeFromEncoded(bytes)
    } catch (_: Exception) {
        return null
    }
    return image.use {
        if (image.width.toLong() * image.height > MAX_WHOLE_DECODE_PIXELS) return null
        val size = reencodedSizeOf(image.width, image.height)
        Bitmap().use { bitmap ->
            bitmap.allocPixels(ImageInfo(size.width, size.height, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
            Canvas(bitmap).use { canvas ->
                val source = Rect.makeWH(image.width.toFloat(), image.height.toFloat())
                canvas.drawImageRect(image, source, Rect.makeWH(size.width.toFloat(), size.height.toFloat()), SamplingMode.MITCHELL, null, true)
            }
            val pixels = bitmap.readPixels(ImageInfo(size.width, size.height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL), size.width * 4) ?: return null
            val argb = IntArray(size.width * size.height) { index ->
                val offset = index * 4
                (pixels[offset + 3].toInt() and 0xFF shl 24) or (pixels[offset + 2].toInt() and 0xFF shl 16) or
                    (pixels[offset + 1].toInt() and 0xFF shl 8) or (pixels[offset].toInt() and 0xFF)
            }
            val result = BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB)
            result.setRGB(0, 0, size.width, size.height, argb, 0, size.width)
            DecodedPicture(image = result, width = image.width, height = image.height)
        }
    }
}

/**
 * [source] at [width] by [height], halved with bilinear filtering while it is more than twice as large, so that every
 * source pixel is averaged into the result rather than skipped, which keeps a scanned chart's thin lines.
 */
private fun scaled(source: BufferedImage, width: Int, height: Int): BufferedImage {
    var current = source
    while (current.width / 2 >= width && current.height / 2 >= height) current = drawn(current, current.width / 2, current.height / 2)
    return if (current.width == width && current.height == height) current else drawn(current, width, height)
}

private fun drawn(source: BufferedImage, width: Int, height: Int): BufferedImage {
    val result = BufferedImage(width, height, if (source.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
    val graphics = result.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.drawImage(source, 0, 0, width, height, null)
    } finally {
        graphics.dispose()
    }
    return result
}

/** [image] as it is shown: moved pixel by pixel, since a quarter turn or a mirror loses nothing that way. */
private fun turnedUpright(image: BufferedImage, origin: EncodedOrigin): BufferedImage {
    if (origin == EncodedOrigin.TOP_LEFT || origin == EncodedOrigin.UNUSED) return image
    val width = image.width
    val height = image.height
    val pixels = image.getRGB(0, 0, width, height, null, 0, width)
    val uprightWidth = if (origin.isTurned) height else width
    val upright = IntArray(pixels.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val index = when (origin) {
                EncodedOrigin.TOP_RIGHT -> y * uprightWidth + (width - 1 - x)
                EncodedOrigin.BOTTOM_RIGHT -> (height - 1 - y) * uprightWidth + (width - 1 - x)
                EncodedOrigin.BOTTOM_LEFT -> (height - 1 - y) * uprightWidth + x
                EncodedOrigin.LEFT_TOP -> x * uprightWidth + y
                EncodedOrigin.RIGHT_TOP -> x * uprightWidth + (height - 1 - y)
                EncodedOrigin.RIGHT_BOTTOM -> (width - 1 - x) * uprightWidth + (height - 1 - y)
                EncodedOrigin.LEFT_BOTTOM -> (width - 1 - x) * uprightWidth + y
                EncodedOrigin.TOP_LEFT, EncodedOrigin.UNUSED -> y * uprightWidth + x
            }
            upright[index] = pixels[y * width + x]
        }
    }
    val result = BufferedImage(uprightWidth, pixels.size / uprightWidth, if (image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
    result.setRGB(0, 0, uprightWidth, result.height, upright, 0, uprightWidth)
    return result
}

/** Written with no metadata at all: ImageIO's writers add none of their own beyond a JFIF header. */
private fun encode(image: BufferedImage, attempt: PictureAttempt): ByteArray {
    val output = ByteArrayOutputStream()
    val writer = ImageIO.getImageWritersByMIMEType(attempt.encoding.mimeType).next()
    try {
        MemoryCacheImageOutputStream(output).use { stream ->
            writer.output = stream
            val parameters = writer.defaultWriteParam
            val written = if (attempt.encoding == PictureEncoding.JPEG) {
                parameters.compressionMode = ImageWriteParam.MODE_EXPLICIT
                parameters.compressionQuality = attempt.quality
                flattened(image)
            } else {
                image
            }
            writer.write(null, IIOImage(written, null, null), parameters)
        }
    } finally {
        writer.dispose()
    }
    return output.toByteArray()
}

/** [image] without transparency, drawn over white, as a JPEG needs it: ImageIO writes an alpha channel as a fourth colour. */
private fun flattened(image: BufferedImage): BufferedImage {
    if (image.type == BufferedImage.TYPE_INT_RGB) return image
    val result = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
    val graphics = result.createGraphics()
    try {
        graphics.drawImage(image, 0, 0, Color.WHITE, null)
    } finally {
        graphics.dispose()
    }
    return result
}
