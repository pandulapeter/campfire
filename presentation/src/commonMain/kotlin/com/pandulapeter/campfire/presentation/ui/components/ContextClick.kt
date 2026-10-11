/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Calls [onClick] when the element is clicked with a mouse's right button, which is what a pointer asks a context menu
 * with — on every platform, since a mouse can be plugged into a phone or a tablet as well. Nothing is added where
 * [onClick] is null.
 *
 * It answers the click as a click, once the button is released over the element, and takes the whole press in the
 * initial pass, so that nothing inside the element and no clickable after it in the chain sees it: Android's clickables
 * answer a right click like a left one, and the row would otherwise be opened as its menu opens. Touch and stylus
 * presses report no mouse buttons, so only a mouse is asked which one it was.
 *
 * The press is looked for by hand rather than with `awaitFirstDown`, which outside Android answers a mouse's primary
 * button alone and so never returns for the click this is about.
 */
@Composable
internal fun Modifier.onContextClick(onClick: (() -> Unit)?): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    return if (onClick == null) {
        this
    } else {
        pointerInput(Unit) {
            awaitEachGesture {
                val press = awaitPointerEvent(PointerEventPass.Initial)
                if (!press.isSecondaryMouseDown()) return@awaitEachGesture
                press.changes.forEach { it.consume() }
                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
                up.consume()
                currentOnClick?.invoke()
            }
        }
    }
}

private fun PointerEvent.isSecondaryMouseDown() = buttons.isSecondaryPressed &&
    !buttons.isPrimaryPressed &&
    changes.all { it.type == PointerType.Mouse && it.changedToDownIgnoreConsumed() }
