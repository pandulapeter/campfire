/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.ui.dom.domEventOrNull
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.unit.IntSize
import org.w3c.dom.events.WheelEvent

internal actual val hasNativeOverscroll = false

/** Same pixel/line/page units as Compose 1.12.1 JsScrollable.web.kt. */
internal actual fun PointerEvent.overscrollWheelDelta(bounds: IntSize, density: Float): Offset {
    val delta = changes.fold(Offset.Zero) { total, change -> total + change.scrollDelta }
    return when ((domEventOrNull as? WheelEvent)?.deltaMode) {
        WheelEvent.DOM_DELTA_LINE -> delta * (-16f * density)
        WheelEvent.DOM_DELTA_PAGE -> Offset(-delta.x * bounds.width, -delta.y * bounds.height)
        else -> delta * -density
    }
}
