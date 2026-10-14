/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songCard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer

/**
 * What the song cards of a list say after the artist: what the reader picked for the list ([of]'s `details`,
 * `UserPreferences.songCardDetails` or `setlistCardDetails`), within the features switched on. The chords switched off
 * take them out of the viewer, and the key is the shortest way of writing them down; the tempo is what the click plays,
 * so unless the reader picked it, it goes with the metronome.
 */
internal data class SongCardOptions(
    val shouldShowChords: Boolean,
    val isKeyShown: Boolean,
    val isTempoShown: Boolean,
    val isDurationShown: Boolean,
    val isCoverArtEnabled: Boolean,
    val chordSpelling: UserPreferences.ChordSpelling,
) {
    companion object {
        fun of(userPreferences: UserPreferences?, details: UserPreferences.CardDetails) = SongCardOptions(
            shouldShowChords = userPreferences?.areChordsEnabled != false,
            isKeyShown = details.isKeyShown,
            isTempoShown = details.isTempoShown(isMetronomeEnabled = userPreferences?.isMetronomeEnabled != false),
            isDurationShown = details.isDurationShown,
            isCoverArtEnabled = userPreferences?.isCoverArtEnabled == true,
            chordSpelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default,
        )
    }
}

/**
 * The key a card writes for [song] played at [transposition] with a capo on [capo]. Remembered, since a row is composed
 * again as every scroll starts and ends and it is a whole transposition to work out again.
 */
@Composable
internal fun rememberSongCardKey(
    songRenderer: SongRenderer,
    song: Song,
    transposition: Int,
    capo: Int,
    chordSpelling: UserPreferences.ChordSpelling,
) = remember(song.key, song.transpose, transposition, capo, chordSpelling) {
    songRenderer.renderKey(song = song, transposition = transposition, capo = capo, spelling = chordSpelling)
}
