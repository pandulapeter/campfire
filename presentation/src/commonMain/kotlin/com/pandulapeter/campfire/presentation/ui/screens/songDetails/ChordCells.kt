/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.presentation.ui.chords.ChordCell
import com.pandulapeter.campfire.presentation.ui.chords.SelectedShape
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.chords.selectShape

/**
 * What a song's Chords section needs beyond the song: the tuning the diagrams are drawn for, the shapes the player
 * chose in it (under its [ChordTuning.voicingKey]), whether the section is folded, and what its header and its diagrams
 * do.
 *
 * @param onFoldToggled Folds the section or unfolds it, one preference for every song; null where nothing folds.
 * @param onChordClicked Opens the sheet of the chord a diagram was tapped on, by the name the page shows it with; null in
 * read only mode (performance mode, or a song read from an archived setlist), which takes the controls off the page.
 * @param onInstrumentClicked Opens the sheet of the instrument the diagrams are drawn for, from the button next to the
 * section's pill; null in read only mode, as [onChordClicked] is.
 * @param showsDefinitionsOnly Whether the section holds the song's own definitions and nothing else, each on the
 * instrument it is written for, which is the editor's preview: it shows what is being written, and the app's own shapes
 * are not written.
 * @param notation The reader's notation: what the chip names a tuning's note in, and what the preview's definitions'
 * names are read in.
 */
@Immutable
internal data class ChordDiagrams(
    val tuning: ChordTuning,
    val storedShapes: Map<String, String> = emptyMap(),
    val isFolded: Boolean = false,
    val onFoldToggled: (() -> Unit)? = null,
    val onChordClicked: ((chordName: String) -> Unit)? = null,
    val onInstrumentClicked: (() -> Unit)? = null,
    val showsDefinitionsOnly: Boolean = false,
    val notation: ChordNotation = ChordNotation.STANDARD,
)

/** The cells of [chords], each with the shape [selectShape] picks for it. */
internal fun chordCellsOf(chords: List<SongChord>, tuning: ChordTuning, storedShapes: Map<String, String>) = chords.map { chord ->
    ChordCell(
        name = chord.name,
        letterName = chord.letterName,
        tuning = tuning,
        root = chord.chord.root,
        selection = selectShape(chord, tuning, storedShapes),
    )
}

/**
 * The cells of every definition [song] holds, in file order and each on its own instrument in standard tuning, see
 * [ChordDiagrams.showsDefinitionsOnly]. A numbering leaves a song's definitions in letters, which is how they are named.
 */
internal fun definitionCellsOf(song: ChordProSong, notation: ChordNotation) = song.metadata.definitions.map { definition ->
    ChordCell(
        name = definition.name,
        tuning = ChordTuning.standard(definition.instrument),
        root = ChordProChords.parse(definition.name, notation)?.root ?: 0,
        selection = SelectedShape(definition.voicing, SelectedShape.Source.DEFINED, definition.movedBy),
    )
}

/**
 * [sections] with the Chords section after the metadata section, or first where there is none: the diagrams are part of
 * how the song is played, which the metadata section starts with. None where there is no chord to draw.
 */
internal fun withChordsSection(sections: List<RenderSection>, cells: List<ChordCell>, isFolded: Boolean): List<RenderSection> {
    if (cells.isEmpty()) return sections
    val index = if (sections.firstOrNull() is RenderSection.Metadata) 1 else 0
    return sections.take(index) + RenderSection.Chords(cells = cells, isFolded = isFolded) + sections.drop(index)
}
