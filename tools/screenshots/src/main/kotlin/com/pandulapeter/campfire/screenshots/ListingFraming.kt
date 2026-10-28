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
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File

/**
 * Frames the listing's renders in [renders] (`<device folder>/<language>/<shot id>.png`) into [output]: every listing
 * image of every store, in every language whose texts are in [texts] (`documentation/store-listings/screenshot-texts`),
 * as `<store>/<language>/<device>-<n>`, numbered by the shot's place in the listing. Returns how many images were
 * missing their render or their text.
 */
internal suspend fun frameListing(renders: File, texts: File, output: File): Int {
    val byLanguage = texts.listFiles { file -> file.extension == "md" }.orEmpty().associate { it.nameWithoutExtension to readListingTexts(it) }
    val devices = Device.entries.filter { it.isInListing }
    var missing = 0
    devices.forEach { device ->
        val canvas = checkNotNull(device.listingCanvas)
        byLanguage.forEach { (language, byShot) ->
            shots.forEachIndexed { index, shot ->
                val source = File(renders, "${device.folder}/$language/${shot.id}.png")
                val text = byShot[shot.id]
                if (!source.isFile || text == null) {
                    missing++
                    println("missing: ${source.relativeTo(renders)}${if (text == null) " (no text)" else ""}")
                    return@forEach
                }
                val format = if (canvas.store == "microsoft-store") EncodedImageFormat.PNG else EncodedImageFormat.JPEG
                val file = File(output, "${canvas.store}/$language/${canvas.name}-${index + 1}.${format.extension}")
                file.parentFile.mkdirs()
                file.writeBytes(frame(canvas = canvas, shot = shot, text = text, screen = Image.makeFromEncoded(source.readBytes()), format = format))
            }
            println("framed ${device.folder} / $language")
        }
    }
    return missing
}

/** One listing image of [shot] on [canvas], with [text]'s headline and chips around [screen], encoded as [format]. */
private suspend fun frame(canvas: ListingCanvas, shot: Shot, text: ListingText, screen: Image, format: EncodedImageFormat): ByteArray {
    val styles = listingChipStyles[shot.id].orEmpty()
    require(styles.size == text.chips.size) { "${shot.id} has ${text.chips.size} chip texts for ${styles.size} chips." }
    val bitmap = screen.toComposeImageBitmap()
    val scene = ImageComposeScene(
        width = canvas.widthPx,
        height = canvas.heightPx,
        density = Density(1f),
        coroutineContext = Dispatchers.Main,
    ) {
        ListingImage(
            headline = if (canvas.isLandscape) text.headline.replace('\n', ' ') else text.headline,
            frame = canvas.frame,
            screen = bitmap,
            isLight = shot.uiMode == UserPreferences.UiMode.LIGHT,
            chips = text.chips.zip(styles) { chip, style -> ListingChip(chip, style.icon, style.accent, style.corner) },
        )
    }
    // The bundled fonts and icons load after the first frame.
    var time = 0L
    repeat(LOADING_FRAMES) {
        scene.render(time)
        time += FRAME_MILLIS * 1_000_000
        delay(FRAME_MILLIS)
    }
    return try {
        checkNotNull(scene.render(time).encodeToData(format, STORE_JPEG_QUALITY)).bytes
    } finally {
        scene.close()
    }
}

private val EncodedImageFormat.extension get() = if (this == EncodedImageFormat.PNG) "png" else "jpg"

private const val LOADING_FRAMES = 30

/** Enough that the gradient behind a frame shows no banding, and a screen's small text no ringing. */
internal const val STORE_JPEG_QUALITY = 92
