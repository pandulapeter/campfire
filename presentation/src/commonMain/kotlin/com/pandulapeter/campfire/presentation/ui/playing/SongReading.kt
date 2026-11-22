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
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer
import com.pandulapeter.campfire.presentation.ui.tabs.TabReading

/**
 * A song read for a reader naming its chords as [shapeNames] says, the one reading the page, the PDF, and the chord shape
 * sheet share, so that what each of them names a chord by is the same.
 *
 * @property transposed What sounds, in the standard notation: what the scales, the degrees, and the diagrams are read from.
 * @property named What the page names: the same instance as [transposed] where [shapeNames] is null.
 * @property shapeNames The one the song was read with, so that [shownSongOf] names the key that sounds from the same value.
 * @property namedInferredKey The key a song naming none is counted from in a numbering, in the frame of [named].
 */
@Immutable
internal data class SongReading(
    val transposed: ChordProSong,
    val named: ChordProSong,
    val shapeNames: ShapeNames?,
    val namedInferredKey: String?,
) {

    /** The semitones [named]'s chords are below what sounds, 0 where they are named as they sound. */
    val nameCapo: Int get() = shapeNames?.capo ?: 0
}

/**
 * [text] read as it sounds, moved by [transposition] with [tabCapo] of its semitones left out of a tab's frets
 * (`SongRenderer.transposedSong`), and, where [shapeNames] names the shapes, read a second time moved down by their capo
 * with its tabs' frets left where they are: moving the song that sounds instead would apply its own key changes twice.
 *
 * @param inferredChordsKey The key the song's chords were found in for a file that names none, in the standard notation
 *   and in the frame of the sounding chords (`SongRenderer.movedKey`).
 */
internal fun SongRenderer.songReadingOf(
    text: String,
    transposition: Int,
    tabCapo: Int,
    spelling: UserPreferences.ChordSpelling,
    tabs: TabReading?,
    shapeNames: ShapeNames?,
    inferredChordsKey: String?,
): SongReading {
    val transposed = transposedSong(text, transposition, spelling, tabs = tabs, capo = tabCapo)
    val named = shapeNames?.let { transposedSong(text, transposition - it.capo, spelling, tabs = tabs, capo = tabCapo - it.capo) } ?: transposed
    return SongReading(
        transposed = transposed,
        named = named,
        shapeNames = shapeNames,
        namedInferredKey = if (shapeNames == null) inferredChordsKey else shapeNames.inferredChordsKey,
    )
}

/**
 * [SongReading.named] as the page and the PDF show it: [tempo] and [capo] put on, notated, and its key the one that
 * sounds, since the song is played in that key whatever its chords are named by.
 *
 * @param inferredKey The sounding key the song's chords were found in, in the reader's notation, for a song naming none.
 */
internal fun SongRenderer.shownSongOf(
    reading: SongReading,
    spelling: UserPreferences.ChordSpelling,
    tempo: Int?,
    capo: Int?,
    inferredKey: String?,
): ChordProSong {
    val song = notatedSong(reading.named.withTempo(tempo).withCapo(capo), spelling, reading.namedInferredKey)
    val key = reading.shapeNames?.soundingKey ?: inferredKey?.takeIf { song.metadata.key.isNullOrBlank() }
    return key?.let { song.copy(metadata = song.metadata.copy(key = it)) } ?: song
}
