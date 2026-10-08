/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.playing

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.data.model.domain.Song

/** Every override of how a song is played, as one value the screens that read all three collect once. */
@Immutable
internal data class PlayingOverridesSnapshot(
    val transpositions: Transpositions = Transpositions(),
    val capos: Capos = Capos(),
    val tempos: Tempos = Tempos(),
)

/** How a song is played where it is opened: the transposition, the capo and the tempo, overrides applied. */
@Immutable
internal data class SongPlayback(
    val transposition: Int,
    val capo: EffectiveCapo,
    val tempo: EffectiveTempo,
)

/** [song] as it is played opened from [setlistFileName], or from the library for null, see [SongOverrides]. */
internal fun songPlaybackOf(song: Song, setlistFileName: String?, overrides: PlayingOverridesSnapshot) = SongPlayback(
    transposition = overrides.transpositions[song.fileName, setlistFileName],
    capo = effectiveCapo(song = song, setlistFileName = setlistFileName, capos = overrides.capos),
    tempo = effectiveTempo(song = song, setlistFileName = setlistFileName, tempos = overrides.tempos),
)
