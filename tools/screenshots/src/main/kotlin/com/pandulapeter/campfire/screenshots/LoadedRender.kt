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
import kotlinx.coroutines.delay
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image

/**
 * The scene drawn once what it loads after its first frame (the bundled fonts, the icons, the mark) has arrived: a
 * frame every LOADING_STEP_MILLIS until STABLE_FRAMES in a row come out the same, or LOADING_TIMEOUT_MILLIS pass.
 * Loading takes real time rather than frames, and a frame of a whole store canvas is most of what framing costs, so
 * the frames are few and each is freed as soon as it is compared. The caller closes the image returned.
 */
internal suspend fun ImageComposeScene.renderLoaded(): Image {
    var time = 0L
    var previous: Int? = null
    var unchanged = 0
    val deadline = System.nanoTime() + LOADING_TIMEOUT_MILLIS * 1_000_000
    while (unchanged < STABLE_FRAMES && System.nanoTime() < deadline) {
        delay(LOADING_STEP_MILLIS)
        time += LOADING_STEP_MILLIS * 1_000_000
        val hash = render(time).use { image -> Bitmap.makeFromImage(image).use { checkNotNull(it.readPixels()).contentHashCode() } }
        unchanged = if (hash == previous) unchanged + 1 else 0
        previous = hash
    }
    return render(time)
}

private const val LOADING_STEP_MILLIS = 100L
private const val STABLE_FRAMES = 3
private const val LOADING_TIMEOUT_MILLIS = 5_000L
