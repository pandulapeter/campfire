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

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_sort
import com.pandulapeter.campfire.presentation.resources.setlists_sort
import com.pandulapeter.campfire.presentation.resources.setlists_sorting_mode_by_date
import com.pandulapeter.campfire.presentation.resources.setlists_sorting_mode_by_title
import com.pandulapeter.campfire.presentation.resources.songs_sort
import com.pandulapeter.campfire.presentation.resources.songs_sorting_mode_by_artist
import com.pandulapeter.campfire.presentation.resources.songs_sorting_mode_by_title
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import org.jetbrains.compose.resources.painterResource

/**
 * The order of the songs, offered on the songs screen and in the song picker sheet alike. It is one preference rather
 * than one per place, as the order of the tags is: the sheet lists the songs the way the library is browsed.
 */
@Composable
internal fun SongSortMenu(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    SortMenu(
        modifier = modifier,
        contentDescription = stringResource(Res.string.songs_sort),
        options = listOf(
            UserPreferences.SortingMode.BY_ARTIST to stringResource(Res.string.songs_sorting_mode_by_artist),
            UserPreferences.SortingMode.BY_TITLE to stringResource(Res.string.songs_sorting_mode_by_title),
        ),
        selected = userPreferences?.sortingMode,
        onSelected = viewModel::setSortingMode,
    )
}

/** The order of the setlists, offered on the setlists screen and in the setlist picker sheet alike, see [SongSortMenu]. */
@Composable
internal fun SetlistSortMenu(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    onSortingModeChanged: () -> Unit = {},
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    SortMenu(
        modifier = modifier,
        contentDescription = stringResource(Res.string.setlists_sort),
        options = listOf(
            UserPreferences.SetlistSortingMode.BY_DATE to stringResource(Res.string.setlists_sorting_mode_by_date),
            UserPreferences.SetlistSortingMode.BY_TITLE to stringResource(Res.string.setlists_sorting_mode_by_title),
        ),
        selected = userPreferences?.setlistSortingMode,
        onSelected = {
            if (it != userPreferences?.setlistSortingMode) onSortingModeChanged()
            viewModel.setSetlistSortingMode(it)
        },
    )
}

/**
 * The action of both list screens and both picker sheets that picks the order of the list, a popup of radio rows rather than a
 * section of a sheet: the order is one question with a handful of answers, and a choice is its whole answer, so the
 * menu closes with it.
 *
 * @param options Every order the list can come in, with its label, in the order they are offered.
 */
@Composable
internal fun <T> SortMenu(
    modifier: Modifier = Modifier,
    contentDescription: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelected: (T) -> Unit,
) = OverflowMenu(
    modifier = modifier,
    button = { open ->
        IconButton(onClick = open) {
            Icon(
                painter = painterResource(Res.drawable.ic_sort),
                contentDescription = contentDescription,
            )
        }
    },
) { select ->
    options.forEach { (option, label) ->
        RadioListItem(
            modifier = Modifier.width(SORT_MENU_WIDTH),
            title = label,
            isSelected = option == selected,
            onSelected = { select { onSelected(option) } },
        )
    }
}

private val SORT_MENU_WIDTH = 220.dp
