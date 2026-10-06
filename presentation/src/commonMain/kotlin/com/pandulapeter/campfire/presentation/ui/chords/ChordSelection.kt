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
import com.pandulapeter.campfire.chordpro.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordVoicing

/** The shape a chord is drawn with, and where it came from. */
@Immutable
internal data class SelectedShape(
    val shape: ChordVoicing?,
    val source: Source,
) {

    enum class Source {
        /** The player's own choice of shape for the chord on the instrument, from the Chord shapes sheet. */
        PLAYER,

        /** The app's own first shape of the chord, or no shape at all where it has none. */
        DEFAULT,
    }
}

/**
 * The shape [chord] is drawn with on [instrument]: the player's stored one for the chord ([storedShapes], by the
 * chord's id), where it still reads as a shape of that instrument, and otherwise the app's own.
 */
internal fun selectShape(
    chord: SongChord,
    instrument: ChordInstrument,
    storedShapes: Map<String, String>,
): SelectedShape {
    storedShapes[chord.chord.id]?.let { ChordVoicings.read(it, instrument, chord.chord) }?.let { return SelectedShape(it, SelectedShape.Source.PLAYER) }
    return SelectedShape(chord.defaultShape, SelectedShape.Source.DEFAULT)
}
