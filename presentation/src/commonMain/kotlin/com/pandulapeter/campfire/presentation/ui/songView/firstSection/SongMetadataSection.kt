/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.firstSection

import com.pandulapeter.campfire.presentation.ui.songView.LocalSongChange
import com.pandulapeter.campfire.presentation.ui.songView.leavingWith
import com.pandulapeter.campfire.presentation.ui.songView.HEADER_HORIZONTAL_PADDING
import com.pandulapeter.campfire.presentation.ui.songView.model.withChordsSection
import com.pandulapeter.campfire.presentation.ui.songView.model.withMetadataSection
import com.pandulapeter.campfire.presentation.ui.songInfo.SongInfoCard
import com.pandulapeter.campfire.presentation.ui.songInfo.SongInfoEditing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_capo_shapes
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_time
import com.pandulapeter.campfire.presentation.localization.textResource

/**
 * The first section of the song: the key, capo, tempo and time read on one line, since they are what is played, or
 * where [isSongInfoShown] the card of what the song is, which holds them as its Song defaults tiles, headed by the
 * instrument and tuning pill where it has one. A whole, uncuttable section. Song details draws none of it: its dock
 * holds what it says (`screens/songDetails/dock`).
 *
 * @param readsDefaults Whether the line names a capo of none, and the default tempo and time signature, too, see
 * [withMetadataSection].
 * @param shapesKey The key of the shapes the chords are named by, which that line names after the capo, see
 * [withMetadataSection].
 * @param lyricsStyle The style the lyrics are set in, already scaled, which the line draws the four values in, so that in
 * the lyrics' size they read as part of what is played rather than as a caption above it; only the key is set as a
 * chord is, being one (see [playingValuesText]).
 * @param instrumentPill The instrument and tuning pill that starts the section, since it is as much about how the whole
 * song is played as the values are (see [withChordsSection]); null where there is none. It fades in and out rather than
 * the section growing towards it, since the layout reads the section's size as it is measured.
 */
@Composable
internal fun SongMetadataSection(
    modifier: Modifier = Modifier,
    metadata: ChordProMetadata,
    isSongInfoShown: Boolean,
    songInfoEditing: SongInfoEditing?,
    readsDefaults: Boolean,
    shapesKey: String?,
    lyricsStyle: TextStyle,
    fontScale: Float,
    instrumentPill: (@Composable () -> Unit)? = null,
) = Column(modifier = modifier) {
    // Kept while it fades out, so that it leaves as it was rather than as nothing.
    val lastPill = remember { LastInstrumentPill() }
    instrumentPill?.let { lastPill.content = it }
    val pillVisibility = remember { MutableTransitionState(instrumentPill != null) }
        .apply { targetState = instrumentPill != null }
    // A change of how the song is shown fades the pill out before the page changes and in after it has moved, so it
    // comes and goes at once then rather than fading a second time.
    val change = LocalSongChange.current
    AnimatedVisibility(
        modifier = Modifier.leavingWith(change, change.leaving?.instrumentPill == true),
        visibleState = pillVisibility,
        enter = if (change.isUnderWay) EnterTransition.None else fadeIn(),
        exit = if (change.isUnderWay) ExitTransition.None else fadeOut(),
    ) {
        // The pill has a line of its own over the rest, reaching out of the values' keyline as far as the section pills do.
        Box(modifier = Modifier.padding(start = 12.dp - HEADER_HORIZONTAL_PADDING, end = 12.dp, bottom = INSTRUMENT_PILL_GAP)) {
            lastPill.content?.invoke()
        }
    }
    if (isSongInfoShown) {
        SongInfoCard(
            metadata = metadata,
            editing = songInfoEditing,
            fontScale = fontScale,
        )
    } else {
        SongPlayingValues(
            values = playingValuesOf(metadata = metadata, readsDefaults = readsDefaults, shapesKey = shapesKey),
            style = lyricsStyle,
        )
    }
}

private class LastInstrumentPill(var content: (@Composable () -> Unit)? = null)

/** The four values of [metadata] the line reads, each with whether it is set as a chord is (the key). */
@Composable
private fun playingValuesOf(metadata: ChordProMetadata, readsDefaults: Boolean, shapesKey: String?) = listOfNotNull(
    metadata.key?.takeIf { it.isNotBlank() }?.let { it to true },
    metadata.capo?.takeIf { readsDefaults || it != 0 }?.let { capo ->
        // The key before it sounds, so the shapes the chords are named by are said with the capo they are played above.
        val capoText = stringResource(Res.string.song_details_capo, capo)
        (shapesKey?.let { textResource(Res.string.song_details_capo_shapes, capoText, it) } ?: capoText) to false
    },
    metadata.tempo?.takeIf { it.isNotBlank() }?.let { textResource(Res.string.song_details_tempo, it) to false },
    metadata.time?.takeIf { it.isNotBlank() }?.let { textResource(Res.string.song_details_time, it) to false },
)

/** Under the instrument pill where it has a line of its own. */
private val INSTRUMENT_PILL_GAP = 8.dp
