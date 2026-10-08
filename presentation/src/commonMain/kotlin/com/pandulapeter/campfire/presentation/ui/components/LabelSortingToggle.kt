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

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_sort_by_alpha
import com.pandulapeter.campfire.presentation.resources.ic_sort_by_usage
import com.pandulapeter.campfire.presentation.resources.songs_labels_sorted_alphabetically
import com.pandulapeter.campfire.presentation.resources.songs_labels_sorted_by_usage
import org.jetbrains.compose.resources.painterResource

/**
 * Switches the values of a group between the two [UserPreferences.LabelSortingMode]s: most used first, or
 * alphabetical. The icon shows the order the list is in rather than the one a tap would put it in, the way a column
 * header's sort indicator does, and so does what a screen reader announces.
 */
@Composable
internal fun LabelSortingToggle(
    modifier: Modifier = Modifier,
    sortingMode: UserPreferences.LabelSortingMode,
    onSortingModeSelected: (UserPreferences.LabelSortingMode) -> Unit,
) = IconButton(
    modifier = modifier,
    onClick = {
        onSortingModeSelected(
            when (sortingMode) {
                UserPreferences.LabelSortingMode.BY_USAGE -> UserPreferences.LabelSortingMode.ALPHABETICAL
                UserPreferences.LabelSortingMode.ALPHABETICAL -> UserPreferences.LabelSortingMode.BY_USAGE
            }
        )
    },
) {
    Crossfade(targetState = sortingMode) { mode ->
        Icon(
            modifier = Modifier.size(SORTING_TOGGLE_ICON_SIZE),
            painter = painterResource(
                when (mode) {
                    UserPreferences.LabelSortingMode.BY_USAGE -> Res.drawable.ic_sort_by_usage
                    UserPreferences.LabelSortingMode.ALPHABETICAL -> Res.drawable.ic_sort_by_alpha
                }
            ),
            contentDescription = stringResource(
                when (mode) {
                    UserPreferences.LabelSortingMode.BY_USAGE -> Res.string.songs_labels_sorted_by_usage
                    UserPreferences.LabelSortingMode.ALPHABETICAL -> Res.string.songs_labels_sorted_alphabetically
                }
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
