/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import com.pandulapeter.campfire.presentation.ui.dialogs.instrument.ChordShapeGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Every shape of [chord] in [tuning] for a chord's sheet ([ChordShapeGrid]), none for a chord the song defines
 * ([isDefined]), and null until they are found. The search is quick, but not quick enough for a frame, so it runs off
 * the main thread, and the sheet is put up once it is over: put up before, the sheet slid up with one empty frame and
 * grew to the whole grid on its way, composing every card in a frame of the slide. Shapes found in another tuning are
 * held while the new tuning's are searched for, so that the grid changes once; another chord's never are.
 */
@Composable
internal fun rememberChordShapes(
    chord: Chord?,
    tuning: ChordTuning,
    isDefined: Boolean,
): List<ChordVoicing>? {
    val searched by produceState<Pair<Chord, List<ChordVoicing>>?>(null, chord, tuning, isDefined) {
        if (chord != null) value = chord to if (isDefined) emptyList() else withContext(Dispatchers.Default) { ChordVoicings.all(chord, tuning) }
    }
    return searched?.takeIf { it.first == chord }?.second
}
