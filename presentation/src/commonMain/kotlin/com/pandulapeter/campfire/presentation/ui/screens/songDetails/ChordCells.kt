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
import com.pandulapeter.campfire.chordpro.ChordProChords
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.presentation.ui.chords.SelectedShape
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.chords.selectShape

/**
 * What a song's Chords section needs beyond the song: the instrument the diagrams are drawn for, the shapes the player
 * chose, whether the section is folded, and what its header does.
 *
 * @param onFoldToggled Folds the section or unfolds it, one preference for every song; null where nothing folds.
 * @param onShapesClicked Opens the Chord shapes sheet of the song, null in read only mode (performance mode, or a song
 * read from an archived setlist), which takes the controls off the page. Offered only while the section is unfolded.
 * @param showsDefinitionsOnly Whether the section holds the song's own definitions and nothing else, each on the
 * instrument it is written for, which is the editor's preview: it shows what is being written, and the app's own shapes
 * are not written. [notation] is what their names are read in.
 */
@Immutable
internal data class ChordDiagrams(
    val instrument: ChordInstrument,
    val storedShapes: Map<String, String> = emptyMap(),
    val isFolded: Boolean = false,
    val onFoldToggled: (() -> Unit)? = null,
    val onShapesClicked: (() -> Unit)? = null,
    val showsDefinitionsOnly: Boolean = false,
    val notation: ChordNotation = ChordNotation.STANDARD,
)

/**
 * One diagram of the Chords section: the chord as the page names it and the shape it is drawn with, and the names
 * [SongChord.soundingName] and [SongChord.letterName] give it after that.
 */
@Immutable
internal data class ChordCell(
    val name: String,
    val soundingName: String?,
    val letterName: String? = null,
    val instrument: ChordInstrument,
    val root: Int,
    val selection: SelectedShape,
)

/** The cells of [chords], each with the shape [selectShape] picks for it. */
internal fun chordCellsOf(chords: List<SongChord>, instrument: ChordInstrument, storedShapes: Map<String, String>) = chords.map { chord ->
    ChordCell(
        name = chord.name,
        soundingName = chord.soundingName,
        letterName = chord.letterName,
        instrument = instrument,
        root = chord.chord.root,
        selection = selectShape(chord, instrument, storedShapes),
    )
}

/**
 * The cells of every definition [song] holds, in file order and each on its own instrument, see
 * [ChordDiagrams.showsDefinitionsOnly]. A numbering leaves a song's definitions in letters, which is how they are named.
 */
internal fun definitionCellsOf(song: ChordProSong, notation: ChordNotation) = song.metadata.definitions.map { definition ->
    ChordCell(
        name = definition.name,
        soundingName = null,
        instrument = definition.instrument,
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
