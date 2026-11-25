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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.preferences.SongLayout
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController

/**
 * Until the layout question the first song opened asks is answered, keeps the layout it will show as picked the one that
 * suits the window's shape ([songLayoutFittingWindow]), so that the song under the question is already laid out that
 * way in its first frame rather than changing as the question comes up. Written while nothing is picked by hand: never
 * while the question is shown, where a pick of the reader's own must not be taken back by a turned phone, and never once
 * it is answered. Its inputs are collected here, in a scope of its own, so that a preference written while a song is
 * read does not compose the app's root again.
 */
@Composable
internal fun FitUnchosenSongLayoutToWindow(
    preferencesController: PreferencesController,
    dialogHost: DialogHost,
) {
    val userPreferences by preferencesController.userPreferences.collectAsStateWithLifecycle()
    val visibleDialog by dialogHost.visibleDialog.collectAsStateWithLifecycle()
    val fitting = songLayoutFittingWindow(LocalWindowInfo.current.containerDpSize)
    val isOwed = userPreferences?.isSongLayoutChosen == false && visibleDialog != DialogType.SongLayout
    val current by rememberUpdatedState(userPreferences?.songLayout)
    LaunchedEffect(isOwed, fitting) {
        if (isOwed && current != fitting) preferencesController.pickSongLayout(fitting)
    }
}

/** One scrolling column for a window taller than it is wide (a phone held upright), pages for any other. */
internal fun songLayoutFittingWindow(windowSize: DpSize) = if (windowSize.height > windowSize.width) SongLayout.CONTINUOUS else SongLayout.PAGES
