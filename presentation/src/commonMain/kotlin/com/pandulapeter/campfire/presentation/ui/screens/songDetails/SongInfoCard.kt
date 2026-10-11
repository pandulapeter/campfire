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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordProTempo
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.ui.components.rememberSettledCoverArtUrl

/**
 * What the song is, in the editor's preview: the About the song sheet's grouped tiles, as wide as their column whatever
 * they hold, untitled since every tile names itself, the Song defaults among them in place of a line of text reading the
 * same four values under the card. [metadata] holds them the way that line would read them (see `withMetadataSection`),
 * and the tempo and the time signature the click starts at stand in where the text names none, as in the sheet. The preview has the room the sheet would only repeat, so each tile
 * opens its own editor directly ([editing]) rather than the sheet, and a group with nothing in it offers that editor so that it
 * can be given something: a song that says nothing about itself yet has every tile all the same. Its links are not
 * followed, since a tap on a tile is meant for its editor. Its cover follows the text the way the editor's title does,
 * once the typing has paused, and only where the cover art setting gives [editing] its cover search, which is what tells
 * the card that setting.
 */
@Composable
internal fun SongInfoCard(
    metadata: ChordProMetadata,
    editing: SongInfoEditing?,
    fontScale: Float,
) = SongInfoBody(
    modifier = Modifier.fillMaxWidth(),
    metadata = metadata,
    coverArtUrl = rememberSettledCoverArtUrl(metadata.coverArt.takeIf { editing?.onEditCoverArt != null }),
    fontScale = fontScale,
    horizontalPadding = 0.dp,
    editing = editing,
    defaults = SongDefaults(
        key = metadata.key?.takeIf { it.isNotBlank() },
        capo = metadata.capo,
        tempo = ChordProTempo.parse(metadata.tempo) ?: MetronomePattern.DEFAULT_BPM,
        time = metadata.time?.takeIf { it.isNotBlank() } ?: TimeSignature.COMMON_TIME.toString(),
        // The text being typed is read through no setlist, and the preview's steppers change nothing about it.
        overrides = emptyList(),
        isReadFromSetlist = false,
        onEdit = editing?.onEditDefaults,
    ),
    onOpenLink = null,
)
