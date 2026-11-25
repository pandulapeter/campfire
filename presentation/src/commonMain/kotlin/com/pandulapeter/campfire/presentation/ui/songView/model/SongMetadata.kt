/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.model

import com.pandulapeter.campfire.chordpro.ChordProTempo
import com.pandulapeter.campfire.chordpro.model.ChordProMetadata
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.presentation.mapper.timeSignatureOf
import com.pandulapeter.campfire.presentation.ui.songInfo.hasSongInfo

/**
 * What the song says about itself is the first section of its own grid, so it shares the rows, columns, stepping and
 * motion of the lyrics rather than taking their full width away above them: the card of what the song is, which only the
 * editor's preview draws (song details opens it as a sheet from its title, and holds how it is played in its dock).
 *
 * @param shouldShowChords False with the chords switched off, which leaves out the key and the capo along with the
 * chords: they are what is played, and say nothing to somebody who is only singing.
 * @param isCapoShown False to leave the capo alone out, see `SongPlayback.isCapoShownFor`.
 * @param shouldShowTempo False with the metronome switched off, which leaves out the tempo and the time signature: they
 * are what the click plays, and say nothing without one.
 * @param isSongInfoEditable Whether that card has edit buttons, which puts it there for a song that says nothing about
 * itself yet too: it is where its first tag or link is added.
 */
internal fun withMetadataSection(
    sections: List<RenderSection>,
    metadata: ChordProMetadata,
    shouldShowChords: Boolean,
    isCapoShown: Boolean = true,
    shouldShowTempo: Boolean = true,
    isSongInfoEditable: Boolean = false,
): List<RenderSection> {
    // Read the way the click and the steppers read them, so that the card says what is played: a number of beats a
    // minute within the click's range, a time signature it can count ("C" being 4/4) and a capo on the neck.
    val shownMetadata = metadata.copy(
        key = metadata.key.takeIf { shouldShowChords },
        capo = if (!shouldShowChords || !isCapoShown) null else metadata.capo?.coerceIn(Song.CAPO_RANGE),
        tempo = if (!shouldShowTempo) null else ChordProTempo.parse(metadata.tempo)?.let(MetronomePattern::coerceBpm)?.toString(),
        time = if (!shouldShowTempo) null else timeSignatureOf(metadata.time)?.toString(),
    )
    return if (isSongInfoEditable || shownMetadata.hasSongInfo(areAttachmentsShown = false) || shownMetadata.hasPlayingValues) {
        listOf(RenderSection.Metadata(metadata = shownMetadata)) + sections
    } else {
        sections
    }
}

private val ChordProMetadata.hasPlayingValues
    get() = !key.isNullOrBlank() || (capo ?: 0) != 0 || !tempo.isNullOrBlank() || !time.isNullOrBlank()
