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
 * The semitones that take the file's chords to the ones that sound where the song is opened: the reader's transposition
 * and the file's own `{capo}`, wrapped around the octave as the stepper wraps. The file's capo only says which shapes the
 * file is written in, so it is pitch, while a capo the setlist or the device puts on instead keeps the pitch and changes
 * the shapes played under it ([shapeCapoFor]): every instrument names the chords, the key, and the scales that sound.
 */
internal val SongPlayback.soundingTransposition get() = soundingTransposition(transposition, capo.songFret)

/**
 * The semitones of [soundingTransposition] that are the file's capo rather than the reader's transposition, the octave
 * the wrap took included: what a tab, whose frets are counted from the capo, is not moved by.
 */
internal val SongPlayback.soundingCapo get() = soundingTransposition - transposition

/**
 * The fret the shapes the player frets sit above, a chord's sounding name moved down by it naming its shape (a capo of 2
 * plays an A as a G shape): the effective capo, and none on an instrument without a capo ([playsWithoutCapo]), whose
 * shapes are the chords that sound.
 */
internal fun SongPlayback.shapeCapoFor(preferences: UserPreferences?) = if (preferences.playsWithoutCapo) 0 else capo.fret

/**
 * Whether the player's instrument takes no capo: the keyboard, which has no capo stepper or capo line, and whose diagrams
 * are the sounding chords themselves. Overrides of the capo are kept for the other instruments.
 */
internal val UserPreferences?.playsWithoutCapo get() = this?.instrument == UserPreferences.Instrument.KEYBOARD

/** The transposition that takes a song played at [transposition] and capoed at [capo] to the chords that sound. */
internal fun soundingTransposition(transposition: Int, capo: Int) = wrapTransposition(transposition + capo)
