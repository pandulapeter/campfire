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
import com.pandulapeter.campfire.chordpro.chords.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordVoicing

/** The shape a chord is drawn with, and where it came from. */
@Immutable
internal data class SelectedShape(
    val shape: ChordVoicing?,
    val source: Source,
    /** How many frets a transposition moved a [Source.DEFINED] shape by, which the sheet says. */
    val movedBy: Int = 0,
) {

    enum class Source {
        /** The song's own `{define}`, which has no stepper: the song says how it is played, the editor is where that changes. */
        DEFINED,

        /** The player's own choice of shape for the chord on the instrument, from the Chord shapes sheet. */
        PLAYER,

        /** The app's own first shape of the chord, or no shape at all where it has none. */
        DEFAULT,
    }
}

/**
 * The shape [chord] is drawn with on [instrument]: the song's own definition of it where there is one that is drawn
 * (see [songChordsOf]), the player's stored one for the chord ([storedShapes], by the chord's id) where it still reads as
 * a shape of that instrument, and otherwise the app's own.
 */
internal fun selectShape(
    chord: SongChord,
    instrument: ChordInstrument,
    storedShapes: Map<String, String>,
): SelectedShape {
    chord.definition?.let { return SelectedShape(it.voicing, SelectedShape.Source.DEFINED, it.movedBy) }
    storedShapes[chord.chord.id]?.let { ChordVoicings.read(it, instrument, chord.chord) }?.let { return SelectedShape(it, SelectedShape.Source.PLAYER) }
    return SelectedShape(chord.defaultShape, SelectedShape.Source.DEFAULT)
}
