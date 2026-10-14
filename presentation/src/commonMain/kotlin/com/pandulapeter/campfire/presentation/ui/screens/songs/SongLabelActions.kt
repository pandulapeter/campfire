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
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_label
import com.pandulapeter.campfire.presentation.resources.ic_language
import com.pandulapeter.campfire.presentation.resources.song_details_languages_edit
import com.pandulapeter.campfire.presentation.resources.song_details_tags_manage
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.menu.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import org.jetbrains.compose.resources.painterResource

/**
 * The tag and the language editors as entries of a song card's menu on the songs screen: unlike the metadata and the
 * link dialogs they need nothing but the song list's own metadata to open, and filing songs under a tag or a language
 * is done to many of them in a row, which a trip to every song's details screen makes a chore.
 */
@Composable
internal fun songLabelActions(
    viewModel: CampfireViewModel,
    song: Song,
    target: SongEditTarget,
): List<ActionsMenuItem> = listOf(
    ActionsMenuItem(
        title = stringResource(Res.string.song_details_tags_manage),
        icon = painterResource(Res.drawable.ic_label),
        isAlwaysInMenu = true,
        onClick = { viewModel.dialogHost.showDialog(DialogType.SongTags(song = song, target = target)) },
    ),
    ActionsMenuItem(
        title = stringResource(Res.string.song_details_languages_edit),
        icon = painterResource(Res.drawable.ic_language),
        isAlwaysInMenu = true,
        onClick = { viewModel.dialogHost.showDialog(DialogType.SongLanguages(song = song, target = target)) },
    ),
)
