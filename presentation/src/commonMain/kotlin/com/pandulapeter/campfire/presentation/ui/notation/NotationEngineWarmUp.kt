/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.pandulapeter.campfire.data.model.song.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * Starts the notation engine once the library first holds a song with an `abc` block, away from any song being opened,
 * so that a tune's first open pays its own rendering (~5 ms on the desktop) rather than the engine's start as well.
 *
 * The wait is the same on every platform: Android, the desktop, and iOS start the engine on a thread of its own, but the
 * start still takes a core for a while, and the library is read during the cold start the Baseline Profile is tuned
 * for; the web starts it on the page's one thread, a pause better taken while the list is read than inside the first
 * tune's enter transition. A tune opened within the wait starts the engine from its page, and this then finds it running.
 */
@Composable
internal fun WarmNotationEngine(songs: StateFlow<List<Song>>) {
    val renderer = LocalAbcRenderer.current ?: return
    LaunchedEffect(renderer, songs) {
        songs.first { library -> library.any { it.hasSheetMusic } }
        delay(WARM_UP_DELAY_MILLIS)
        renderer.render(tune = WARM_UP_TUNE, width = WARM_UP_WIDTH, transposition = 0)
    }
}

/** Two bars with a chord symbol and a line of words, so that every part of the engine a real tune needs is started. */
private const val WARM_UP_TUNE = "X:1\nK:C\n\"C\"C2 E2 | G4 |]\nw:la la la"
private const val WARM_UP_WIDTH = 300
private const val WARM_UP_DELAY_MILLIS = 2_000L
