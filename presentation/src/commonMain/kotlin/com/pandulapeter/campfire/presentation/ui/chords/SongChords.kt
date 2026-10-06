/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chords

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.ChordProChords
import com.pandulapeter.campfire.chordpro.ChordProTransposer
import com.pandulapeter.campfire.chordpro.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import com.pandulapeter.campfire.data.model.domain.UserPreferences

/**
 * A chord of a song as its Chords section lists it.
 *
 * @property name The chord as the page names it, transposed and in the reader's notation.
 * @property chord The notes the diagram is of: the page's chord, or on the keyboard of a capoed song the chord that
 *   sounds, since a keyboard has no capo to play the page's shape above.
 * @property soundingName The name of [chord] where it is not the one the page shows, which the cell names after it.
 * @property defaultShape The shape [chord] is shown with where nothing else says how it is played, worked out with the
 *   rest of the song away from the main thread.
 */
@Immutable
internal data class SongChord(
    val name: String,
    val chord: Chord,
    val soundingName: String? = null,
    val defaultShape: ChordVoicing? = null,
)

/**
 * The chords of [song], which is already transposed and in [notation], each once by the name the page shows and in the
 * order they are first played, as the song's Chords section lists them. Annotations, `N.C.` and anything else
 * [ChordProChords] does not read are not chords. A song of more than [MAX_SONG_CHORDS] chords is a songbook rather than
 * a song, and gets none: a section of fifty diagrams would be a page of its own before the first line.
 *
 * On the keyboard [capo] moves every chord to the one that sounds, see [SongChord.chord].
 */
internal fun songChordsOf(
    song: ChordProSong,
    notation: ChordNotation,
    instrument: ChordInstrument,
    capo: Int = 0,
): List<SongChord> {
    val parsed = ChordProChords.namesIn(song).mapNotNull { name -> ChordProChords.parse(name, notation)?.let { name to it } }
    if (parsed.size > MAX_SONG_CHORDS) return emptyList()
    val soundingShift = if (instrument == ChordInstrument.KEYBOARD) capo.mod(12) else 0
    val preferFlats = soundingShift != 0 && ChordProTransposer.prefersFlats(song, soundingShift)
    return parsed.map { (name, chord) ->
        val sounding = if (soundingShift == 0) chord else chord.copy(root = (chord.root + soundingShift) % 12, bass = chord.bass?.let { (it + soundingShift) % 12 })
        SongChord(
            name = name,
            chord = sounding,
            soundingName = if (soundingShift == 0) null else ChordProChords.transposedName(name, soundingShift, notation, preferFlats),
            defaultShape = ChordVoicings.default(sounding, instrument),
        )
    }
}

/** `:chordpro`'s instrument for the one the preferences name. */
internal fun UserPreferences.ChordInstrument.toChordInstrument() = when (this) {
    UserPreferences.ChordInstrument.GUITAR -> ChordInstrument.GUITAR
    UserPreferences.ChordInstrument.UKULELE -> ChordInstrument.UKULELE
    UserPreferences.ChordInstrument.KEYBOARD -> ChordInstrument.KEYBOARD
}

/** `:chordpro`'s notation for the one the preferences name. */
internal fun UserPreferences.Notation.toChordNotation() = when (this) {
    UserPreferences.Notation.STANDARD -> ChordNotation.STANDARD
    UserPreferences.Notation.GERMAN -> ChordNotation.GERMAN
}

internal const val MAX_SONG_CHORDS = 48
