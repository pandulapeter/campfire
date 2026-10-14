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
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.preferences.UserPreferences

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

/**
 * Whether the capo is read as a transposition rather than as a fret: on the keyboard, which has no capo to put on, so
 * its player reads every chord as the one that sounds, see [withCapoSounding].
 */
internal val UserPreferences?.readsCapoAsTransposition get() = this?.instrument == UserPreferences.Instrument.KEYBOARD

/**
 * [this] as an instrument without a capo plays it (see [readsCapoAsTransposition]): the frets the capo raises the song by
 * are semitones of the transposition instead, wrapped around the octave as the stepper wraps, so the chords on the page,
 * their diagrams and the key next to the stepper are the ones the band hears, and no capo is left to read.
 */
internal fun SongPlayback.withCapoSounding() = copy(
    transposition = soundingTransposition(transposition, capo.fret),
    capo = EffectiveCapo(fret = 0, songFret = 0),
)

/** [this] as the player whose [preferences] these are reads it, see [readsCapoAsTransposition]. */
internal fun SongPlayback.shownFor(preferences: UserPreferences?) = if (preferences.readsCapoAsTransposition) withCapoSounding() else this

/** The transposition that takes a song played at [transposition] and capoed at [capo] to the chords that sound. */
internal fun soundingTransposition(transposition: Int, capo: Int) = wrapTransposition(transposition + capo)
