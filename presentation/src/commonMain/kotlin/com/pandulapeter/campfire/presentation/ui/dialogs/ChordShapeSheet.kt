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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.chords.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_chord_defined_standard_tuning
import com.pandulapeter.campfire.presentation.resources.song_details_chord_defined_standard_tuning_hide
import com.pandulapeter.campfire.presentation.resources.song_details_chord_defined_standard_tuning_show
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shape_choose
import com.pandulapeter.campfire.presentation.resources.song_details_chord_tuning
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.instrument.chordTuning
import com.pandulapeter.campfire.presentation.ui.chords.chordTuningLabel
import com.pandulapeter.campfire.presentation.ui.chords.selectShape
import com.pandulapeter.campfire.presentation.ui.chords.songChordsOf
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.playing.effectiveCapo
import com.pandulapeter.campfire.presentation.ui.playing.readsCapoAsTransposition
import com.pandulapeter.campfire.presentation.ui.playing.soundingTransposition
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
    val playedTransposition = transpositions[song.fileName, dialog.setlistFileName]
    val transposition = if (userPreferences.readsCapoAsTransposition) {
        soundingTransposition(playedTransposition, effectiveCapo(song = song, setlistFileName = dialog.setlistFileName, capos = capos).fret)
    } else {
        playedTransposition
    }
    val chords by produceState<List<SongChord>?>(null, text, transposition, spelling, tuning) {
        value = text?.let { withContext(Dispatchers.Default) { songChordsOf(viewModel.songRenderer.transposedSong(it, transposition, spelling), notation, tuning) } }
    }
    val chord = chords?.firstOrNull { it.name == dialog.chordName }
    // The page names the chord as the song is played there, so a transposition or an edit synced in while the sheet is up
    // may leave nothing by that name: the diagram that was tapped is gone, and with it the sheet.
    if (chords != null && chord == null) return LaunchedEffect(Unit) { viewModel.dismissSheet(dialog) }
    val storedShapes = userPreferences?.chordVoicings?.get(tuning.voicingKey).orEmpty()
    CampfireBottomSheet(
        // A numbering names the chord by its step, which reads as another chord in letters (`b7`), so it stays as it is.
        title = textResource(
            Res.string.song_details_chord_shape_choose,
            dialog.chordName.takeIf { notation.isNumbering } ?: ChordProNotation.canonicalName(dialog.chordName, notation) ?: dialog.chordName,
        ),
        // The notes rather than the song: the sheet is only ever opened from that song's page, and the notes say what the
        // shapes below have in common, which a line scrolled away with them would stop saying.
        subtitle = chord?.let { chordNoteNames(it, notation) }.orEmpty(),
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        val gridModifier = Modifier.weight(1f, fill = false)
        if (chord == null) {
            // The chords are worked out off the main thread, which is over before the sheet has slid up: the frame of a
            // diagram holds the place of the shapes meanwhile.
            ChordShapeGrid(
                modifier = gridModifier,
                chord = null,
                chordName = dialog.chordName,
                tuning = tuning,
                notation = notation,
                selection = null,
                tuningLine = tuningLine(tuning, notation),
                contentPadding = contentPadding,
                onShapeSelected = {},
            )
        } else {
            // The grid holds its chord's shapes while a new list of chords is worked out under the open sheet, so it is
            // keyed on the chord: its shapes are never handed to another one.
            key(chord.chord.id, chord.name) {
                var showsDefinition by rememberSaveable { mutableStateOf(false) }
                val definition = chord.undrawnDefinition?.takeIf { showsDefinition }
                val shownChord = if (definition == null) chord else chord.copy(definition = definition)
                val shownTuning = if (definition == null) tuning else ChordTuning.standard(tuning.instrument)
                key(showsDefinition) {
                    ChordShapeGrid(
                        modifier = gridModifier,
                        chord = shownChord,
                        chordName = shownChord.name,
                        tuning = shownTuning,
                        notation = notation,
                        selection = selectShape(shownChord, shownTuning, storedShapes),
                        tuningLine = tuningLine(shownTuning, notation),
                        contentPadding = contentPadding,
                        onShapeSelected = { shape -> viewModel.setChordVoicing(tuning.voicingKey, chord.chord.id, shape?.let(ChordVoicings::write)) },
                        footer = if (chord.undrawnDefinition == null) {
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
    }
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
