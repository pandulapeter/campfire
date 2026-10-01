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
import com.pandulapeter.campfire.presentation.resources.ic_info
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.ic_link
import com.pandulapeter.campfire.presentation.resources.song_details_change_cover_art
import com.pandulapeter.campfire.presentation.resources.song_details_languages_edit
import com.pandulapeter.campfire.presentation.resources.song_details_links_edit
import com.pandulapeter.campfire.presentation.resources.song_details_metadata_edit
import com.pandulapeter.campfire.presentation.resources.song_details_set_cover_art
import com.pandulapeter.campfire.presentation.resources.song_details_tags_manage
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenuItem
import org.jetbrains.compose.resources.painterResource

/**
 * The entries that edit what a song says about itself, in the song details overflow menu and in the editor's. They stay
 * behind the overflow tap, even when the app bar has room for more buttons. From the editor ([isEditorDraft]) they edit
 * the text being typed rather than the file, see [CampfireViewModel.DialogType.SongEdit]; [hasText] is whether the
 * text the metadata and link dialogs are prefilled from is at hand.
 */
@Composable
internal fun songMetadataActions(
    viewModel: CampfireViewModel,
    song: Song,
    hasText: Boolean,
    isEditorDraft: Boolean,
    isCoverArtEnabled: Boolean,
): List<ActionsMenuItem> = listOfNotNull(
    ActionsMenuItem(
        title = stringResource(Res.string.song_details_metadata_edit),
        icon = painterResource(Res.drawable.ic_info),
        isEnabled = hasText,
        isAlwaysInMenu = true,
        onClick = { viewModel.showSongMetadataDialog(song = song, isEditorDraft = isEditorDraft) },
    ),
    if (isCoverArtEnabled) {
        ActionsMenuItem(
            title = stringResource(if (song.coverArtUrl == null) Res.string.song_details_set_cover_art else Res.string.song_details_change_cover_art),
            icon = painterResource(Res.drawable.ic_album),
            isAlwaysInMenu = true,
            onClick = { viewModel.showDialog(CampfireViewModel.DialogType.CoverArtSearch(song = song, isEditorDraft = isEditorDraft)) },
        )
    } else {
        null
    },
    ActionsMenuItem(
        title = stringResource(Res.string.song_details_tags_manage),
        icon = painterResource(Res.drawable.ic_label),
        isAlwaysInMenu = true,
        onClick = { viewModel.showDialog(CampfireViewModel.DialogType.SongTags(song = song, isEditorDraft = isEditorDraft)) },
    ),
    ActionsMenuItem(
        title = stringResource(Res.string.song_details_languages_edit),
        icon = painterResource(Res.drawable.ic_language),
        isAlwaysInMenu = true,
        onClick = { viewModel.showDialog(CampfireViewModel.DialogType.SongLanguages(song = song, isEditorDraft = isEditorDraft)) },
    ),
    ActionsMenuItem(
        title = stringResource(Res.string.song_details_links_edit),
        icon = painterResource(Res.drawable.ic_link),
        isEnabled = hasText,
        isAlwaysInMenu = true,
        onClick = { viewModel.showSongLinksDialog(song = song, isEditorDraft = isEditorDraft) },
    ),
)
