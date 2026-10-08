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

package com.pandulapeter.campfire.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.browser.window
import org.w3c.dom.AddEventListenerOptions
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.WheelEvent
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.unsafeCast
import kotlin.math.exp

/**
 * Turns the browser's zoom shortcuts into the text size of the song details screen while it is on top
 * ([CampfireViewModel.isSongTextZoomable]): Ctrl / Cmd + plus, minus and zero step it the way the app bar's buttons
 * do, and a Ctrl + scroll is kept from the browser so that the screen's own gesture handler can have it (see
 * fontScaleGestures). A zoomed page is the whole app grown around a song that stayed the same size, which is not what
 * anybody reading one asked for. Everywhere else the browser zooms the page as it always does, which is how the rest of
 * the app is made larger on the web.
 *
 * Every browser the app runs in reports a pinch on a touchpad as a Ctrl + scroll too, with Ctrl set although nobody
 * holds it and a distance that is the logarithm of the pinch ([PIXELS_PER_PINCH_E]) - which, taken for notches of a
 * wheel, would move the text a few percent for a whole pinch. So a Ctrl + scroll arriving while the Ctrl key is not
 * down is taken for the pinch it is: it goes to [CampfireViewModel.magnifyByTouchpad] and is stopped before the canvas
 * hears of it - which is also how the page on preview of the PDF export screen is zoomed by a pinch, while a real
 * Ctrl + scroll there is left to the browser. Whether the key is down is only known from its own key events, so one held
 * since before the page had the focus reads as not held, and scrolling with it resizes as fast as a pinch does.
 *
 * Window listeners in the capture phase, for the reasons [SearchShortcutEffect] gives. The wheel listener has to be
 * declared not passive, since a wheel listener on the window is passive unless it says otherwise and a passive one
 * cannot prevent anything.
 */
@Composable
internal fun SongTextZoomEffect(viewModel: CampfireViewModel) = DisposableEffect(viewModel) {
    var isControlKeyDown = false
    val keyDownListener: (Event) -> Unit = listener@{ event ->
        val keyEvent = event.unsafeCast<KeyboardEvent>()
        if (keyEvent.key == KEY_CONTROL) isControlKeyDown = true
        // key rather than code, as the browser's own zoom goes by it: the plus of a Hungarian layout is Shift + 3.
        // Alt is left out because AltGr arrives as Ctrl + Alt on Windows, and AltGr with these keys types a character
        // on some layouts.
        val steps = when (keyEvent.key) {
            "+", "=" -> 1
            "-", "_" -> -1
            "0" -> null
            else -> return@listener
        }
        if ((keyEvent.ctrlKey || keyEvent.metaKey) && !keyEvent.altKey && viewModel.zoomSongText(steps)) {
            keyEvent.preventDefault()
        }
    }
    val keyUpListener: (Event) -> Unit = { event ->
        if (event.unsafeCast<KeyboardEvent>().key == KEY_CONTROL) isControlKeyDown = false
    }
    // A key released while another window has the focus is never reported to this one.
    val blurListener: (Event) -> Unit = { isControlKeyDown = false }
    val wheelListener: (Event) -> Unit = { event ->
        val wheelEvent = event.unsafeCast<WheelEvent>()
        if (wheelEvent.ctrlKey) {
            if (viewModel.isSongTextZoomable) event.preventDefault()
            val isPinch = !isControlKeyDown && wheelEvent.deltaMode == WheelEvent.DOM_DELTA_PIXEL
            if (isPinch && viewModel.magnifyByTouchpad(exp(-wheelEvent.deltaY / PIXELS_PER_PINCH_E).toFloat())) {
                event.preventDefault()
                event.stopPropagation()
            }
        }
    }
    window.addEventListener(EVENT_KEY_DOWN, keyDownListener, true)
    window.addEventListener(EVENT_KEY_UP, keyUpListener, true)
    window.addEventListener(EVENT_BLUR, blurListener)
    window.addEventListener(EVENT_WHEEL, wheelListener, AddEventListenerOptions(passive = false, capture = true))
    onDispose {
        window.removeEventListener(EVENT_KEY_DOWN, keyDownListener, true)
        window.removeEventListener(EVENT_KEY_UP, keyUpListener, true)
        window.removeEventListener(EVENT_BLUR, blurListener)
        window.removeEventListener(EVENT_WHEEL, wheelListener, true)
    }
}

private const val KEY_CONTROL = "Control"
private const val PIXELS_PER_PINCH_E = 100.0 // A pinch that moves the fingers e times farther apart scrolls by -100 px.
