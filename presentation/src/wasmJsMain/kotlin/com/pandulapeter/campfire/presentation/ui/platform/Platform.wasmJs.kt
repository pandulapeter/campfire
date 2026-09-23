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

import androidx.compose.ui.dom.domEventOrNull
import androidx.compose.ui.input.pointer.PointerEvent
import kotlin.js.ExperimentalWasmJsInterop
import org.w3c.dom.events.WheelEvent

// The page can be open on a phone just as well as on a computer, so the input method decides: with a touchscreen the
// touch treatment is used (a long press and a bottom sheet), without one the desktop treatment is.
internal actual val isDesktopPlatform = !hasTouchScreen()

// The page's own loading screen is over the app until the launch screen has gone, whatever the input.
internal actual val isLaunchScreenWholeStartup = false

// The Origin Private File System is not reachable from outside the page.
internal actual val libraryLocation: LibraryLocation? = null

// The page runs on every operating system and knows none of them well enough to pick a store, and it is served by
// the project itself, with no store's rules to follow.
internal actual val platformStore: Distribution? = null

internal actual fun PointerEvent.verticalWheelNotches(): Float {
    val deltaY = changes.fold(0f) { total, change -> total + change.scrollDelta.y }
    return when ((domEventOrNull as? WheelEvent)?.deltaMode) {
        WheelEvent.DOM_DELTA_LINE -> deltaY / LINES_PER_WHEEL_NOTCH
        WheelEvent.DOM_DELTA_PAGE -> deltaY
        else -> deltaY / PIXELS_PER_WHEEL_NOTCH
    }
}

/**
 * True if the browser reports any touchscreen. `maxTouchPoints` covers every current browser, `ontouchstart` is the
 * fallback for older ones.
 */
private fun hasTouchScreen(): Boolean = js("navigator.maxTouchPoints > 0 || 'ontouchstart' in window")

private const val PIXELS_PER_WHEEL_NOTCH = 100f // What Chrome, Edge and Safari report for one notch at 100 % zoom.
private const val LINES_PER_WHEEL_NOTCH = 3f // What Firefox reports for one notch in line mode.
