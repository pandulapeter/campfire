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
import com.pandulapeter.campfire.presentation.resources.setlists_sort
import com.pandulapeter.campfire.presentation.resources.setlists_sorting_mode_by_date
import com.pandulapeter.campfire.presentation.resources.setlists_sorting_mode_by_title

/**
 * The order of the setlists, offered on the setlists screen and in the setlist picker sheet alike, see [SongSortMenu].
 *
 * @param onSortingModeChanged Called before [onSelected] when the order chosen is another one than [selected].
 */
@Composable
internal fun SetlistSortMenu(
    modifier: Modifier = Modifier,
    selected: UserPreferences.SetlistSortingMode?,
    onSelected: (UserPreferences.SetlistSortingMode) -> Unit,
    onSortingModeChanged: () -> Unit = {},
    isEnabled: Boolean = true,
) {
    SortMenu(
        modifier = modifier,
        isEnabled = isEnabled,
        title = stringResource(Res.string.setlists_sort),
        options = listOf(
            UserPreferences.SetlistSortingMode.BY_DATE to stringResource(Res.string.setlists_sorting_mode_by_date),
            UserPreferences.SetlistSortingMode.BY_TITLE to stringResource(Res.string.setlists_sorting_mode_by_title),
        ),
        selected = selected,
        onSelected = {
            if (it != selected) onSortingModeChanged()
            onSelected(it)
        },
    )
}
