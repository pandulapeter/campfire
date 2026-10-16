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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.add_songs_hint_title
import com.pandulapeter.campfire.presentation.ui.components.AddSongsTips
import com.pandulapeter.campfire.presentation.ui.components.songCard.SONG_CARD_OUTER_PADDING
import com.pandulapeter.campfire.presentation.ui.components.songCard.SONG_CARD_VERTICAL_PADDING

/**
 * [AddSongsTips] under the songs of a library too small to be anybody's own yet ([isAddSongsHintShown]), which is
 * where the onboarding leaves a user with only the demo songs. Drawn in a stronger tone than the song cards, so that it
 * reads as something the app says rather than one more song.
 */
@Composable
internal fun AddSongsHintCard(
    modifier: Modifier = Modifier,
    isEnabled: Boolean,
    onPaste: () -> Unit,
    onImport: () -> Unit,
) = Surface(
    modifier = modifier.fillMaxWidth().padding(horizontal = SONG_CARD_OUTER_PADDING, vertical = SONG_CARD_VERTICAL_PADDING + 8.dp),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            modifier = Modifier.padding(bottom = 16.dp).semantics { heading() },
            text = stringResource(Res.string.add_songs_hint_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        AddSongsTips(
            isEnabled = isEnabled,
            onPaste = onPaste,
            onImport = onImport,
        )
    }
}

/**
 * Whether the songs screen ends in [AddSongsHintCard]: while the whole library holds fewer than [ADD_SONGS_HINT_LIMIT]
 * songs (null, a library not read yet, is no answer), and only under a list that shows songs, since every placeholder
 * of an empty one offers the same buttons or is about something else. It leaves while a search or the selection is
 * open, as neither is a moment for adding songs, and with performance mode, which takes every way of adding one.
 */
internal fun isAddSongsHintShown(
    librarySongCount: Int?,
    hasPlaceholder: Boolean,
    isSearchOpen: Boolean,
    isSelecting: Boolean,
    isPerformanceModeEnabled: Boolean,
) = librarySongCount != null &&
    librarySongCount < ADD_SONGS_HINT_LIMIT &&
    !hasPlaceholder &&
    !isSearchOpen &&
    !isSelecting &&
    !isPerformanceModeEnabled

/** One more than the demo library's songs, so that the card greets everybody who kept them and added nothing yet. */
private const val ADD_SONGS_HINT_LIMIT = 3
