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
 * picked a layout yet: an installation from before there was a choice, or one whose onboarding was skipped before its
 * page. Only over no other dialog, and asked once whatever the answer, closing the sheet included.
 */
@Composable
internal fun AskForSongLayoutOnce(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
) {
    val isOwed = userPreferences?.isSongLayoutChosen == false
    LaunchedEffect(isOwed) {
        if (isOwed && viewModel.dialogHost.showIfNoneIsShown(DialogType.SongLayout)) viewModel.preferencesController.markSongLayoutChosen()
    }
}
