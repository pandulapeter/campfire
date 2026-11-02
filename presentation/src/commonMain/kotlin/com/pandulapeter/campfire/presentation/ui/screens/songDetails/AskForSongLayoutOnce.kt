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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType

/**
 * Asks how songs are to be laid out ([DialogType.SongLayout]) the first time a song is opened by a reader who has not
 * picked a layout yet (in Preferences, the only other place it is set; the onboarding leaves it to the song, where the
 * choice can be seen). Only over no other dialog, and once per song screen until its Confirm answers it: a sheet put
 * away without it (the stack closing it, a window closed) asks again over the next song opened.
 */
@Composable
internal fun AskForSongLayoutOnce(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
) {
    val isOwed = userPreferences?.isSongLayoutChosen == false
    LaunchedEffect(isOwed) {
        if (isOwed) viewModel.dialogHost.showIfNoneIsShown(DialogType.SongLayout)
    }
}
