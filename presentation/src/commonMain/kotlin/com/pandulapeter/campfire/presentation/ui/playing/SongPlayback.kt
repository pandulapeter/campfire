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
import com.pandulapeter.campfire.data.model.preferences.ChordNames
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
 * and the effective capo, wrapped around the octave as the stepper wraps. The file's chords are the shapes fingered above
 * a capo, the file's own `{capo}` or the one the setlist or the device puts on instead, so every capo keeps the shapes
 * ([shapeCapoFor]) and raises the pitch. The key, the cards, and the scales name what sounds on every instrument; a song's
 * chords are named [nameCapoFor] lower ([namingTransposition]).
 */
internal val SongPlayback.soundingTransposition get() = soundingTransposition(transposition, capo.fret)

/**
 * The semitones below the chords that sound that a song's chords are named at, over the lyrics, in its comments, in its
 * Chords section, its key changes, and the PDF: the effective capo where the reader names the shapes fingered above it
 * ([namesShapes]), so that a capo moves no name there and only the transposition does; none where they name what sounds.
 */
internal fun SongPlayback.nameCapoFor(preferences: UserPreferences?) = if (preferences.namesShapes) capo.fret else 0

/** The semitones that take the file's chords to the ones a song's page names, see [nameCapoFor]. */
internal fun SongPlayback.namingTransposition(preferences: UserPreferences?) = soundingTransposition - nameCapoFor(preferences)

/**
 * The semitones of [soundingTransposition] a tab's frets are not moved by: a tab is counted from the capo it is played
 * above ([shapeCapoFor]), so it moves by the reader's transposition alone, as the shapes do. The octave the wrap took is
 * counted in, so that the frets move by the unwrapped amount.
 */
internal fun SongPlayback.tabCapoFor(preferences: UserPreferences?) = soundingTransposition - transposition - capo.fret + shapeCapoFor(preferences)

/**
 * The fret the shapes the player frets sit above, a chord's sounding name moved down by it naming its shape (a capo of 2
 * plays an A as a G shape): the effective capo, and none on an instrument without a capo ([playsWithoutCapo]), whose
 * shapes are the chords that sound.
 */
internal fun SongPlayback.shapeCapoFor(preferences: UserPreferences?) = if (preferences.playsWithoutCapo) 0 else capo.fret

/**
 * Whether the player's instrument takes no capo: the keyboard, whose diagrams and names are the sounding chords
 * themselves, the effective capo counted in as for every instrument. It still shows the capo and its stepper, since a
 * capo the band plays with raises the pitch for the pianist too.
 */
internal val UserPreferences?.playsWithoutCapo get() = this?.instrument == UserPreferences.Instrument.KEYBOARD

/**
 * Whether a song's chords are named by the shapes fingered above the capo ([ChordNames.SHAPES], the default, also before
 * the preferences are read) rather than by what sounds: never on the keyboard ([playsWithoutCapo]), fingered as it sounds.
 */
internal val UserPreferences?.namesShapes get() = !playsWithoutCapo && (this?.chordNames ?: ChordNames.SHAPES) == ChordNames.SHAPES

/** The transposition that takes a song played at [transposition] and capoed at [capo] to the chords that sound. */
internal fun soundingTransposition(transposition: Int, capo: Int) = wrapTransposition(transposition + capo)
