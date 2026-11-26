/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.preferences.SongLayout
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController
import kotlinx.coroutines.flow.first

/**
 * Until the layout question the first song opened asks is answered, keeps the layout it will show as picked the one that
 * suits the window's shape ([songLayoutFittingWindow]), so that the song under the question is already laid out that
 * way in its first frame rather than changing as the question comes up. Written while nothing is picked by hand: never
 * while a song is open (anywhere in the [backStack], since the editor opens over its song), where a pick of
 * the reader's own put away without Confirm must not be taken back by a turned phone, and never once it is answered. So
 * such a pick lasts while its song is open and is fitted again once it is closed, and a song open from launch shows the
 * stored layout as it is. Fitting resumes only once the pop has settled ([isTransitionRunning], reported a
 * frame after the back stack changes, so given two frames to start), or the song sliding away would be laid out again.
 * Its inputs are read here, in a scope of its own, so that a preference written while a song is read, or a push or a
 * pop, does not compose the app's root again.
 */
@Composable
internal fun FitUnchosenSongLayoutToWindow(
    preferencesController: PreferencesController,
    backStack: List<CampfireDestination>,
    isTransitionRunning: State<Boolean>,
) {
    val userPreferences by preferencesController.userPreferences.collectAsStateWithLifecycle()
    // Derived, and read only here, so that only a song opening or closing composes this again, and a push or a pop never
    // the app's root.
    val isSongOpenState = remember(backStack) { derivedStateOf { backStack.any { it is CampfireDestination.SongDetails } } }
    val isAnySongOpen by isSongOpenState
    var hasSongClosingSettled by remember { mutableStateOf(!isSongOpenState.value) }
    LaunchedEffect(isAnySongOpen) {
        if (isAnySongOpen) {
            hasSongClosingSettled = false
        } else {
            repeat(2) { withFrameNanos { } }
            snapshotFlow { isTransitionRunning.value }.first { !it }
            hasSongClosingSettled = true
        }
    }
    val fitting = songLayoutFittingWindow(LocalWindowInfo.current.containerDpSize)
    val isOwed = userPreferences?.isSongLayoutChosen == false && !isAnySongOpen && hasSongClosingSettled
    val current by rememberUpdatedState(userPreferences?.songLayout)
    LaunchedEffect(isOwed, fitting) {
        if (isOwed && current != fitting) preferencesController.pickSongLayout(fitting)
    }
}

/** One scrolling column for a window taller than it is wide (a phone held upright), pages for any other. */
internal fun songLayoutFittingWindow(windowSize: DpSize) = if (windowSize.height > windowSize.width) SongLayout.CONTINUOUS else SongLayout.PAGES
