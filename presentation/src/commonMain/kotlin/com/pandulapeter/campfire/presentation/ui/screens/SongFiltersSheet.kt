/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.songs_filter
import com.pandulapeter.campfire.presentation.ui.components.songFilters.ResetFiltersAction
import com.pandulapeter.campfire.presentation.ui.components.songFilters.SongFilterActions
import com.pandulapeter.campfire.presentation.ui.components.songFilters.SongFilterUiState
import com.pandulapeter.campfire.presentation.ui.components.songFilters.SongFilters
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor

/** The [SongFilters] of the songs screen, with the action that resets them all in the header. */
@Composable
internal fun SongFiltersSheet(
    state: SongFilterUiState,
    actions: SongFilterActions,
    normalizeForSearch: (String) -> String,
    onDismiss: () -> Unit,
) {
    CampfireBottomSheet(
        title = stringResource(Res.string.songs_filter),
        actions = { ResetFiltersAction(isVisible = state.isActive, onClick = actions::clearSongFilter) },
        onDismiss = onDismiss,
    ) { contentPadding ->
        SongFilters(
            state = state,
            actions = actions,
            normalizeForSearch = normalizeForSearch,
            contentPadding = contentPadding,
            fadeBackgroundColor = sheetContainerColor(),
        )
    }
}
