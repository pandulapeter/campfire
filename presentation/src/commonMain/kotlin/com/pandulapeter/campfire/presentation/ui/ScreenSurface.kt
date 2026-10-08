/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * One card of the deck that covers the navigation chrome: an opaque screen over the whole window. The screens have to
 * be opaque, otherwise the one being covered would show through the one covering it.
 *
 * A [Surface] rather than a plain box because it also blocks touches from reaching what is behind it: the chrome is
 * drawn under the screens, so without this the rail would still take taps through the song details screen covering
 * it, and a screen being covered would still take taps through the one landing on it.
 *
 * It also takes no touches while it is moving - being dealt, taken away, or uncovered by the card above it leaving.
 * Navigation 3 holds every entry below RESUMED until its scene transition has settled, so an entry below RESUMED in a
 * host that is RESUMED is one that is moving. The editor leaves on a spring that has cleared a finger within a tenth of
 * a second, and without this the second tap of a double-tap on its Close would land on the Back arrow of the song
 * underneath and close that too. The host is asked as well because it is not always RESUMED while the app is in use:
 * a desktop window that does not have the focus is only STARTED, and the click that focuses it has to count.
 */
@Composable
internal fun ScreenSurface(
    hostLifecycle: Lifecycle,
    scrim: NavigationScrim,
    content: @Composable () -> Unit,
) {
    val entryLifecycle = LocalLifecycleOwner.current.lifecycle
    val scrimCoverage = rememberScrimCoverage(scrim)
    val scrimColor = MaterialTheme.colorScheme.scrim
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .coveredScreenScrim(scrimColor) { scrimCoverage.value }
            .pointerInput(hostLifecycle, entryLifecycle) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // Asked when the finger comes down rather than for every event: a gesture that started on a screen
                    // that had landed is the user's to finish, and one that started on a moving screen is not, even if
                    // the screen lands before the finger is lifted. The down is consumed as well, or a child that does
                    // not require an unconsumed down would still start its press ripple.
                    if (hostLifecycle.currentState == Lifecycle.State.RESUMED && !entryLifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        down.consume()
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
            },
        color = MaterialTheme.colorScheme.background,
        content = content,
    )
}
