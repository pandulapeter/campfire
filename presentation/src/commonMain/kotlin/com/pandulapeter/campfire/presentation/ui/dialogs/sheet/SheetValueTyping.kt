/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.pandulapeter.campfire.presentation.ui.components.menu.LocalOverlayState

/**
 * Tells the app's [com.pandulapeter.campfire.presentation.ui.components.menu.OverlayState] of every press inside a sheet,
 * as the app's root does for the screen, so that a press outside a stepper being typed into in the sheet ends the typing
 * there too: a sheet is a window of its own (a layer of its own on the desktop), whose presses never reach the root's.
 * The position is handed over in the sheet's root's coordinates, the ones the stepper measures its own bounds in. Seen in
 * the initial pass and never consumed, so that the press still does whatever it does underneath.
 */
@Composable
internal fun Modifier.endingValueTypingOnPress(): Modifier {
    val overlayState = LocalOverlayState.current
    val placed = remember { PlacedCoordinates() }
    return onGloballyPositioned { placed.value = it }.pointerInput(overlayState) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val coordinates = placed.value?.takeIf { it.isAttached }
                if (event.type == PointerEventType.Press && coordinates != null) {
                    event.changes.forEach { overlayState.onPointerPressed(coordinates.localToRoot(it.position)) }
                }
            }
        }
    }
}

/** Where [endingValueTypingOnPress] was last placed. Not a state: read only at a press. */
private class PlacedCoordinates(var value: LayoutCoordinates? = null)
