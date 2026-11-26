/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Whether the reader has a hand on the dock: a finger or a button pressed on it, or a mouse over it ([dockHold]). The
 * dock's rows are never reordered or scrolled from under it.
 */
@Stable
internal class DockHold {
    var isPressed by mutableStateOf(false)
    var isHovered by mutableStateOf(false)
    val isHeld get() = isPressed || isHovered
}

/** Keeps [hold] told whether a pointer is pressed on or hovers over what this is applied to, without taking its events. */
internal fun Modifier.dockHold(hold: DockHold) = pointerInput(hold) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            hold.isPressed = event.changes.any { it.pressed }
            when (event.type) {
                PointerEventType.Enter -> hold.isHovered = true
                PointerEventType.Exit -> hold.isHovered = false
            }
        }
    }
}

/**
 * [value] (what is on the screen, as the dimming reads it) as the dock's order follows it: taken at once on the first
 * frame, so that a song opened again starts in its order, and after that only once it has held for [DOCK_REORDER_SETTLE]
 * (a step's or a fling's resting place, never every range a drag passes), no sooner than [DOCK_REORDER_INTERVAL] after
 * the last (a column scrolling by itself passes sections steadily, and the dock would never stand still), and never
 * while [isHeld] (a hand on the dock, or one of its rows scrolling), which it waits out.
 */
@Composable
internal fun <T> rememberSettledForDockOrder(value: T, isHeld: () -> Boolean): T {
    val latest = rememberUpdatedState(value)
    val latestIsHeld = rememberUpdatedState(isHeld)
    val settled = remember { mutableStateOf(value) }
    LaunchedEffect(Unit) {
        var lastChange: TimeSource.Monotonic.ValueTimeMark? = null
        snapshotFlow { latest.value }.collectLatest { target ->
            if (target == settled.value) return@collectLatest
            delay(DOCK_REORDER_SETTLE)
            lastChange?.elapsedNow()?.let { elapsed -> if (elapsed < DOCK_REORDER_INTERVAL) delay(DOCK_REORDER_INTERVAL - elapsed) }
            snapshotFlow { latestIsHeld.value() }.first { !it }
            settled.value = target
            lastChange = TimeSource.Monotonic.markNow()
        }
    }
    return settled.value
}

/** How long what is on the screen holds before the dock's order follows it. */
private val DOCK_REORDER_SETTLE = 400.milliseconds

/** The least time between two of the dock's reorders. */
private val DOCK_REORDER_INTERVAL = 1500.milliseconds
