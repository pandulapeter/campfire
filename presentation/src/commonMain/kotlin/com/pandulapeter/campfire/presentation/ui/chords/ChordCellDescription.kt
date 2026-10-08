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

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.chords.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_chord_diagram
import com.pandulapeter.campfire.presentation.resources.song_details_chord_diagram_none
import com.pandulapeter.campfire.presentation.resources.song_details_chord_letters
import com.pandulapeter.campfire.presentation.resources.song_details_chord_sounding
import com.pandulapeter.campfire.presentation.ui.components.textResource

/**
 * How a chord is read out: its name, the one it sounds as where that differs or else its letters where the page counts
 * it, and its shape as it would be dictated.
 */
@Composable
internal fun chordCellDescription(cell: ChordCell): String {
    val name = cell.soundingName?.let { textResource(Res.string.song_details_chord_sounding, cell.name, it) }
        ?: cell.letterName?.let { textResource(Res.string.song_details_chord_letters, cell.name, it) }
        ?: cell.name
    val shape = cell.selection.shape ?: return textResource(Res.string.song_details_chord_diagram_none, name)
    return textResource(Res.string.song_details_chord_diagram, name, spokenShape(shape))
}

/** A fretted shape as its frets from the lowest string, a keyboard one as the notes it presses from the lowest up. */
private fun spokenShape(shape: ChordVoicing) = when (shape) {
    is ChordVoicing.Fretted -> ChordVoicings.write(shape)
    is ChordVoicing.Keys -> (listOfNotNull(shape.bass) + shape.notes).joinToString(" ") { note ->
        ChordProChords.noteNames(Chord(root = note % 12, intervals = listOf(0))).first()
    }
}
