/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.presentation.localization.joinIsolated
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.print_as_played
import com.pandulapeter.campfire.presentation.resources.print_as_played_songs_description
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_playing_override_transposition
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import com.pandulapeter.campfire.presentation.ui.playing.SongAsPlayed
import com.pandulapeter.campfire.presentation.ui.playing.transpositionLabel
import com.pandulapeter.campfire.presentation.ui.print.PrintSource

/**
 * The switch that writes the library's own files as the songs are played here ([PrintSettings.exportsAsPlayed]), offered
 * only where that changes a file ([isAsPlayedOffered]). One song's row names what it changes, as the song's Song defaults
 * card does; several songs' says so in general, each song being changed its own way.
 */
@Composable
internal fun AsPlayedOption(
    modifier: Modifier,
    source: PrintSource,
    settings: PrintSettings,
    onSettings: (PrintSettings) -> Unit,
) = SwitchListItem(
    modifier = modifier,
    title = stringResource(Res.string.print_as_played),
    description = if (source.hasSeveralSongs) {
        stringResource(Res.string.print_as_played_songs_description)
    } else {
        source.songs.firstNotNullOfOrNull { it.asPlayed }?.let { asPlayedLabels(it) }?.takeIf { it.isNotEmpty() }
    },
    isChecked = settings.exportsAsPlayed,
    onCheckedChange = { onSettings(settings.copy(exportsAsPlayed = it)) },
)

/** What [asPlayed] writes into the file, in the words of the song's own steppers. */
@Composable
private fun asPlayedLabels(asPlayed: SongAsPlayed) = listOfNotNull(
    asPlayed.transposition.takeIf { it != 0 }?.let {
        stringResource(Res.string.song_details_playing_override_transposition, transpositionLabel(transposition = it, key = null))
    },
    asPlayed.capo?.let { stringResource(Res.string.song_details_capo, it) },
    asPlayed.tempo?.let { stringResource(Res.string.song_details_tempo, it.toString()) },
).joinIsolated(" · ")
