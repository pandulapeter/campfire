/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songFilters

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.songs_filters_reset

/**
 * Empties every group of [SongFilters] at once, from the header of their sheet. It is offered for exactly as long as the
 * filter action carries its badge, since what it resets is the selection that badge points at. In the header its
 * arrival moves nothing: the row's height is the close button's, and the sheet passes it as its action whether it is
 * shown or not, since a header with no action pads its end differently.
 */
@Composable
internal fun ResetFiltersAction(
    isVisible: Boolean,
    onClick: () -> Unit,
) = AnimatedVisibility(
    visible = isVisible,
    enter = fadeIn(),
    exit = fadeOut(),
) {
    TextButton(
        modifier = Modifier.padding(start = 4.dp, end = 8.dp),
        onClick = onClick,
    ) {
        Text(text = stringResource(Res.string.songs_filters_reset))
    }
}
