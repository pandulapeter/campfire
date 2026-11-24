/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The song scrolling by itself on the song details screen on top, for the ways back that reach [Navigator.navigateBack]
 * (the desktop's Escape, the app bar's arrow) and for the browser's history, which keeps an entry for it: a step back
 * stops it before it leaves the screen, since Back is the nearest way to stop a song with an instrument in hand. The
 * screen registers how to stop it while it plays, and takes that back once it stops; a back gesture it answers itself.
 */
internal class AutoScrollPlayback {

    private var stopPlaying by mutableStateOf<(() -> Unit)?>(null)

    val isPlaying get() = stopPlaying != null

    fun register(stop: () -> Unit) {
        stopPlaying = stop
    }

    /** Takes back [stop], unless another screen has registered its own since. */
    fun unregister(stop: () -> Unit) {
        if (stopPlaying === stop) stopPlaying = null
    }

    fun stop() {
        stopPlaying?.invoke()
    }
}
