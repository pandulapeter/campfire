/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.menu

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Whether any [OverflowMenu] is open, for the platform shells that have to know that before they act on a key, and for
 * the web build's history, which gives an open menu an entry of its own (which is why it is a snapshot state). A
 * [androidx.compose.material3.DropdownMenu] keeps that next to the button that opened it, inside the composition, where
 * the desktop window's key handler cannot see it, so every menu counts itself in here for as long as it is up.
 *
 * The view model owns the one the app reads and [LocalOverlayState] hands it to the menus, so that nothing about it is
 * shared between two compositions of the app in one process - the screenshot tool's renders, or a test.
 *
 * A bottom sheet a screen hosts itself rather than through the view model's dialogs (the editor's search) counts itself
 * in here too, for the same reason: it is something on top that Escape and the browser's back button have to close
 * before they go back from the screen under it.
 *
 * A stepper's value being typed into ([StepperField][com.pandulapeter.campfire.presentation.ui.components.controls.StepperField])
 * is counted apart from those: its keys are the field's - the arrows, Space and M, which the song details screen and the
 * metronome otherwise take before anything focused sees them - but it is no overlay to the back stack or the browser's
 * history, and Escape is answered by the field itself. A press anywhere outside its stepper ends the typing
 * ([onPointerPressed], from the app's root): nothing else on the screen takes the focus from it, and a field left
 * focused once the user has moved on keeps its caret, and its keyboard.
 */
@Stable
internal class OverlayState {

    private var openMenuCount by mutableIntStateOf(0)

    val isAnyMenuOpen: Boolean get() = openMenuCount > 0

    fun onMenuOpened() {
        openMenuCount++
    }

    fun onMenuClosed() {
        openMenuCount--
    }

    private var typedValue by mutableStateOf<TypedValue?>(null)

    val isValueBeingTyped: Boolean get() = typedValue != null

    /**
     * @param bounds Where the stepper being typed into is, in the coordinates of the app's root, read at each press
     *   since a scrolled page moves it.
     * @param onEnd Ends the typing, as a press outside [bounds] asks.
     */
    fun onValueTypingStarted(bounds: () -> Rect, onEnd: () -> Unit) {
        typedValue = TypedValue(bounds = bounds, onEnd = onEnd)
    }

    fun onValueTypingEnded() {
        typedValue = null
    }

    /** A pointer pressed at [position] in the app's root, seen before anything under it handles the press. */
    fun onPointerPressed(position: Offset) {
        val typed = typedValue ?: return
        if (!typed.bounds().contains(position)) typed.onEnd()
    }

    private class TypedValue(
        val bounds: () -> Rect,
        val onEnd: () -> Unit,
    )
}

/**
 * The [OverlayState] the menus count themselves into, provided by `CampfireApp`. The default keeps a menu composed
 * outside the app working.
 */
internal val LocalOverlayState = staticCompositionLocalOf { OverlayState() }
