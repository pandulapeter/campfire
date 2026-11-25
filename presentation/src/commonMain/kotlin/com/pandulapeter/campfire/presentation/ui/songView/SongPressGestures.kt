/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Whether a tap on the song steps it (song details), so that what is drawn in it leaves a tap alone and answers a long
 * press instead: a picture opening its viewer. Elsewhere (the editor's preview) a tap is free to open it.
 */
internal val LocalTapsStepSong = staticCompositionLocalOf { false }

/**
 * Calls [onLongPress] once a press has been held for the long press timeout without anything else taking it. A press
 * let go before then is left as it was, unconsumed, for the song's tap to step on; one the scroll takes as a drag is
 * given up. A long press's release is consumed, so that nothing under it answers it too.
 */
@Composable
internal fun Modifier.onLongPress(onLongPress: () -> Unit): Modifier {
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown()
            var isOver = false
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation()
                isOver = true
            }
            if (isOver) return@awaitEachGesture
            currentOnLongPress()
            // Only the release is taken, which tells the song's tap that the press was answered here: a slide taken as
            // well would read to it as a drag the system cancelled.
            waitForUpOrCancellation()?.consume()
        }
    }
}

/** Calls [onClick] for a click of a mouse, taking the press so that nothing under it answers it; a touch is left alone. */
@Composable
internal fun Modifier.onMouseClick(onClick: () -> Unit): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (down.type != PointerType.Mouse) return@awaitEachGesture
            down.consume()
            val up = waitForUpOrCancellation() ?: return@awaitEachGesture
            up.consume()
            currentOnClick()
        }
    }
}
