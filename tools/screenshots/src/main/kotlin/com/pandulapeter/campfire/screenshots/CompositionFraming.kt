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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.math.roundToInt

/**
 * Draws every [Composition] from the banners' renders in [renders] and writes its files: the store images and the link
 * preview into [output], the README's banners into [readme]. One whose banner is localized is drawn in every language
 * whose texts are in [texts], from that language's renders. Returns how many images were missing a render.
 */
internal suspend fun frameCompositions(renders: File, texts: File, output: File, readme: File): Int {
    val byLanguage = readAllListingTexts(texts)
    var missing = 0
    compositions.forEach { composition ->
        val banner = composition.banner?.let(::banner)
        val languages = if (banner?.isLocalized == true) byLanguage.keys.toList() else listOf(ENGLISH)
        languages.forEach { language ->
            val folder = if (banner?.isLocalized == true) "${banner.folder}/$language" else banner?.folder
            val slots = composition.elements.filterIsInstance<Composition.Screen>().map { it.slot }
            val sources = slots.associateWith { slot -> File(renders, "$folder/${checkNotNull(banner).fileName(slot)}.png") }
            val absent = sources.values.filterNot(File::isFile)
            if (absent.isNotEmpty()) {
                missing++
                absent.forEach { println("missing: ${it.relativeTo(renders)}") }
                return@forEach
            }
            val image = draw(
                composition = composition,
                screens = sources.mapValues { (_, file) -> Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap() },
                lightSlots = slots.filter { banner?.slots?.get(it)?.second?.uiMode == UserPreferences.UiMode.LIGHT }.toSet(),
                texts = byLanguage[language].orEmpty(),
            )
            val partWidth = image.width / composition.outputs.size
            composition.outputs.forEachIndexed { index, target ->
                val file = File(if (target.isReadme) readme else output, target.path.replace("{language}", language))
                file.parentFile.mkdirs()
                file.writeBytes(encodeImage(image = image.part(left = index * partWidth, width = partWidth), extension = file.extension, hasAlpha = target.hasAlpha))
                println("composed $file")
            }
        }
    }
    return missing
}

private suspend fun draw(composition: Composition, screens: Map<Int, ImageBitmap>, lightSlots: Set<Int>, texts: Map<String, ListingText>): Image {
    val scene = ImageComposeScene(
        width = (composition.widthPx * composition.scale).roundToInt(),
        height = (composition.heightPx * composition.scale).roundToInt(),
        density = Density(composition.scale),
        coroutineContext = Dispatchers.Main,
    ) {
        CompositionImage(composition = composition, screens = screens, lightSlots = lightSlots, texts = texts)
    }
    // The bundled fonts and the mark load after the first frame.
    var time = 0L
    repeat(LOADING_FRAMES) {
        scene.render(time)
        time += FRAME_MILLIS * 1_000_000
        delay(FRAME_MILLIS)
    }
    return try {
        scene.render(time)
    } finally {
        scene.close()
    }
}

private fun Image.part(left: Int, width: Int): Image {
    if (left == 0 && width == this.width) return this
    val surface = Surface.makeRasterN32Premul(width, height)
    surface.canvas.drawImageRect(this, Rect.makeXYWH(left.toFloat(), 0f, width.toFloat(), height.toFloat()), Rect.makeWH(width.toFloat(), height.toFloat()))
    return surface.makeImageSnapshot()
}

private const val LOADING_FRAMES = 30

private const val ENGLISH = "en"
