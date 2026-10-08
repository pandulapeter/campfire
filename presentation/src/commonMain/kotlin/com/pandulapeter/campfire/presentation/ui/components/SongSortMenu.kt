/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.songs_sort
import com.pandulapeter.campfire.presentation.resources.songs_sorting_mode_by_artist
import com.pandulapeter.campfire.presentation.resources.songs_sorting_mode_by_title

/**
 * The order of the songs, offered on the songs screen and in the song picker sheet alike. It is one preference rather
 * than one per place, as the order of the tags is: the sheet lists the songs the way the library is browsed.
 *
 * @param selected The order the preferences hold, null while they are being read.
 */
@Composable
internal fun SongSortMenu(
    modifier: Modifier = Modifier,
    selected: UserPreferences.SortingMode?,
    onSelected: (UserPreferences.SortingMode) -> Unit,
) {
    SortMenu(
        modifier = modifier,
        title = stringResource(Res.string.songs_sort),
        options = listOf(
            UserPreferences.SortingMode.BY_ARTIST to stringResource(Res.string.songs_sorting_mode_by_artist),
            UserPreferences.SortingMode.BY_TITLE to stringResource(Res.string.songs_sorting_mode_by_title),
        ),
        selected = selected,
        onSelected = onSelected,
    )
}
