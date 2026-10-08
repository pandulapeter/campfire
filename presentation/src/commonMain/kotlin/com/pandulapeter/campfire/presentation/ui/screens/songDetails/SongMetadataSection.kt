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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_time
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.songInfo.hasSongInfo

/**
 * The first section of the song: the card of what the song is, where [isSongInfoShown] and the song says anything for
 * it or [songInfoEditing] lets it be given something, then the key, capo, tempo and time, which are always on the page,
 * since they are what is played. A whole, uncuttable section.
 *
 * @param playingControls What sets those four, where they are set here rather than only read: the song details screen
 * hands them down outside read only mode, see [SongPlayingControls].
 * @param readsCapoAndTime Whether the line of text names a capo of none too, see [withMetadataSection].
 * @param animatesControls Whether the controls travel to the places a new layout gives them, which the song's own
 * sections decide (see `SectionMotion`): never while a pinch or a window edge being dragged changes the layout every frame.
 * @param titleStyle The style of the section titles of the lyrics, which the card's own title follows as the text is
 * scaled; what the card holds scales with [fontScale] the way the lyrics do.
 * @param chordStyle The style the chords of the lyrics are set in, already scaled, which the line of text read only mode
 * draws the four values in: the key and the capo are read in the chords' accent color, and in the chords' size they read
 * as part of what is played rather than as a caption above it.
 */
@Composable
internal fun SongMetadataSection(
    modifier: Modifier = Modifier,
    metadata: ChordProMetadata,
    isSongInfoShown: Boolean,
    songInfoEditing: SongInfoEditing?,
    playingControls: SongPlayingControls?,
    readsCapoAndTime: Boolean,
    animatesControls: Boolean,
    titleStyle: TextStyle,
    chordStyle: TextStyle,
    fontScale: Float,
) = Column(modifier = modifier) {
    val hasSongInfoCard = isSongInfoShown && (songInfoEditing != null || metadata.hasSongInfo)
    if (hasSongInfoCard) {
        SongInfoCard(
            metadata = metadata,
            editing = songInfoEditing,
            titleStyle = titleStyle,
            fontScale = fontScale,
        )
    }
    if (playingControls == null) {
        SongPlayingMetadata(
            modifier = Modifier.padding(top = if (hasSongInfoCard) 12.dp else 0.dp),
            metadata = metadata,
            readsCapoAndTime = readsCapoAndTime,
            style = chordStyle,
        )
    } else {
        SongPlayingControlsRow(
            modifier = Modifier.padding(top = if (hasSongInfoCard) 12.dp else 0.dp),
            controls = playingControls,
            isAnimated = animatesControls,
            titleStyle = titleStyle,
            fontScale = fontScale,
        )
    }
}

@Composable
private fun SongPlayingMetadata(
    modifier: Modifier = Modifier,
    metadata: ChordProMetadata,
    readsCapoAndTime: Boolean,
    style: TextStyle,
) = SongPlayingValues(
    modifier = modifier,
    values = listOfNotNull(
        metadata.key?.takeIf { it.isNotBlank() }?.let { it to true },
        metadata.capo?.takeIf { readsCapoAndTime || it != 0 }?.let { stringResource(Res.string.song_details_capo, it) to true },
        metadata.tempo?.takeIf { it.isNotBlank() }?.let { textResource(Res.string.song_details_tempo, it) to false },
        metadata.time?.takeIf { it.isNotBlank() }?.let { textResource(Res.string.song_details_time, it) to false },
    ),
    style = style,
)
