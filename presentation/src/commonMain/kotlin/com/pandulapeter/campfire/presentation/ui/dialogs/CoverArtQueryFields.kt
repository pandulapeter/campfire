/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.cover_art_search
import com.pandulapeter.campfire.presentation.resources.ic_search
import com.pandulapeter.campfire.presentation.resources.song_editor_insert_album
import com.pandulapeter.campfire.presentation.resources.songs_new_song_artist
import com.pandulapeter.campfire.presentation.resources.songs_new_song_title
import com.pandulapeter.campfire.presentation.ui.components.MAX_SEARCH_QUERY_LENGTH
import com.pandulapeter.campfire.presentation.ui.components.rememberClearTextButton
import org.jetbrains.compose.resources.painterResource

/**
 * The three things a record is found by. Artist and album share a row, since those two name a record, with the title
 * and the button under them, since the title is what is searched by only where the album is left empty.
 *
 * On larger windows they stay above the results. On small windows they scroll with the results so the fields can
 * move out of the way of the covers, especially while the keyboard is open.
 */
@Composable
internal fun CoverArtQueryFields(
    artist: String,
    album: String,
    title: String,
    onArtistChange: (String) -> Unit,
    onAlbumChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    canSearch: Boolean,
    onSearch: () -> Unit,
) = Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CoverArtQueryField(
            modifier = Modifier.weight(1f),
            value = artist,
            label = stringResource(Res.string.songs_new_song_artist),
            onValueChange = onArtistChange,
            onSearch = onSearch,
        )
        CoverArtQueryField(
            modifier = Modifier.weight(1f),
            value = album,
            label = stringResource(Res.string.song_editor_insert_album),
            onValueChange = onAlbumChange,
            onSearch = onSearch,
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArtQueryField(
            modifier = Modifier.weight(1f),
            value = title,
            label = stringResource(Res.string.songs_new_song_title),
            onValueChange = onTitleChange,
            onSearch = onSearch,
        )
        // Outlined rather than filled: the header's Save is what finishes the sheet, and two filled buttons in sight
        // left the reader to work out which of them was the one that wrote the cover.
        OutlinedButton(
            modifier = Modifier.padding(top = 8.dp),
            enabled = canSearch,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            onClick = onSearch,
        ) {
            Icon(
                modifier = Modifier.size(ButtonDefaults.IconSize),
                painter = painterResource(Res.drawable.ic_search),
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(Res.string.cover_art_search))
        }
    }
}

@Composable
private fun CoverArtQueryField(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
) = OutlinedTextField(
    modifier = modifier,
    value = value,
    onValueChange = { onValueChange(it.replace("\n", "").take(MAX_SEARCH_QUERY_LENGTH)) },
    label = { Text(label) },
    trailingIcon = rememberClearTextButton(isVisible = value.isNotEmpty(), onClear = { onValueChange("") }),
    singleLine = true,
    // A name is searched for as it is spelled, so autocorrect is off for the reason it is off in the list screens' search.
    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
)
