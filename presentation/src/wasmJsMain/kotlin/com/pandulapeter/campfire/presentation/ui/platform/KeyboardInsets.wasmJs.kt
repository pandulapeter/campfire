/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class, InternalComposeUiApi::class)

package com.pandulapeter.campfire.presentation.ui.platform

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalPlatformWindowInsets
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.math.roundToInt

/**
 * Hands the shared UI the on-screen keyboard as the IME inset Android and iOS give it, in the browsers that tell a page
 * where their keyboard is (the VirtualKeyboard API: Chrome, Edge and Samsung Internet on Android).
 *
 * Left to itself the browser shortens the page for the keyboard (index.html's `interactive-widget=resizes-content`),
 * and the canvas with it. To the app that is a window that changed its size rather than a keyboard: every screen under
 * a dialog is laid out again, the dialog is centered anew in what is left, and all of it jumps once as the keyboard
 * comes and once as it goes. Here the keyboard is laid over the page instead (`overlaysContent`), the window keeps its
 * size, and what the keyboard covers arrives as the inset everything that keeps clear of it already reads on the
 * native builds: the shell's content padding, the sheets, the editor and the dialogs, which Compose places above it.
 *
 * The browser only says where the keyboard ends up, never how it gets there, so the inset is animated here, for about
 * as long as the keyboard's own slide. Firefox has no such API and goes on shortening the page, which the app takes as
 * the window changing its size; Safari has neither and pans the page.
 */
@Composable
internal fun ProvideKeyboardInsets(content: @Composable () -> Unit) {
    val virtualKeyboard = remember { virtualKeyboard() } ?: return content()
    val density = LocalDensity.current.density
    val keyboardHeight = remember { Animatable(0f) }
    LaunchedEffect(virtualKeyboard, density) {
        // Conflated, so that a keyboard that changes its mind halfway is animated from wherever the inset is to where
        // the keyboard is going now.
        val targets = Channel<Float>(Channel.CONFLATED)
        val listener: (Event) -> Unit = { targets.trySend(boundingHeight(virtualKeyboard).toFloat() * density) }
        setOverlaysContent(virtualKeyboard, true)
        virtualKeyboard.addEventListener(EVENT_GEOMETRY_CHANGE, listener)
        try {
            keyboardHeight.snapTo(boundingHeight(virtualKeyboard).toFloat() * density)
            targets.receiveAsFlow().collectLatest { keyboardHeight.animateTo(it, KEYBOARD_ANIMATION_SPEC) }
        } finally {
            virtualKeyboard.removeEventListener(EVENT_GEOMETRY_CHANGE, listener)
            setOverlaysContent(virtualKeyboard, false)
        }
    }
    val platformInsets = LocalPlatformWindowInsets.current
    val insets = remember(platformInsets) { platformInsets.withKeyboard { keyboardHeight.value.roundToInt() } }
    CompositionLocalProvider(LocalPlatformWindowInsets provides insets, content = content)
}

/**
 * These insets with the keyboard at the bottom, [height] pixels tall. Read when the inset is used rather than when it
 * is made, so a layout that keeps clear of the keyboard is laid out again on every frame of its slide, and nothing is
 * composed again for it.
 */
private fun PlatformWindowInsets.withKeyboard(height: () -> Int): PlatformWindowInsets = object : PlatformWindowInsets by this {
    override val ime = PlatformInsets(getBottom = height)

    // A dialog or a popup asks for the insets less the ones it has already kept clear of, the keyboard among them.
    override fun excluding(safeInsets: Boolean, ime: Boolean) = this@withKeyboard.excluding(safeInsets, ime)
        .let { if (ime) it else it.withKeyboard(height) }
}

private fun virtualKeyboard(): EventTarget? = js("(typeof navigator !== 'undefined' && navigator.virtualKeyboard) || null")

private fun setOverlaysContent(virtualKeyboard: EventTarget, isOverlaying: Boolean) {
    js("virtualKeyboard.overlaysContent = isOverlaying")
}

/** In CSS pixels. A floating keyboard covers no edge of the page, and the browser reports it with no size at all. */
private fun boundingHeight(virtualKeyboard: EventTarget): Double = js("virtualKeyboard.boundingRect.height")

/** About as long as the keyboard of Android takes to slide in, which is the one that sends these events. */
private val KEYBOARD_ANIMATION_SPEC = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing)

private const val EVENT_GEOMETRY_CHANGE = "geometrychange"
