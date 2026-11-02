/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.rendering

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.song.Song

/**
 * The key a card writes for [song] played at [transposition], the one that takes its chords to the ones that sound
 * (`SongPlayback.soundingTransposition`). Remembered, since a row is composed again as every scroll starts and ends and
 * it is a whole transposition to work out again.
 */
@Composable
internal fun rememberSongCardKey(
    songRenderer: SongRenderer,
    song: Song,
    transposition: Int,
    chordSpelling: UserPreferences.ChordSpelling,
) = remember(song.chordsKey, song.transpose, transposition, chordSpelling) {
    songRenderer.renderKey(song = song, transposition = transposition, spelling = chordSpelling)
}
