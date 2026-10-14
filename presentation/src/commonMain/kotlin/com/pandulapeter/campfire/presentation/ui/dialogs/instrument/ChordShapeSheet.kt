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

import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.rememberChordShapes
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_chord_defined_standard_tuning
import com.pandulapeter.campfire.presentation.resources.song_details_chord_defined_standard_tuning_hide
import com.pandulapeter.campfire.presentation.resources.song_details_chord_defined_standard_tuning_show
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shape_choose
import com.pandulapeter.campfire.presentation.resources.song_details_chord_tuning
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.chords.SelectedShape
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.instrument.chordTuning
import com.pandulapeter.campfire.presentation.ui.chords.chordTuningLabel
import com.pandulapeter.campfire.presentation.ui.chords.selectShape
import com.pandulapeter.campfire.presentation.ui.chords.songChordsOf
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.ui.instrument.storedChordShapes
import com.pandulapeter.campfire.presentation.ui.playing.PlayingOverridesSnapshot
import com.pandulapeter.campfire.presentation.ui.playing.shownFor
import com.pandulapeter.campfire.presentation.ui.playing.songPlaybackOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One chord of a song, opened by tapping its diagram in the Chords section: every way it can be played, as a grid of
 * diagrams larger than the section draws, with their fingers ([ChordShapeGrid]), under the chord's notes in the subtitle.
 * The shape picked there is the player's choice for that chord in that tuning in every song, written at once like the
 * page's own steppers write, so the sheet has nothing to save and its close button cancels nothing; the section behind it
 * follows. Its title
 * names the chord with its root in capitals, or in title case in Latin, however the song writes it. It reads the song
 * as the page shows it: transposed (on the keyboard by the capo too, see `withCapoSounding`) and spelled the same way,
 * and finds the chord by the name the page gave it. A tuning other than the standard one is named under the title, so
 * that a shape is never read for the wrong strings, and a chord the song defines for standard tuning, which such a
 * tuning does not draw, says so and offers to show the definition, named as standard tuning while it is shown.
 */
@Composable
internal fun ChordShapeSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.ChordShape,
) {
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val song = songs.firstOrNull { it.fileName == dialog.song.fileName } ?: dialog.song
    val songTexts by viewModel.songTexts.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val capos by viewModel.capos.collectAsStateWithLifecycle()
    // The sheet is opened from a song's Chords section, which an instrument without shapes leaves out, so only an
    // instrument changed while it was open gets here without a tuning; there are no shapes to offer then.
    val tuning = userPreferences.let { if (it == null) ChordTuning.GUITAR else it.chordTuning() } ?: return LaunchedEffect(Unit) { viewModel.dismissSheet(dialog) }
    val spelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
    val notation = spelling.notation.toChordNotation()
    val text = songTexts[song.fileName]
    val transposition = songPlaybackOf(
        song = song,
        setlistFileName = dialog.setlistFileName,
        overrides = PlayingOverridesSnapshot(transpositions = transpositions, capos = capos),
    ).shownFor(userPreferences).transposition
    val chords by produceState<List<SongChord>?>(null, text, transposition, spelling, tuning) {
        value = text?.let { withContext(Dispatchers.Default) { songChordsOf(viewModel.songRenderer.transposedSong(it, transposition, spelling), notation, tuning) } }
    }
    val chord = chords?.firstOrNull { it.name == dialog.chordName }
    // The page names the chord as the song is played there, so a transposition or an edit synced in while the sheet is up
    // may leave nothing by that name: the diagram that was tapped is gone, and with it the sheet.
    if (chords != null && chord == null) return LaunchedEffect(Unit) { viewModel.dismissSheet(dialog) }
    val storedShapes = userPreferences?.storedChordShapes(tuning).orEmpty()
    // Kept per chord, like the grid's own state: a chord that takes this one's place under the open sheet starts over.
    var showsDefinition by rememberSaveable(chord?.chord?.id, chord?.name) { mutableStateOf(false) }
    val definition = chord?.undrawnDefinition?.takeIf { showsDefinition }
    val shownChord = chord?.let { if (definition == null) it else it.copy(definition = definition) }
    val shownTuning = if (definition == null) tuning else ChordTuning.standard(tuning.instrument)
    val selection = shownChord?.let { selectShape(it, shownTuning, storedShapes) }
    val shapes = rememberChordShapes(shownChord?.chord, shownTuning, isDefined = selection?.source == SelectedShape.Source.DEFINED)
    // Put up once the song's chords and this one's shapes are worked out (see rememberChordShapes), and kept up after.
    val shapesArrival = remember { ShapesArrival() }
    if (shapes != null) shapesArrival.value = true
    if (!shapesArrival.value || shownChord == null) return
    CampfireBottomSheet(
        // A numbering names the chord by its step, which reads as another chord in letters (`b7`), so it stays as it is.
        title = textResource(
            Res.string.song_details_chord_shape_choose,
            dialog.chordName.takeIf { notation.isNumbering } ?: ChordProNotation.canonicalName(dialog.chordName, notation) ?: dialog.chordName,
        ),
        // The notes rather than the song: the sheet is only ever opened from that song's page, and the notes say what the
        // shapes below have in common, which a line scrolled away with them would stop saying.
        subtitle = chordNoteNames(shownChord, notation),
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        // The grid's own state (the marked shape, the scroll) is never handed to another chord, nor kept between the
        // tuning's shapes and the song's definition.
        key(shownChord.chord.id, shownChord.name, showsDefinition) {
            ChordShapeGrid(
                modifier = Modifier.weight(1f, fill = false),
                chord = shownChord,
                shapes = shapes,
                tuning = shownTuning,
                notation = notation,
                selection = selection,
                tuningLine = tuningLine(shownTuning, notation),
                contentPadding = contentPadding,
                onShapeSelected = { shape -> viewModel.setChordVoicing(tuning.voicingKey, shownChord.chord.id, shape?.let(ChordVoicings::write)) },
                footer = if (chord?.undrawnDefinition == null) {
                    null
                } else {
                    {
                        UndrawnDefinitionNote(
                            isShown = showsDefinition,
                            onToggled = { showsDefinition = !showsDefinition },
                        )
                    }
                },
            )
        }
    }
}

/** Whether a chord's sheet has had shapes to show, after which it stays up whatever comes. */
private class ShapesArrival {
    var value = false
}

/** The tuning the shapes are drawn for, named under the title where it is not the standard one. */
@Composable
private fun tuningLine(tuning: ChordTuning, notation: ChordNotation) =
    if (tuning.isStandard) null else stringResource(Res.string.song_details_chord_tuning, chordTuningLabel(tuning, notation))

/**
 * That the song defines the chord for standard tuning, which the tuning the sheet is in does not draw, with a button
 * showing the definition in the grid's place, and the tuning's shapes again.
 */
@Composable
private fun UndrawnDefinitionNote(
    isShown: Boolean,
    onToggled: () -> Unit,
) = Column(
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Text(
        modifier = Modifier.padding(top = 4.dp),
        text = stringResource(Res.string.song_details_chord_defined_standard_tuning),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
    TextButton(onClick = onToggled) {
        Text(
            text = stringResource(
                if (isShown) Res.string.song_details_chord_defined_standard_tuning_hide else Res.string.song_details_chord_defined_standard_tuning_show,
            ),
        )
    }
}
