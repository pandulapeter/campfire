/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File

/**
 * Frames the listing shots that a run left in [renders] (one folder per [Device]) into listing images in
 * [output], for judging the frames before the whole listing is made with them. A headline breaks its lines where its
 * text says on a portrait canvas, the way a translator wants it read, and is one line on a landscape one.
 */
internal suspend fun renderFramePreviews(renders: File, output: File) {
    Device.entries.filter { it.isInListing }.forEach { device ->
        val canvas = previewCanvases.getValue(device)
        listingPreviews.forEach { (id, headline, isLight, chips) ->
            val source = File(renders, "${device.folder}/$id.png").takeIf(File::isFile) ?: return@forEach
            val screen = Image.makeFromEncoded(source.readBytes()).withoutTop(canvas.cropTopPx).toComposeImageBitmap()
            val scene = ImageComposeScene(
                width = canvas.widthPx,
                height = canvas.heightPx,
                density = Density(1f),
                coroutineContext = Dispatchers.Main,
            ) {
                ListingImage(
                    headline = if (canvas.widthPx > canvas.heightPx) headline.replace('\n', ' ') else headline,
                    frame = canvas.frame,
                    screen = screen,
                    isLight = isLight,
                    chips = chips,
                )
            }
            // The bundled fonts load after the first frame.
            var time = 0L
            repeat(FONT_FRAMES) {
                scene.render(time)
                time += FRAME_MILLIS * 1_000_000
                delay(FRAME_MILLIS)
            }
            val file = File(output, "${device.folder}/$id.png").apply { parentFile.mkdirs() }
            file.writeBytes(checkNotNull(scene.render(time).encodeToData(EncodedImageFormat.PNG)).bytes)
            scene.close()
            println("framed $file")
        }
    }
}

/** The image with its top [pixels] cut off: the band a Screenshot Bro frame's camera housing covered. */
private fun Image.withoutTop(pixels: Int): Image {
    if (pixels == 0) return this
    val surface = Surface.makeRasterN32Premul(width, height - pixels)
    surface.canvas.drawImageRect(this, Rect.makeXYWH(0f, pixels.toFloat(), width.toFloat(), (height - pixels).toFloat()), Rect.makeWH(width.toFloat(), (height - pixels).toFloat()))
    return surface.makeImageSnapshot()
}

/** A store's canvas for a device's listing images, in pixels, and the frame its screenshots are drawn in. */
private class PreviewCanvas(val widthPx: Int, val heightPx: Int, val frame: FrameKind, val cropTopPx: Int = 0)

private val previewCanvases = mapOf(
    Device.ANDROID_PHONE to PreviewCanvas(1440, 2560, FrameKind.PHONE),
    Device.ANDROID_SMALL_TABLET to PreviewCanvas(1200, 1920, FrameKind.TABLET),
    Device.ANDROID_LARGE_TABLET to PreviewCanvas(2752, 2064, FrameKind.TABLET),
    Device.CHROMEBOOK to PreviewCanvas(1920, 1080, FrameKind.LAPTOP, cropTopPx = 76),
    Device.IPHONE to PreviewCanvas(1320, 2868, FrameKind.PHONE),
    Device.IPHONE_DUO to PreviewCanvas(2853, 2007, FrameKind.FOLDABLE),
    Device.IPAD to PreviewCanvas(2752, 2064, FrameKind.TABLET),
    Device.MAC to PreviewCanvas(2880, 1800, FrameKind.LAPTOP),
    Device.WINDOWS to PreviewCanvas(2880, 1800, FrameKind.LAPTOP, cropTopPx = 72),
)

private const val FONT_FRAMES = 30
