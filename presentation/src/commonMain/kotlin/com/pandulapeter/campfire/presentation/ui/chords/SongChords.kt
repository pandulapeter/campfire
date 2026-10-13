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
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.chords.ChordProDefinitions
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.chords.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.chordpro.model.ChordDefinition
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import com.pandulapeter.campfire.data.model.domain.UserPreferences

/**
 * A chord of a song as its Chords section lists it.
 *
 * @property name The chord as the page names it, transposed and in the reader's notation.
 * @property chord The notes the diagram is of. On the keyboard a capoed song's page is already in the chords that
 *   sound (see `withCapoSounding`), so these are the page's on every instrument.
 * @property letterName The page's chord in letters where the page counts it as a step of the key, which the cell names
 *   after it too: a `6-` says nothing about the shape under it to a player who has not counted the key out yet.
 * @property spelling The chord the notes are spelled from, in the standard notation.
 * @property defaultShape The shape [chord] is shown with where nothing else says how it is played, or null while
 *   [isShapePending].
 * @property definition The shape the song itself gives the chord on the instrument, where it gives one that is drawn,
 *   see [songChordsOf].
 * @property undrawnDefinition The shape the song gives the chord where the tuning does not draw it
 *   ([ChordTuning.drawsDefinitions]): a standard tuning shape, which the chord's sheet says is there and offers to show.
 * @property isShapePending The default shape has not been looked for yet, see [songChordsOf]'s `searchesShapes`; drawn
 *   as the empty frame until it has.
 */
@Immutable
internal data class SongChord(
    val name: String,
    val chord: Chord,
    val letterName: String? = null,
    val spelling: String? = null,
    val defaultShape: ChordVoicing? = null,
    val definition: ChordDefinition? = null,
    val undrawnDefinition: ChordDefinition? = null,
    val isShapePending: Boolean = false,
)

/**
 * The chords of [song], which is already transposed but still in the standard notation, each once by the name the page
 * shows in [notation] for the one it is first written with, however else the song spells it, and in the order they are
 * first played, as the song's Chords section lists them. Annotations, `N.C.` and anything else [ChordProChords] does
 * not read are not chords. A song of more than [MAX_SONG_CHORDS] chords is a songbook rather than a song, and gets none:
 * a section of fifty diagrams would be a page of its own before the first line.
 *
 * The chords are read before the notation is applied rather than out of what the page shows, since in a numbering a step
 * of the key says nothing about the notes without the stretch of the song it stands in; a chord played on both sides of
 * a modulation is one diagram, named by the step it is first played on.
 *
 * A chord is matched to the song's definition for the instrument by its name, which the transposition renames the
 * definition to as well, or else by the notes the two names stand for. A definition as the file writes it is drawn
 * whatever it asks of a hand, since asking for the unusual is what one is for; one a transposition moved only while a
 * hand can hold it, in the octave the move chose for exactly that, and otherwise the chord is drawn as if the song gave
 * it no shape. A definition is a shape in standard tuning, so in a tuning whose shapes are not the standard ones it is
 * not drawn either, and is only kept as [SongChord.undrawnDefinition].
 *
 * Without [searchesShapes] a chord whose default shape only the search finds is left [SongChord.isShapePending], for
 * [withSearchedShapes] to fill in away from the main thread: what builds a page in a frame cannot wait for a search.
 */
internal fun songChordsOf(
    song: ChordProSong,
    notation: ChordNotation,
    tuning: ChordTuning,
    searchesShapes: Boolean = true,
): List<SongChord> {
    val shownNames = ChordProNotation.shownNames(song, notation)
    val definitions = song.metadata.definitions.filter { it.instrument == tuning.instrument }.map(ChordProDefinitions::sounding)
    fun definitionOf(name: String, chord: Chord) =
        (definitions.lastOrNull { it.name == name } ?: definitions.lastOrNull { ChordProChords.parse(it.name) == chord })
            ?.takeIf { it.movedBy == 0 || (it.voicing as? ChordVoicing.Fretted)?.frets?.let(ChordVoicings::isHoldable) != false }
    // One chord written two ways - (G) and G, a and Am, A# and Bb - is one diagram, under the name it is first written
    // with, unless the song defines the two spellings differently.
    val entries = shownNames.keys
        .mapNotNull { name -> ChordProChords.parse(name)?.let { chord -> Triple(name, chord, definitionOf(name, chord)) } }
        .distinctBy { (_, chord, definition) -> chord to definition?.let { it.voicing to it.movedBy } }
    if (entries.size > MAX_SONG_CHORDS) return emptyList()
    return entries.map { (name, chord, written) ->
        val definition = written?.takeIf { tuning.drawsDefinitions }
        val isShapePending = definition == null && !searchesShapes && ChordVoicings.needsSearch(chord, tuning)
        SongChord(
            name = shownNames.getValue(name),
            chord = chord,
            letterName = name.takeIf { notation.isNumbering && shownNames[name] != name },
            spelling = name,
            defaultShape = if (definition == null && !isShapePending) ChordVoicings.default(chord, tuning) else null,
            definition = definition,
            undrawnDefinition = written?.takeIf { definition == null },
            isShapePending = isShapePending,
        )
    }
}

/** [this] with the shapes [songChordsOf] left to be looked for filled in: the search, so never on the main thread. */
internal fun List<SongChord>.withSearchedShapes(tuning: ChordTuning) = if (none { it.isShapePending }) {
    this
} else {
    map { if (it.isShapePending) it.copy(defaultShape = ChordVoicings.default(it.chord, tuning), isShapePending = false) else it }
}

/**
 * Whether [song] has the chords [songChordsOf] would list on an instrument with shapes: at least one, and no more than
 * [MAX_SONG_CHORDS]. Asked where there is no tuning to collect them for, so that the Chords section is there for the
 * same songs whatever the instrument.
 */
internal fun hasSongChords(song: ChordProSong, notation: ChordNotation): Boolean {
    val chords = ChordProNotation.shownNames(song, notation).keys.mapNotNull { ChordProChords.parse(it) }.distinct()
    return chords.isNotEmpty() && chords.size <= MAX_SONG_CHORDS
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
