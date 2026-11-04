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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.presentation.localization.joinIsolated
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.print_as_played
import com.pandulapeter.campfire.presentation.resources.print_as_played_songs_description
import com.pandulapeter.campfire.presentation.resources.print_as_written
import com.pandulapeter.campfire.presentation.resources.print_as_written_description
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_playing_override_transposition
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.ui.components.controls.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.components.list.LIST_ITEM_KEYLINE
import com.pandulapeter.campfire.presentation.ui.playing.SongAsPlayed
import com.pandulapeter.campfire.presentation.ui.playing.transpositionLabel
import com.pandulapeter.campfire.presentation.ui.print.PrintSource

/**
 * The choice between writing the library's own files as stored and as the songs are played here
 * ([PrintSettings.exportsAsPlayed]), offered only where that changes a file ([isAsPlayedOffered]). Both name what they
 * write, rather than a switch whose off side said nothing of the file it leaves: the line under them describes the
 * chosen one, cross-fading as the choice changes. One song's played side names what it changes, as the song's Song
 * defaults card does; several songs' says so in general, each song being changed its own way.
 */
@Composable
internal fun AsPlayedOption(
    modifier: Modifier,
    source: PrintSource,
    settings: PrintSettings,
    onSettings: (PrintSettings) -> Unit,
) = Column(modifier.padding(vertical = 8.dp)) {
    SegmentedChoice(
        options = listOf(
            false to stringResource(Res.string.print_as_written),
            true to stringResource(Res.string.print_as_played),
        ),
        selected = settings.exportsAsPlayed,
        onSelected = { onSettings(settings.copy(exportsAsPlayed = it)) },
    )
    val description = if (settings.exportsAsPlayed) asPlayedDescription(source) else stringResource(Res.string.print_as_written_description)
    AnimatedContent(
        modifier = Modifier.padding(start = LIST_ITEM_KEYLINE, end = LIST_ITEM_KEYLINE, top = 8.dp),
        targetState = description,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
    ) { shownDescription ->
        Text(text = shownDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun asPlayedDescription(source: PrintSource) = source.songs
    .takeUnless { source.hasSeveralSongs }
    ?.firstNotNullOfOrNull { it.asPlayed }
    ?.let { asPlayedLabels(it) }
    ?.takeIf { it.isNotEmpty() }
    ?: stringResource(Res.string.print_as_played_songs_description)

/** What [asPlayed] writes into the file, in the words of the song's own steppers. */
@Composable
private fun asPlayedLabels(asPlayed: SongAsPlayed) = listOfNotNull(
    asPlayed.transposition.takeIf { it != 0 }?.let {
        stringResource(Res.string.song_details_playing_override_transposition, transpositionLabel(transposition = it, key = null))
    },
    asPlayed.capo?.let { stringResource(Res.string.song_details_capo, it) },
    asPlayed.tempo?.let { stringResource(Res.string.song_details_tempo, it.toString()) },
).joinIsolated(" · ")
