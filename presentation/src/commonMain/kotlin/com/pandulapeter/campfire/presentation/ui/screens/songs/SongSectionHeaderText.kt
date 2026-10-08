/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.domain.api.models.SongSection
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.songs_unknown_artist
import com.pandulapeter.campfire.presentation.resources.songs_unsorted_label

@Composable
internal fun SongSection.Header.displayText(): String = when (this) {
    // A song can be created without an artist, so the section still needs a name.
    is SongSection.Header.Artist -> name.ifBlank { stringResource(Res.string.songs_unknown_artist) }
    is SongSection.Header.Letter -> letter.toString()
    SongSection.Header.Symbols -> stringResource(Res.string.songs_unsorted_label)
}
