/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.scales

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.scales.ScaleChord
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicings
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.chords.shownChordName

/** One chord of the scale sheet's rows: the degree it stands on, over the chord as the Chords tab draws it. */
@Immutable
internal data class ScaleSheetChord(
    val numeral: String,
    val chord: SongChord,
)

/**
 * [chords], a scale's chords in the standard notation, as the cells the scale sheet draws on [tuning], named in
 * [notation]. The shape is the tables' only: a chord they lack is drawn as the empty frame ([SongChord.isShapePending])
 * rather than searched for while the sheet is composed, which the qualities a stack of thirds makes never need, and its
 * own sheet still finds every shape it has.
 */
internal fun scaleSheetChords(chords: List<ScaleChord>, notation: ChordNotation, tuning: ChordTuning) = chords.mapNotNull { scaleChord ->
    val chord = ChordProChords.parse(scaleChord.name) ?: return@mapNotNull null
    val isShapePending = ChordVoicings.needsSearch(chord, tuning)
    ScaleSheetChord(
        numeral = ChordProNotation.withAccidentalSigns(scaleChord.numeral),
        chord = SongChord(
            name = shownChordName(scaleChord.name, notation),
            chord = chord,
            spelling = scaleChord.name,
            defaultShape = if (isShapePending) null else ChordVoicings.default(chord, tuning),
            isShapePending = isShapePending,
        ),
    )
}
