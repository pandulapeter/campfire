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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.unit.IntSize
import java.awt.event.MouseWheelEvent
import kotlin.math.sqrt

internal actual val hasNativeOverscroll = false

/** Same wheel units as Compose 1.12.1 DesktopScrollable.desktop.kt, including OS and page scrolling. */
internal actual fun PointerEvent.overscrollWheelDelta(bounds: IntSize, density: Float): Offset {
    val delta = changes.fold(Offset.Zero) { total, change -> total + change.scrollDelta }
    val wheel = awtEventOrNull as? MouseWheelEvent
    val scaled = if (wheel?.scrollType == MouseWheelEvent.WHEEL_BLOCK_SCROLL) {
        Offset(delta.x * bounds.width, delta.y * bounds.height)
    } else {
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("mac") || os.contains("darwin") -> delta * (10f * density)
            os.contains("win") -> Offset(delta.x * bounds.width / 20f, delta.y * bounds.height / 20f)
            else -> Offset(delta.x * sqrt(bounds.width.toFloat()), delta.y * sqrt(bounds.height.toFloat()))
        }
    }
    return scaled * -(wheel?.scrollAmount?.toFloat() ?: 1f)
}
