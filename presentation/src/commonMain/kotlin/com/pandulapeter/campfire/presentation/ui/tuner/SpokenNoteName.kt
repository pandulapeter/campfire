/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tuner

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.tuner_note_sharp_letter
import com.pandulapeter.campfire.presentation.resources.tuner_note_sharp_syllable

/** [spokenNoteNameWithOctave] in the app's language: the solfège syllables take the sign's name, letters a suffix. */
@Composable
internal fun spokenNoteName(note: Int, notation: ChordNotation): String {
    val name = noteName(note, notation)
    val sharp = if (name.endsWith('#')) {
        stringResource(
            if (notation == ChordNotation.LATIN) Res.string.tuner_note_sharp_syllable else Res.string.tuner_note_sharp_letter,
            name.dropLast(1),
        )
    } else {
        null
    }
    return spokenNoteNameWithOctave(note, notation) { sharp ?: it }
}
