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
import com.pandulapeter.campfire.chordpro.ChordProDefinitions
import com.pandulapeter.campfire.chordpro.ChordProNotation
import com.pandulapeter.campfire.chordpro.ChordProTransposer
import com.pandulapeter.campfire.chordpro.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.chordpro.model.ChordDefinition
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
 * @property letterName The page's chord in letters where the page counts it as a step of the key, which the cell names
 *   after it too: a `6-` says nothing about the shape under it to a player who has not counted the key out yet.
 * @property isSpelledWithFlats Whether the page spells the chord's root with a flat, which its notes are spelled with.
 * @property defaultShape The shape [chord] is shown with where nothing else says how it is played, worked out with the
 *   rest of the song away from the main thread.
 * @property definition The shape the song itself gives the chord on the instrument, where it gives one that is drawn,
 *   see [songChordsOf].
 */
@Immutable
internal data class SongChord(
    val name: String,
    val chord: Chord,
    val soundingName: String? = null,
    val letterName: String? = null,
    val isSpelledWithFlats: Boolean = false,
    val defaultShape: ChordVoicing? = null,
    val definition: ChordDefinition? = null,
)

/** What a cell names after the chord the page shows: the chord that sounds where it differs, or else its letters. */
internal val SongChord.secondaryName get() = soundingName ?: letterName

/**
 * The chords of [song], which is already transposed but still in the standard notation, each once by the name the page
 * shows in [notation] and in the order they are first played, as the song's Chords section lists them. Annotations,
 * `N.C.` and anything else [ChordProChords] does not read are not chords. A song of more than [MAX_SONG_CHORDS] chords
 * is a songbook rather than a song, and gets none: a section of fifty diagrams would be a page of its own before the
 * first line.
 *
 * The chords are read before the notation is applied rather than out of what the page shows, since in a numbering a step
 * of the key says nothing about the notes without the stretch of the song it stands in; a chord played on both sides of
 * a modulation is one diagram, named by the step it is first played on.
 *
 * On the keyboard [capo] moves every chord to the one that sounds, see [SongChord.chord], and the keys a definition
 * presses with it.
 *
 * A chord is matched to the song's definition for the instrument by its name, which the transposition renames the
 * definition to as well, or else by the notes the two names stand for. A definition as the file writes it is drawn
 * whatever it asks of a hand, since asking for the unusual is what one is for; one a transposition moved only while a
 * hand can hold it, in the octave the move chose for exactly that, and otherwise the chord is drawn as if the song gave
 * it no shape.
 */
internal fun songChordsOf(
    song: ChordProSong,
    notation: ChordNotation,
    instrument: ChordInstrument,
    capo: Int = 0,
): List<SongChord> {
    val shownNames = ChordProNotation.shownNames(song, notation)
    val parsed = shownNames.keys.mapNotNull { name -> ChordProChords.parse(name)?.let { name to it } }
    if (parsed.size > MAX_SONG_CHORDS) return emptyList()
    val soundingShift = if (instrument == ChordInstrument.KEYBOARD) capo.mod(12) else 0
    val preferFlats = soundingShift != 0 && ChordProTransposer.prefersFlats(song, soundingShift)
    val definitions = song.metadata.definitions.filter { it.instrument == instrument }
    return parsed.map { (name, chord) ->
        val sounding = if (soundingShift == 0) chord else chord.copy(root = (chord.root + soundingShift) % 12, bass = chord.bass?.let { (it + soundingShift) % 12 })
        val definition = (definitions.lastOrNull { it.name == name } ?: definitions.lastOrNull { ChordProChords.parse(it.name) == chord })
            ?.takeIf { it.movedBy == 0 || (it.voicing as? ChordVoicing.Fretted)?.frets?.let(ChordVoicings::isHoldable) != false }
            ?.let { if (soundingShift == 0) it else ChordProDefinitions.transposed(it, soundingShift) { name -> name } }
        SongChord(
            name = shownNames.getValue(name),
            chord = sounding,
            soundingName = if (soundingShift == 0) null else ChordProNotation.shownName(ChordProChords.transposedName(name, soundingShift, preferFlats = preferFlats), notation),
            letterName = name.takeIf { notation.isNumbering && shownNames[name] != name },
            isSpelledWithFlats = name.getOrNull(1) == 'b',
            defaultShape = if (definition == null) ChordVoicings.default(sounding, instrument) else null,
            definition = definition,
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
    UserPreferences.Notation.LATIN -> ChordNotation.LATIN
    UserPreferences.Notation.NASHVILLE -> ChordNotation.NASHVILLE
    UserPreferences.Notation.ROMAN -> ChordNotation.ROMAN
}

internal const val MAX_SONG_CHORDS = 48
