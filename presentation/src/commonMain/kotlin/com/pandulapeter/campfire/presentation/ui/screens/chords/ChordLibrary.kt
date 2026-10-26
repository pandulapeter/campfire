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
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.chords.movedBy
import com.pandulapeter.campfire.presentation.ui.chords.shownChordName

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
    val capo: Int = 0,
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
 * The chords the Chords tab lists in the key at [keyIndex] of [keys] (see [libraryKeys]) on [tuning], named in
 * [notation], narrowed by [query] to those whose name holds it, ignoring case (`m7`, `sus`, `cmaj`). A search that is a
 * whole chord name the key does not list is that chord too, apart from the key's, whatever key it is in ([searchedChord]):
 * a song's `D/F#` or `Emaj7` is then found by typing it, since no key lists every bass a chart puts under a chord. Every
 * name is the one the key pill writes, its root in capitals (in title case in the Latin notation), whatever case the
 * search was typed in. A chord with no shape on the instrument is left out, since every cell is there to be played. A
 * numbering names the chords by their letters, as it names a chord with no song around it everywhere. Under a [capo] every
 * chord keeps its name, the one that sounds, and is the shape fingered above it (an A at the second fret a G shape, see
 * [SongChord.shapeChord]), as a capoed song's Chords section draws it; a chord whose shape the instrument lacks is left
 * out as before.
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
    capo: Int = 0,
): ChordLibraryResults? {
    val typed = query.trim()
    val key = keys[keyIndex.coerceIn(keys.indices)]
    val searchedName = searchedChord(typed, notation)
    val names = listOfNotNull(searchedName) + CHORD_LIBRARY_QUALITIES.map { key + it }.filter { name ->
        ChordProNotation.shownName(name, notation).contains(typed, ignoreCase = true)
    }
    val found = names.mapNotNull { name ->
        val chord = ChordProChords.parse(name) ?: return@mapNotNull null
        val shapeChord = chord.movedBy(-capo)
        if (!searchesShapes && ChordVoicings.needsSearch(shapeChord, tuning)) return null
        val shape = ChordVoicings.default(shapeChord, tuning) ?: return@mapNotNull null
        SongChord(name = shownChordName(name, notation), chord = chord, shapeChord = shapeChord, capo = capo, spelling = name, defaultShape = shape)
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
