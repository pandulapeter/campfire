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
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer

/**
 * How a song's page and its PDF name its chords where they are the shapes fingered above a capo rather than what sounds
 * ([SongPlayback.nameCapoFor]): the page's chords, its key changes, and its Chords section's names are the shapes',
 * while the key the song is played in is still named as it sounds, followed by the key of the shapes, so that the page
 * says both ("F♯m · Capo 2, Em shapes").
 *
 * @property capo The semitones the names are below what sounds, never 0.
 * @property shapesKey The key the shapes are in, in the reader's notation; null where the song names none and none was
 *   found in its chords.
 * @property soundingKey The key the song sounds in, in the reader's notation, which the page's line of values and the PDF
 *   name in place of the key its chords are written in.
 * @property inferredChordsKey The key a song naming none is counted from in a numbering, in the frame of the shapes and
 *   in the standard notation (`SongRenderer.movedKey`).
 */
@Immutable
internal data class ShapeNames(
    val capo: Int,
    val shapesKey: String?,
    val soundingKey: String?,
    val inferredChordsKey: String?,
)

/**
 * The [ShapeNames] of [song] played as [playback] says, for a reader with [preferences] spelling chords as [spelling]; null where its chords are named
 * as they sound, under no capo, a reader naming what sounds, or on the keyboard.
 *
 * @param inferredKey The key the song's chords were found in (`InferredSongKeys`), for a file that names none.
 */
internal fun SongRenderer.shapeNamesOf(
    song: Song,
    playback: SongPlayback,
    preferences: UserPreferences?,
    spelling: UserPreferences.ChordSpelling,
    inferredKey: String?,
): ShapeNames? {
    val capo = playback.nameCapoFor(preferences).takeIf { it != 0 } ?: return null
    val naming = playback.namingTransposition(preferences)
    return ShapeNames(
        capo = capo,
        shapesKey = renderKey(song = song, transposition = naming, spelling = spelling, inferredKey = inferredKey),
        soundingKey = renderKey(song = song, transposition = playback.soundingTransposition, spelling = spelling, inferredKey = inferredKey),
        inferredChordsKey = inferredKey?.takeIf { song.key.isNullOrBlank() }?.let { movedKey(inferredKey = it, transposition = naming, spelling = spelling) },
    )
}
