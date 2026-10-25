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

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import kotlin.math.roundToInt

/**
 * The screen of [device], drawn from two scenes: [window], the app's, as large as its window and where the screen has it,
 * and one of the whole screen for [chrome], laid over it. The app's scene is its window rather than the screen because
 * a dialog (every bottom sheet) is a layer as large as the scene it is in: in a scene of the whole screen it would reach
 * over a taskbar, a shelf, or a menu bar, which a window keeps clear of. Both scenes run on [Dispatchers.Main].
 */
internal class DeviceScreen(
    device: Device,
    app: @Composable () -> Unit,
    chrome: @Composable () -> Unit,
) : AutoCloseable {
    /** Where the window starts, in the screen's pixels, which the window's own are moved by. */
    val windowTop = (device.chrome.windowTop.value * device.density).roundToInt()
    val window = ImageComposeScene(
        width = device.widthPx,
        height = device.heightPx - windowTop - (device.chrome.windowBottom.value * device.density).roundToInt(),
        density = Density(device.density),
        coroutineContext = Dispatchers.Main,
        content = app,
    )
    private val chromeScene = ImageComposeScene(
        width = device.widthPx,
        height = device.heightPx,
        density = Density(device.density),
        coroutineContext = Dispatchers.Main,
        content = chrome,
    )
    private val surface = Surface.makeRasterN32Premul(device.widthPx, device.heightPx)

    /** Draws both scenes at [nanoTime] of their clock, and the screen they make together. */
    fun render(nanoTime: Long): Image {
        val windowImage = window.render(nanoTime)
        val chromeImage = chromeScene.render(nanoTime)
        surface.canvas.clear(Color.TRANSPARENT)
        surface.canvas.drawImage(windowImage, 0f, windowTop.toFloat())
        surface.canvas.drawImage(chromeImage, 0f, 0f)
        return surface.makeImageSnapshot()
    }

    override fun close() {
        window.close()
        chromeScene.close()
        surface.close()
    }
}
