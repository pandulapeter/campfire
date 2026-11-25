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
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata

/**
 * The first section of the song, which only the editor's preview draws: the card of what the song is, whose Song
 * defaults tiles hold how it is played, headed by the instrument and tuning pill where it has one. A whole, uncuttable
 * section. Song details draws none of it: its title opens the card's sheet, and its dock holds how the song is played
 * (`screens/songDetails/dock`).
 *
 * @param instrumentPill The instrument and tuning pill that starts the section, since it is as much about how the whole
 * song is played as the values are (see [withChordsSection]); null where there is none. It fades in and out rather than
 * the section growing towards it, since the layout reads the section's size as it is measured.
 */
@Composable
internal fun SongMetadataSection(
    modifier: Modifier = Modifier,
    metadata: ChordProMetadata,
    songInfoEditing: SongInfoEditing?,
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
    SongInfoCard(
        metadata = metadata,
        editing = songInfoEditing,
        fontScale = fontScale,
    )
}

private class LastInstrumentPill(var content: (@Composable () -> Unit)? = null)

/** Under the instrument pill where it has a line of its own. */
private val INSTRUMENT_PILL_GAP = 8.dp
