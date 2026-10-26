/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.instrument

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_capo
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shape_capo
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.chords.shownChordName

/**
 * The shape [chord] is fingered as and the capo it is played above, named under the title where there is a capo: the
 * shape by its chord's name in the reader's notation, and the capo alone in a numbering, where a step names no shape.
 */
@Composable
internal fun capoLine(chord: SongChord, notation: ChordNotation): String? {
    if (chord.capo == 0) return null
    val spelling = chord.spelling?.takeUnless { notation.isNumbering } ?: return stringResource(Res.string.song_details_capo, chord.capo)
    val shapeName = ChordProTransposer.transposeText("[$spelling]", -chord.capo).removeSurrounding("[", "]")
    return textResource(Res.string.song_details_chord_shape_capo, shownChordName(shapeName, notation), chord.capo.toString())
}
