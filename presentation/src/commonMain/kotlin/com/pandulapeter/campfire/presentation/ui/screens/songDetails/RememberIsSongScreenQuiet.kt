/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * Whether the song pages are at rest, read as a snapshot so that a wait on it resumes once they are: the screen's own
 * navigation transition has ended and the pager is not moving. The models pages build in the background are applied
 * only then ([rememberSongLyricsModel][com.pandulapeter.campfire.presentation.ui.songView.rememberSongLyricsModel]), so
 * no song is laid out on a frame of a slide.
 *
 * The transition is at rest only once its current state is its target too: it counts as running only from its first
 * animation frame on, so on the push frame itself, and in the effects that frame launches, it would read as quiet.
 * Read through the lambda rather than here, so the pages do not recompose for it.
 */
@Composable
internal fun rememberIsSongScreenQuiet(pagerState: PagerState): () -> Boolean {
    val transition = LocalNavAnimatedContentScope.current.transition
    return remember(transition, pagerState) {
        { !transition.isRunning && transition.currentState == transition.targetState && !pagerState.isScrollInProgress }
    }
}
