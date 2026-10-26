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
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.voicings.ChordProDefinitions
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.chordpro.model.ChordDefinition
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.model.ChordVoicing

/**
 * A chord of a song as its Chords section lists it.
 *
 * @property name The chord as the page names it, transposed and in the reader's notation: the chord that sounds.
 * @property chord The notes the page's name stands for, the chord that sounds, which its sheet's notes and scales are of.
 * @property shapeChord The chord the diagram's shape is fingered as above the [capo]: [chord] moved down by it (an A
 *   is a G shape at the second fret), the one a shape is looked up, picked, and stored for.
 * @property capo The fret the shape is played above, which its diagram draws it at (see `SongPlayback.shapeCapoFor`).
 * @property letterName The page's chord in letters where the page counts it as a step of the key, which the cell names
 *   after it too: a `6-` says nothing about the shape under it to a player who has not counted the key out yet.
 * @property spelling The chord the notes are spelled from, in the standard notation.
 * @property degree The step of the song's key the chord stands on, in Roman numerals, which the cell names after it where
 *   the page names chords by their letters (see [withDegrees]); null where it names none.
 * @property defaultShape The shape [shapeChord] is shown with where nothing else says how it is played, or null while
 *   [isShapePending].
 * @property definition The shape the song itself gives the chord on the instrument, where it gives one that is drawn,
 *   moved to be fingered above the [capo], see [songChordsOf].
 * @property undrawnDefinition The shape the song gives the chord where the tuning does not draw it
 *   ([ChordTuning.drawsDefinitions]): a standard tuning shape, which the chord's sheet says is there and offers to show.
 * @property isShapePending The default shape has not been looked for yet, see [songChordsOf]'s `searchesShapes`; drawn
 *   as the empty frame until it has.
 */
@Immutable
internal data class SongChord(
    val name: String,
    val chord: Chord,
    val shapeChord: Chord = chord,
    val capo: Int = 0,
    val letterName: String? = null,
    val spelling: String? = null,
    val degree: String? = null,
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
 *
 * The song is the one that sounds, and every shape is fingered above [capo] (see [SongChord.shapeChord]): a definition is
 * moved down the neck by it as a transposition moves one, so that a file's own shapes come back to where it wrote them
 * under the capo it names, and the app's and the player's shapes are those of the chord moved down by it.
 */
internal fun songChordsOf(
    song: ChordProSong,
    notation: ChordNotation,
    tuning: ChordTuning,
    searchesShapes: Boolean = true,
    capo: Int = 0,
): List<SongChord> {
    val shownNames = ChordProNotation.shownNames(song, notation)
    val definitions = song.metadata.definitions.filter { it.instrument == tuning.instrument }.map(ChordProDefinitions::sounding)
    fun definitionOf(name: String, chord: Chord) =
        (definitions.lastOrNull { it.name == name } ?: definitions.lastOrNull { ChordProChords.parse(it.name) == chord })
            ?.let { if (capo == 0) it else ChordProDefinitions.transposed(it, -capo) { shapeName -> shapeName } }
            ?.takeIf { it.movedBy == 0 || (it.voicing as? ChordVoicing.Fretted)?.frets?.let(ChordVoicings::isHoldable) != false }
    // One chord written two ways - (G) and G, a and Am, A# and Bb - is one diagram, under the name it is first written
    // with, unless the song defines the two spellings differently.
    val entries = shownNames.keys
        .mapNotNull { name -> ChordProChords.parse(name)?.let { chord -> Triple(name, chord, definitionOf(name, chord)) } }
        .distinctBy { (_, chord, definition) -> chord to definition?.let { it.voicing to it.movedBy } }
    if (entries.size > MAX_SONG_CHORDS) return emptyList()
    return entries.map { (name, chord, written) ->
        val definition = written?.takeIf { tuning.drawsDefinitions }
        val shapeChord = chord.movedBy(-capo)
        val isShapePending = definition == null && !searchesShapes && ChordVoicings.needsSearch(shapeChord, tuning)
        SongChord(
            name = ChordProNotation.withAccidentalSigns(shownNames.getValue(name)),
            chord = chord,
            shapeChord = shapeChord,
            capo = capo,
            letterName = name.takeIf { notation.isNumbering && shownNames[name] != name }?.let(ChordProNotation::withAccidentalSigns),
            spelling = name,
            defaultShape = if (definition == null && !isShapePending) ChordVoicings.default(shapeChord, tuning) else null,
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
    map { if (it.isShapePending) it.copy(defaultShape = ChordVoicings.default(it.shapeChord, tuning), isShapePending = false) else it }
}

/** [this] moved by [semitones], its root and bass with it: what a capo makes of a chord's shape. */
internal fun Chord.movedBy(semitones: Int) = if (semitones.mod(12) == 0) {
    this
} else {
    copy(root = (root + semitones).mod(12), bass = bass?.let { (it + semitones).mod(12) })
}

/**
 * [this] with each chord's [SongChord.degree] counted against [key], a key in the standard notation in the frame the
 * chords are in, the way [ChordNotation.ROMAN] writes it (`V7`, `♭VII`, `V/7`): every chord against the one key, a
 * chord off the key's scale by its chromatic step rather than as an applied chord, since the label says where a chord
 * stands, not what it leads to. None where [key] is null or no note, and none in a numbering, whose cells already name
 * the step first.
 */
internal fun List<SongChord>.withDegrees(key: String?, notation: ChordNotation): List<SongChord> {
    if (key == null || notation.isNumbering) return this
    return map { chord ->
        val degree = chord.spelling?.let { ChordProNotation.romanNumeral(it, key) } ?: return@map chord
        chord.copy(degree = ChordProNotation.withAccidentalSigns(degree))
    }
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

internal const val MAX_SONG_CHORDS = 48
