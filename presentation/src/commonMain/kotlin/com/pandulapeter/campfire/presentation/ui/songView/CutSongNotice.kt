/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_cut
import com.pandulapeter.campfire.presentation.ui.songView.model.LayoutBudget

/** Where a song too long to be laid out whole stops, see [LayoutBudget]: the file is whole, and the editor shows it all. */
@Composable
internal fun CutSongNotice(
    modifier: Modifier = Modifier,
) = Text(
    modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = CARD_PADDING)
        .padding(top = 16.dp),
    text = stringResource(Res.string.song_details_cut),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
