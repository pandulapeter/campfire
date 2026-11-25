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

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * Keeps the wheel and trackpad scrolls that run mostly up or down inside the page of a horizontal pager, rather than
 * letting them reach the pager. Foundation passes a scroll that a page's vertical scroll could not use (the page already
 * at its end) on to the pager, which reads the slight sideways drift of a trackpad's vertical swipe as a page turn, and,
 * once it has started scrolling, takes every following event of the swipe ahead of the page. A scroll is held here only
 * in the main pass, after everything inside the page has had it, and only where its vertical part is the larger, so a
 * sideways swipe still turns the page and every scrollable inside still scrolls; macOS locks a swipe to its main axis
 * the same way.
 */
internal fun Modifier.holdingVerticalWheelScrolls() = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            if (event.type != PointerEventType.Scroll || event.changes.any { it.isConsumed }) continue
            val delta = event.changes.fold(Offset.Zero) { sum, change -> sum + change.scrollDelta }
            if (delta != Offset.Zero && abs(delta.y) >= abs(delta.x)) event.changes.forEach { it.consume() }
        }
    }
}
