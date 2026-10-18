/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.chords

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.chords.SongChord

/**
 * The twelve keys the Chords tab lists chords in, from C, in the standard notation, as [accidentals] spells them: as a
 * chart spells each key where it keeps the song's own accidentals (`C#` but `Eb`, `F#` but `Bb`), and every one of them
 * sharp or flat where the reader asked for that.
 */
internal fun chordLibraryKeys(accidentals: UserPreferences.Accidentals) = when (accidentals) {
    UserPreferences.Accidentals.ORIGINAL -> listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
    UserPreferences.Accidentals.SHARPS -> listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    UserPreferences.Accidentals.FLATS -> listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
}

/**
 * What a key lists after its root, in the standard notation: the chords a chart uses, the everyday ones first and the
 * rest roughly by how often a song asks for them, each a different set of notes (`ChordLibraryTest` holds them to that),
 * and each among the ones the tables were filled with, so that every instrument has its players' own shapes for them.
 */
internal val CHORD_LIBRARY_QUALITIES = listOf(
    "", "m", "7", "maj7", "m7", "sus2", "sus4", "7sus4", "6", "m6", "add9", "madd9", "9", "maj9", "m9", "5", "dim", "dim7",
    "m7b5", "aug", "aug7", "mmaj7", "69", "m69", "11", "m11", "add11", "13", "maj13", "7b5", "7b9", "7#9", "7#11", "9b5",
    "maj7b5", "maj7#5", "mmaj9",
)

/** What the Chords tab shows under its controls. */
@Immutable
internal sealed interface ChordLibraryResults {

    /**
     * The chords to draw, each with the shape the app shows it with where nothing else says how it is played: [searched],
     * the chord the search names where the key does not list it, and the key's own [chords], under a heading that says
     * they are its common ones rather than all there are.
     */
    data class Chords(
        val searched: SongChord?,
        val chords: List<SongChord>,
    ) : ChordLibraryResults

    /** A search that names no chord and is part of the name of none of the chords the key lists. */
    data class NoChord(val query: String) : ChordLibraryResults
}

/** What the Chords tab's results are found for: the arguments of [chordLibraryResults]. */
@Immutable
internal data class ChordLibraryInputs(
    val keys: List<String>,
    val keyIndex: Int,
    val query: String,
    val notation: ChordNotation,
    val tuning: ChordTuning,
) {
    /** The key the results are the chords of, in the standard notation. */
    val key get() = keys[keyIndex.coerceIn(keys.indices)]
}

/**
 * The results the Chords tab draws, with the [inputs] they were found for: results held on screen while the next ones are
 * searched for are drawn on their own strings, since every chord carries the shape it was given for that tuning.
 */
@Immutable
internal data class ChordLibraryShown(
    val inputs: ChordLibraryInputs,
    val results: ChordLibraryResults?,
) {
    val tuning get() = inputs.tuning
}

/**
 * What the Chords tab opens on for [inputs], [held] being the results it was left on in this session: those where they
 * were found for the same inputs, then what the tables answer alone ([answerFromTables]), then the held results where
 * they are for the same tuning, kept on screen while the search answers the rest as within a visit, else nothing.
 */
internal fun initialChordLibraryShown(
    held: ChordLibraryShown?,
    inputs: ChordLibraryInputs,
    answerFromTables: () -> ChordLibraryResults?,
): ChordLibraryShown? {
    if (held?.inputs == inputs) return held
    answerFromTables()?.let { return ChordLibraryShown(inputs, it) }
    return held?.takeIf { it.tuning == inputs.tuning }
}

/**
 * The chords the Chords tab lists in the key at [keyIndex] of [keys] (see [chordLibraryKeys]) on [tuning], named in
 * [notation], narrowed by [query] to those whose name holds it, ignoring case (`m7`, `sus`, `cmaj`). A search that is a
 * whole chord name the key does not list is that chord too, apart from the key's, whatever key it is in ([searchedChord]):
 * a song's `D/F#` or `Emaj7` is then found by typing it, since no key lists every bass a chart puts under a chord. Every
 * name is the one the key row writes, its root in capitals (in title case in the Latin notation), whatever case the
 * search was typed in. A chord with no shape on the instrument is left out, since every cell is there to be played. A
 * numbering names the chords by their letters, as it names a chord with no song around it everywhere.
 *
 * This looks shapes up, and runs the search for the ones the tables lack, so it is called on the main thread only
 * without [searchesShapes], where it answers null for anything the tables cannot answer alone: what the tab opens on is
 * then there from its first frame, and the rest is worked out away from it.
 */
internal fun chordLibraryResults(
    keys: List<String>,
    keyIndex: Int,
    query: String,
    notation: ChordNotation,
    tuning: ChordTuning,
    searchesShapes: Boolean = true,
): ChordLibraryResults? {
    val typed = query.trim()
    val key = keys[keyIndex.coerceIn(keys.indices)]
    val searchedName = searchedChord(typed, notation)
    val names = listOfNotNull(searchedName) + CHORD_LIBRARY_QUALITIES.map { key + it }.filter { name ->
        ChordProNotation.shownName(name, notation).contains(typed, ignoreCase = true)
    }
    val found = names.mapNotNull { name ->
        val chord = ChordProChords.parse(name) ?: return@mapNotNull null
        if (!searchesShapes && ChordVoicings.needsSearch(chord, tuning)) return null
        val shape = ChordVoicings.default(chord, tuning) ?: return@mapNotNull null
        SongChord(name = ChordProNotation.shownName(name, notation), chord = chord, spelling = name, defaultShape = shape)
    }
    val searched = found.firstOrNull()?.takeIf { searchedName != null && it.spelling == searchedName }
    val chords = if (searched == null) found else found.drop(1)
    val separate = searched?.takeIf { chords.none { listed -> listed.chord == it.chord } }
    return if (separate == null && chords.isEmpty() && typed.isNotEmpty()) {
        ChordLibraryResults.NoChord(typed)
    } else {
        ChordLibraryResults.Chords(searched = separate, chords = chords)
    }
}

/**
 * The chord [typed] names in [notation], in the standard notation, or null where it names none. The root is read as a
 * capital whatever case it was typed in, as the rest of the search ignores case: a lowercase root would otherwise be read
 * as a minor (`d/f#` as `Dm/F#`), which a chart writes but nobody searching for `D/F#` means.
 */
private fun searchedChord(typed: String, notation: ChordNotation) = typed.takeIf { it.isNotEmpty() }
    ?.let { ChordProNotation.standardName(it.replaceFirstChar(Char::uppercaseChar), notation) }
