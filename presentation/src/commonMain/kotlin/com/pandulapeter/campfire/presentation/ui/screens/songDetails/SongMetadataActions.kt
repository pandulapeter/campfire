/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_album
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.ic_link
import com.pandulapeter.campfire.presentation.resources.song_details_change_cover_art
import com.pandulapeter.campfire.presentation.resources.song_details_languages_edit
import com.pandulapeter.campfire.presentation.resources.song_details_links_edit
import com.pandulapeter.campfire.presentation.resources.song_details_set_cover_art
import com.pandulapeter.campfire.presentation.resources.song_details_tags_manage
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenuItem
import org.jetbrains.compose.resources.painterResource

/** File edits stay behind the overflow tap, even when the app bar has room for more buttons. */
@Composable
internal fun songMetadataActions(
    viewModel: CampfireViewModel,
    song: Song,
    text: String?,
    isCoverArtEnabled: Boolean,
): List<ActionsMenuItem> = listOfNotNull(
    ActionsMenuItem(
        title = stringResource(Res.string.song_details_tags_manage),
        icon = painterResource(Res.drawable.ic_edit),
        isAlwaysInMenu = true,
        onClick = { viewModel.showDialog(CampfireViewModel.DialogType.SongTags(song)) },
    ),
    ActionsMenuItem(
        title = stringResource(Res.string.song_details_languages_edit),
        icon = painterResource(Res.drawable.ic_language),
        isAlwaysInMenu = true,
        onClick = { viewModel.showDialog(CampfireViewModel.DialogType.SongLanguages(song)) },
    ),
    ActionsMenuItem(
        title = stringResource(Res.string.song_details_links_edit),
        icon = painterResource(Res.drawable.ic_link),
        isEnabled = text != null,
        isAlwaysInMenu = true,
        onClick = { viewModel.showSongLinksDialog(song) },
    ),
    if (isCoverArtEnabled) {
        ActionsMenuItem(
            title = stringResource(if (song.coverArtUrl == null) Res.string.song_details_set_cover_art else Res.string.song_details_change_cover_art),
            icon = painterResource(Res.drawable.ic_album),
            isAlwaysInMenu = true,
            onClick = { viewModel.showDialog(CampfireViewModel.DialogType.CoverArtSearch(song)) },
        )
    } else {
        null
    },
)
