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

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.ChordProChords
import com.pandulapeter.campfire.chordpro.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordVoicing
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_next
import com.pandulapeter.campfire.presentation.resources.ic_previous
import com.pandulapeter.campfire.presentation.resources.song_details_chord_defined
import com.pandulapeter.campfire.presentation.resources.song_details_chord_defined_moved
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shape_next
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shape_position
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shape_previous
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shapes
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.chords.SelectedShape
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.chords.chordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.emptyChordDiagramGeometryOf
import com.pandulapeter.campfire.presentation.ui.chords.secondaryName
import com.pandulapeter.campfire.presentation.ui.chords.selectShape
import com.pandulapeter.campfire.presentation.ui.chords.songChordsOf
import com.pandulapeter.campfire.presentation.ui.chords.toChordInstrument
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.ChordDiagram
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.ChordCell
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.Stepper
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.chordCellDescription
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.effectiveCapo
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs

/**
 * The chords of a song larger than its Chords section draws them, each with its notes and a stepper through the other
 * ways it can be played. A step is the player's choice for that chord on that instrument in every song, written at
 * once like the page's own steppers write, so the sheet has nothing to save and its close button cancels nothing; the
 * section behind it follows. It reads the song as the page plays it: transposed, capoed and spelled the same way.
 */
@Composable
internal fun ChordShapesSheet(
    viewModel: CampfireViewModel,
    dialog: CampfireViewModel.DialogType.ChordShapes,
) {
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val song = songs.firstOrNull { it.fileName == dialog.song.fileName } ?: dialog.song
    val songTexts by viewModel.songTexts.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val capos by viewModel.capos.collectAsStateWithLifecycle()
    val instrumentPreference = userPreferences?.chordInstrument ?: UserPreferences.ChordInstrument.GUITAR
    val instrument = instrumentPreference.toChordInstrument()
    val spelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
    val notation = spelling.notation.toChordNotation()
    val text = songTexts[song.fileName]
    val transposition = transpositions[song.fileName, dialog.setlistFileName]
    val capo = effectiveCapo(song = song, setlistFileName = dialog.setlistFileName, capos = capos).fret
    val chords by produceState<List<SongChord>?>(null, text, transposition, capo, spelling, instrument) {
        value = text?.let { withContext(Dispatchers.Default) { songChordsOf(viewModel.transposedSong(it, transposition, spelling), notation, instrument, capo) } }
    }
    val storedShapes = userPreferences?.chordVoicings?.get(instrument.id).orEmpty()
    CampfireBottomSheet(
        title = stringResource(Res.string.song_details_chord_shapes),
        subtitle = songLabel(song),
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        val scrollState = rememberScrollState()
        FlowRow(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .fadingTopEdge(scrollState)
                .bounceVerticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(CELL_GAP, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(CELL_GAP),
        ) {
            chords.orEmpty().forEach { chord ->
                ChordShapeCell(
                    chord = chord,
                    instrument = instrument,
                    notation = notation,
                    selection = selectShape(chord, instrument, storedShapes),
                    onShapeSelected = { shape -> viewModel.setChordVoicing(instrumentPreference, chord.chord.id, shape?.let(ChordVoicings::write)) },
                )
            }
        }
    }
}

/**
 * One chord of the sheet: its name, its diagram with the fingers that hold it, its notes, and where it has more than one
 * shape, a stepper reading which of them it is on, going around like the transposition's. It is never highlighted and
 * has no reset: which shape is the app's first says nothing to a player who only picks the one their hands play.
 */
@Composable
private fun ChordShapeCell(
    chord: SongChord,
    instrument: ChordInstrument,
    notation: ChordNotation,
    selection: SelectedShape,
    onShapeSelected: (ChordVoicing?) -> Unit,
) = Column(
    modifier = Modifier.width(if (instrument.isFretted) FRETTED_CELL_WIDTH else KEYBOARD_CELL_WIDTH),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    // The search for every shape of one chord is quick, but a sheet of thirty of them is not, so each cell asks for its
    // own away from the main thread and has its stepper once they are there. A chord the song defines has none.
    val shapes by produceState<List<ChordVoicing>?>(null, chord.chord, instrument, selection.source) {
        value = if (selection.source == SelectedShape.Source.DEFINED) emptyList() else withContext(Dispatchers.Default) { ChordVoicings.all(chord.chord, instrument) }
    }
    val current = selection.shape
    // A stored shape the app does not list is still the player's, and comes first, before the app's own.
    val options = shapes?.let { all -> if (current == null || all.any { it.sameShapeAs(current) }) all else listOf(current) + all }
    val index = options?.indexOfFirst { current != null && it.sameShapeAs(current) } ?: -1
    // The app's own first shape is never stored as the player's: stepping around to it takes the chord's entry out, so
    // that the app's default is what plays there, rather than a copy of it that nothing would ever update.
    val defaultShape = shapes?.firstOrNull()
    val select = { shape: ChordVoicing -> onShapeSelected(shape.takeUnless { defaultShape?.sameShapeAs(it) == true }) }
    val cell = ChordCell(
        name = chord.name,
        soundingName = chord.soundingName,
        letterName = chord.letterName,
        instrument = instrument,
        root = chord.chord.root,
        selection = selection,
    )
    val description = chordCellDescription(cell)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = chord.name,
            style = MaterialTheme.typography.titleMedium,
            color = LocalSecondAccentColor.current,
            maxLines = 1,
        )
        chord.secondaryName?.let {
            Text(
                modifier = Modifier.padding(start = 6.dp),
                text = it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
    Crossfade(targetState = current) { shape ->
        ChordDiagram(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(
                    width = if (instrument.isFretted) FRETTED_WIDTH else KEYBOARD_WIDTH,
                    height = if (instrument.isFretted) FRETTED_HEIGHT else KEYBOARD_HEIGHT,
                ),
            geometry = remember(shape, instrument, chord.chord.root) {
                shape?.let { chordDiagramGeometryOf(it, instrument, chord.chord.root) } ?: emptyChordDiagramGeometryOf(instrument)
            },
            description = description,
            showsFingers = true,
        )
    }
    Text(
        modifier = Modifier.padding(top = 4.dp),
        text = ChordProChords.noteNames(chord.chord, notation, preferFlats = chord.isSpelledWithFlats).joinToString(" "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    if (selection.source == SelectedShape.Source.DEFINED) {
        Text(
            modifier = Modifier.padding(top = 8.dp).height(STEPPER_PLACEHOLDER_HEIGHT),
            text = if (selection.movedBy == 0) {
                stringResource(Res.string.song_details_chord_defined)
            } else {
                pluralStringResource(Res.plurals.song_details_chord_defined_moved, abs(selection.movedBy), abs(selection.movedBy))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    } else if (options != null && options.size > 1) {
        Stepper(
            modifier = Modifier.padding(top = 8.dp),
            value = stringResource(Res.string.song_details_chord_shape_position, index + 1, options.size),
            isDefault = true,
            decreaseIcon = painterResource(Res.drawable.ic_previous),
            decreaseLabel = textResource(Res.string.song_details_chord_shape_previous, chord.name),
            canDecrease = true,
            onDecrease = { select(options[(index - 1).mod(options.size)]) },
            increaseIcon = painterResource(Res.drawable.ic_next),
            increaseLabel = textResource(Res.string.song_details_chord_shape_next, chord.name),
            canIncrease = true,
            onIncrease = { select(options[(index + 1).mod(options.size)]) },
            resetLabel = null,
            onReset = null,
        )
    } else {
        // The stepper's room is kept while the shapes are looked for, so that the cells do not grow under the finger.
        Spacer(modifier = Modifier.padding(top = 8.dp).height(STEPPER_PLACEHOLDER_HEIGHT))
    }
}

/** Whether two shapes are fingered on the same frets or keys, whatever either says about the fingers. */
private fun ChordVoicing.sameShapeAs(other: ChordVoicing) = when (this) {
    is ChordVoicing.Fretted -> other is ChordVoicing.Fretted && frets == other.frets
    is ChordVoicing.Keys -> this == other
}

private val CELL_GAP = 16.dp
private val FRETTED_CELL_WIDTH = 132.dp
private val KEYBOARD_CELL_WIDTH = 176.dp
private val FRETTED_WIDTH = 84.dp
private val FRETTED_HEIGHT = 104.dp
private val KEYBOARD_WIDTH = 168.dp
private val KEYBOARD_HEIGHT = 72.dp
private val STEPPER_PLACEHOLDER_HEIGHT = 40.dp
