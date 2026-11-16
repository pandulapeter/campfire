/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.presentation.ui.platform

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.khronos.webgl.toInt8Array
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * Done by the browser, outside the Wasm heap, which a decoded photo would crowd: `createImageBitmap` decodes the picture
 * upright (HEIC only where the browser reads it, which is Safari), and each attempt is drawn into a canvas of its size and
 * written by `convertToBlob`, which keeps no metadata. A canvas is sRGB. Its area stays within iOS Safari's largest
 * canvas, since no attempt is over [MAX_REENCODED_PIXELS].
 */
internal actual suspend fun reencodePicture(bytes: ByteArray, attempts: List<PictureAttempt>, maxBytes: Long): ByteArray? {
    val bitmap = try {
        decodePicture(bytes.toInt8Array()).await<JsAny?>()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Throwable) {
        null
    } ?: return null
    try {
        val width = bitmapWidth(bitmap)
        val height = bitmapHeight(bitmap)
        for (attempt in attempts) {
            val size = reencodedSizeOf(width, height, attempt.scale)
            val encoded = encodePicture(
                bitmap = bitmap,
                width = size.width,
                height = size.height,
                type = attempt.encoding.mimeType,
                quality = attempt.quality.toDouble(),
                isOpaque = attempt.encoding == PictureEncoding.JPEG,
                maxBytes = maxBytes.toDouble(),
            ).await<Int8Array?>() ?: return null
            // An empty array is the browser's answer for a copy over the limit: the next attempt is smaller.
            if (encoded.length > 0) return encoded.toByteArray()
        }
        return null
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Throwable) {
        println("Could not re-encode a picture: ${exception::class.simpleName}")
        return null
    } finally {
        closeBitmap(bitmap)
    }
}

/**
 * Resolves with the picture's `ImageBitmap`, turned by its EXIF orientation (asked for by name, and the browser's default
 * where it does not know the option), or with null where the browser cannot decode it.
 */
private fun decodePicture(bytes: Int8Array): Promise<JsAny?> = js(
    """(async function () {
        try {
            var blob = new Blob([bytes]);
            try {
                return await createImageBitmap(blob, { imageOrientation: 'from-image' });
            } catch (error) {
                if (error && error.name === 'TypeError') return await createImageBitmap(blob);
                throw error;
            }
        } catch (error) {
            return null;
        }
    })()"""
)

private fun bitmapWidth(bitmap: JsAny): Int = js("bitmap.width")

private fun bitmapHeight(bitmap: JsAny): Int = js("bitmap.height")

private fun closeBitmap(bitmap: JsAny): Unit = js("bitmap.close()")

/**
 * Resolves with [bitmap] drawn at [width] by [height] and written as [type], an empty array where that came out over
 * [maxBytes] (so a copy too large never crosses into the Wasm heap), or null where the browser could not write it: no
 * canvas, or a type it does not write (it answers a PNG instead). An opaque picture is drawn over white, since a JPEG has
 * no transparency. The canvas is emptied afterwards, since Safari keeps a canvas's memory until it is.
 */
private fun encodePicture(
    bitmap: JsAny,
    width: Int,
    height: Int,
    type: String,
    quality: Double,
    isOpaque: Boolean,
    maxBytes: Double,
): Promise<Int8Array?> = js(
    """(async function () {
        var canvas = null;
        try {
            if (typeof OffscreenCanvas === 'function') {
                canvas = new OffscreenCanvas(width, height);
            } else {
                canvas = document.createElement('canvas');
                canvas.width = width;
                canvas.height = height;
            }
            var context = canvas.getContext('2d');
            if (!context) return null;
            if (isOpaque) {
                context.fillStyle = '#ffffff';
                context.fillRect(0, 0, width, height);
            }
            context.imageSmoothingEnabled = true;
            context.imageSmoothingQuality = 'high';
            context.drawImage(bitmap, 0, 0, width, height);
            var blob = typeof canvas.convertToBlob === 'function'
                ? await canvas.convertToBlob({ type: type, quality: quality })
                : await new Promise(function (resolve) { canvas.toBlob(resolve, type, quality); });
            if (!blob || blob.type !== type) return null;
            if (blob.size > maxBytes) return new Int8Array(0);
            return new Int8Array(await blob.arrayBuffer());
        } catch (error) {
            return null;
        } finally {
            if (canvas) {
                canvas.width = 0;
                canvas.height = 0;
            }
        }
    })()"""
)
