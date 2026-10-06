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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.ChordProChords
import com.pandulapeter.campfire.chordpro.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.Chord
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shapes
import com.pandulapeter.campfire.presentation.resources.song_details_chord_diagram
import com.pandulapeter.campfire.presentation.resources.song_details_chord_diagram_none
import com.pandulapeter.campfire.presentation.resources.song_details_chord_letters
import com.pandulapeter.campfire.presentation.resources.song_details_chord_sounding
import com.pandulapeter.campfire.presentation.resources.song_details_chords
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.ui.chords.SelectedShape
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.chords.chordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.emptyChordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.selectShape
import com.pandulapeter.campfire.presentation.ui.components.ChordDiagram
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import org.jetbrains.compose.resources.painterResource

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

/**
 * The song's chords as diagrams, headed by a pill that folds them like any section's. The cells flow and wrap rather
 * than scroll sideways, since a song may be read with a pedal, and take no press: a tap on them turns the page like a
 * tap anywhere on the song.
 */
@Composable
internal fun SongChordsSection(
    modifier: Modifier = Modifier,
    section: RenderSection.Chords,
    chordDiagrams: ChordDiagrams?,
    headerStyle: TextStyle,
    chordStyle: TextStyle,
    fontScale: Float,
) = Column(modifier = modifier) {
    var hasBeenToggled by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeaderPill(
            header = stringResource(Res.string.song_details_chords),
            toggle = chordDiagrams?.onFoldToggled?.let { onFoldToggled ->
                FoldToggle(
                    isExpanded = !section.isFolded,
                    onToggled = {
                        hasBeenToggled = true
                        onFoldToggled()
                    },
                )
            },
            style = headerStyle,
            chevronSize = FOLD_CHEVRON_SIZE * fontScale,
        )
        // As tall as the pill and growing with it, like the controls of the song's first section. It comes and goes with
        // the diagrams, since choosing a shape is choosing between the diagrams it would be drawn next to.
        chordDiagrams?.onShapesClicked?.let { onShapesClicked ->
            AnimatedVisibility(
                visible = !section.isFolded,
                // Unclipped, since a clip cuts the round button off while the room for it opens and closes: the scale is
                // what shows it arriving instead.
                enter = fadeIn() + scaleIn() + expandHorizontally(clip = false),
                exit = fadeOut() + scaleOut() + shrinkHorizontally(clip = false),
            ) {
                val height = songControlHeight(headerStyle)
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides height) {
                    IconButton(
                        modifier = Modifier.size(height),
                        onClick = onShapesClicked,
                    ) {
                        Icon(
                            modifier = Modifier.size(SHAPES_ICON_SIZE * fontScale),
                            painter = painterResource(Res.drawable.ic_edit),
                            contentDescription = stringResource(Res.string.song_details_chord_shapes),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
    if (section.isFolded) return@Column
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .fadingIn(isFadingIn = hasBeenToggled)
            .padding(top = HEADER_GAP),
        horizontalArrangement = Arrangement.spacedBy(CELL_GAP * fontScale),
        verticalArrangement = Arrangement.spacedBy(CELL_GAP * fontScale),
    ) {
        section.cells.forEach { cell ->
            ChordCellContent(
                cell = cell,
                chordStyle = chordStyle,
                fontScale = fontScale,
            )
        }
    }
}

@Composable
private fun ChordCellContent(
    cell: ChordCell,
    chordStyle: TextStyle,
    fontScale: Float,
) {
    val description = chordCellDescription(cell)
    Column(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = cell.name,
                style = chordStyle,
                color = LocalSecondAccentColor.current,
                maxLines = 1,
                overflow = TextOverflow.Visible,
            )
            (cell.soundingName ?: cell.letterName)?.let {
                Text(
                    modifier = Modifier.padding(start = 4.dp),
                    text = it,
                    style = chordStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        val shape = cell.selection.shape
        val geometry = remember(shape, cell.instrument, cell.root) {
            shape?.let { chordDiagramGeometryOf(it, cell.instrument, cell.root) } ?: emptyChordDiagramGeometryOf(cell.instrument)
        }
        ChordDiagram(
            modifier = Modifier.size(
                width = (if (cell.instrument.isFretted) FRETTED_WIDTH else KEYBOARD_WIDTH) * fontScale,
                height = (if (cell.instrument.isFretted) FRETTED_HEIGHT else KEYBOARD_HEIGHT) * fontScale,
            ),
            geometry = geometry,
            description = description,
        )
    }
}

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

private val CELL_GAP = 6.dp
private val SHAPES_ICON_SIZE = 18.dp
private val FRETTED_WIDTH = 56.dp
private val FRETTED_HEIGHT = 70.dp
private val KEYBOARD_WIDTH = 76.dp
private val KEYBOARD_HEIGHT = 40.dp
