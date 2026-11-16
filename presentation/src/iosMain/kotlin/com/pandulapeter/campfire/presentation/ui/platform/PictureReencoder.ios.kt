/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalForeignApi::class)

package com.pandulapeter.campfire.presentation.ui.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataCreateMutable
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateWithName
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetInterpolationQuality
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGColorSpaceSRGB
import platform.CoreGraphics.kCGInterpolationHigh
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSNumber
import platform.ImageIO.CGImageDestinationAddImage
import platform.ImageIO.CGImageDestinationCreateWithData
import platform.ImageIO.CGImageDestinationFinalize
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.kCGImageDestinationLossyCompressionQuality
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceShouldCacheImmediately
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.posix.memcpy

/**
 * Decoded by ImageIO's thumbnail maker, which decodes straight to the size asked (sampling a JPEG or HEIC in the decoder,
 * never holding the whole picture) and turns it upright by its orientation. Each attempt is drawn into an sRGB bitmap of
 * its size (over white for a JPEG) and written by a `CGImageDestination` from that bitmap alone, so no metadata of the
 * original is carried over.
 */
internal actual suspend fun reencodePicture(bytes: ByteArray, attempts: List<PictureAttempt>, maxBytes: Long): ByteArray? =
    withContext(Dispatchers.Default) {
        try {
            memScoped { reencode(bytes, attempts, maxBytes) }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            println("Could not re-encode a picture: ${exception::class.simpleName}")
            null
        }
    }

private fun MemScope.reencode(bytes: ByteArray, attempts: List<PictureAttempt>, maxBytes: Long): ByteArray? {
    if (bytes.isEmpty()) return null
    val data = bytes.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.convert()) } ?: return null
    defer { CFRelease(data) }
    val source = CGImageSourceCreateWithData(data, null) ?: return null
    defer { CFRelease(source) }
    val properties = CFBridgingRelease(CGImageSourceCopyPropertiesAtIndex(source, 0u, null)) as? Map<*, *> ?: return null
    val storedWidth = properties[PIXEL_WIDTH].toIntOrNull() ?: return null
    val storedHeight = properties[PIXEL_HEIGHT].toIntOrNull() ?: return null
    // EXIF orientations 5 to 8 turn the picture a quarter, so it is shown with its sides swapped.
    val isTurned = (properties[ORIENTATION].toIntOrNull() ?: 1) in 5..8
    val width = if (isTurned) storedHeight else storedWidth
    val height = if (isTurned) storedWidth else storedHeight
    val target = reencodedSizeOf(width, height)
    val options = dictionary(
        kCGImageSourceCreateThumbnailFromImageAlways to kCFBooleanTrue,
        kCGImageSourceThumbnailMaxPixelSize to retained(NSNumber(int = maxOf(target.width, target.height))),
        kCGImageSourceCreateThumbnailWithTransform to kCFBooleanTrue,
        kCGImageSourceShouldCacheImmediately to kCFBooleanTrue,
    )
    val decoded = CGImageSourceCreateThumbnailAtIndex(source, 0u, options) ?: return null
    defer { CGImageRelease(decoded) }
    // The thumbnail's long side is the one asked for, its short side rounded by ImageIO; each attempt is drawn at the
    // exact size of the shared rule, which may differ from it by a pixel.
    if (CGImageGetWidth(decoded) == 0uL || CGImageGetHeight(decoded) == 0uL) return null
    for (attempt in attempts) {
        val size = reencodedSizeOf(width, height, attempt.scale)
        val drawn = drawn(decoded, size.width, size.height, isOpaque = attempt.encoding == PictureEncoding.JPEG) ?: return null
        val encoded = try {
            encode(drawn, attempt) ?: return null
        } finally {
            CGImageRelease(drawn)
        }
        if (encoded.size <= maxBytes) return encoded
    }
    return null
}

private fun Any?.toIntOrNull() = when (this) {
    is Number -> toInt()
    is NSNumber -> intValue
    else -> null
}

private fun MemScope.drawn(image: CGImageRef, width: Int, height: Int, isOpaque: Boolean): CGImageRef? {
    val colorSpace = CGColorSpaceCreateWithName(kCGColorSpaceSRGB) ?: return null
    defer { CGColorSpaceRelease(colorSpace) }
    val alpha = if (isOpaque) CGImageAlphaInfo.kCGImageAlphaNoneSkipLast else CGImageAlphaInfo.kCGImageAlphaPremultipliedLast
    val context = CGBitmapContextCreate(null, width.convert(), height.convert(), 8u, 0u, colorSpace, alpha.value) ?: return null
    try {
        val rect = CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())
        if (isOpaque) {
            CGContextSetRGBFillColor(context, 1.0, 1.0, 1.0, 1.0)
            CGContextFillRect(context, rect)
        }
        CGContextSetInterpolationQuality(context, kCGInterpolationHigh)
        CGContextDrawImage(context, rect, image)
        return CGBitmapContextCreateImage(context)
    } finally {
        CGContextRelease(context)
    }
}

private fun MemScope.encode(image: CGImageRef, attempt: PictureAttempt): ByteArray? {
    val output = CFDataCreateMutable(null, 0) ?: return null
    defer { CFRelease(output) }
    val type = retained(if (attempt.encoding == PictureEncoding.JPEG) "public.jpeg" else "public.png")
    val destination = CGImageDestinationCreateWithData(output, type?.reinterpret(), 1u, null) ?: return null
    defer { CFRelease(destination) }
    val properties = dictionary(kCGImageDestinationLossyCompressionQuality to retained(NSNumber(double = attempt.quality.toDouble())))
    CGImageDestinationAddImage(destination, image, properties)
    if (!CGImageDestinationFinalize(destination)) return null
    val length = CFDataGetLength(output).toInt()
    val pointer = CFDataGetBytePtr(output) ?: return null
    return ByteArray(length).also { encoded ->
        if (length > 0) encoded.usePinned { memcpy(it.addressOf(0), pointer, length.convert()) }
    }
}

/** A Core Foundation dictionary that lives as long as the scope, retaining what is put into it. */
private fun MemScope.dictionary(vararg entries: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
    val dictionary = CFDictionaryCreateMutable(null, entries.size.convert(), kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    entries.forEach { (key, value) -> CFDictionaryAddValue(dictionary, key, value) }
    defer { CFRelease(dictionary) }
    return dictionary
}

/** A Kotlin object handed to Core Foundation for as long as the scope lives. */
private fun MemScope.retained(value: Any?): CFTypeRef? = CFBridgingRetain(value).also { defer { CFRelease(it) } }

/** The keys of `kCGImagePropertyPixelWidth`, `kCGImagePropertyPixelHeight`, and `kCGImagePropertyOrientation`. */
private const val PIXEL_WIDTH = "PixelWidth"
private const val PIXEL_HEIGHT = "PixelHeight"
private const val ORIENTATION = "Orientation"
