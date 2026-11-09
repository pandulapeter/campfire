/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.songDisplay

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_layout_message
import com.pandulapeter.campfire.presentation.resources.song_layout_title
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController
import com.pandulapeter.campfire.presentation.ui.preferences.SongLayoutChoices

/**
 * The question the first song opened asks once, over that song, of a reader who has not picked a layout: pages, or one
 * column that scrolls by itself, and while pages are picked whether they turn a page or a column at a time, in a
 * [ConfirmedQuestionSheet]. A pick is written at once, the song behind it changing as
 * it goes, and only Confirm answers the question, so that a stray tap beside it does not; confirming without a pick keeps
 * the pages, which is what is shown as picked, and a sheet put away without Confirm (back, Escape) is asked again.
 */
@Composable
internal fun SongLayoutSheet(
    preferencesController: PreferencesController,
    onDismiss: () -> Unit,
) {
    val userPreferences by preferencesController.userPreferences.collectAsStateWithLifecycle()
    ConfirmedQuestionSheet(
        title = stringResource(Res.string.song_layout_title),
        onConfirm = preferencesController::markSongLayoutChosen,
        onDismiss = onDismiss,
    ) {
        Text(
            modifier = Modifier.padding(bottom = 16.dp),
            text = stringResource(Res.string.song_layout_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SongLayoutChoices(
            selected = userPreferences?.songLayout,
            pageTurn = userPreferences?.pageTurn,
            onSelected = preferencesController::pickSongLayout,
            onPageTurnSelected = preferencesController::setPageTurn,
        )
    }
}
