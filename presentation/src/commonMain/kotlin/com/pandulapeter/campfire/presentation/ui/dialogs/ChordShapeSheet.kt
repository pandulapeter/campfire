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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.playing.effectiveCapo
import com.pandulapeter.campfire.presentation.ui.playing.readsCapoAsTransposition
import com.pandulapeter.campfire.presentation.ui.playing.soundingTransposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One chord of a song, opened by tapping its diagram in the Chords section: the chord larger than the section draws it,
 * with its fingers and its notes, on a pager through the other ways it can be played. The page it settles on is the
 * player's choice for that chord in that tuning in every song, written at once like the page's own steppers write,
 * so the sheet has nothing to save and its close button cancels nothing; the section behind it follows. It reads the song
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
        title = textResource(Res.string.song_details_chord_shape_choose, dialog.chordName),
        subtitle = songLabel(song),
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .fadingTopEdge(scrollState, sheetContainerColor())
                .bounceVerticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (chord == null) {
                // The chords are worked out off the main thread, which is over before the sheet has slid up: the frame
                // of a diagram and the room of the rest hold the sheet at its height meanwhile.
                ChordShapePlaceholder(instrument = tuning.instrument, chordName = dialog.chordName)
            } else {
                // The pager holds its chord's shapes while a new list of chords is worked out under the open sheet, so
                // it is keyed on the chord: its pages are never handed to another one.
                key(chord.chord.id, chord.name) {
                    var showsDefinition by rememberSaveable { mutableStateOf(false) }
                    val definition = chord.undrawnDefinition?.takeIf { showsDefinition }
                    val shownChord = if (definition == null) chord else chord.copy(definition = definition)
                    val shownTuning = if (definition == null) tuning else ChordTuning.standard(tuning.instrument)
                    if (!tuning.isStandard) {
                        ChordTuningLine(text = stringResource(Res.string.song_details_chord_tuning, chordTuningLabel(shownTuning, notation)))
                    }
                    key(showsDefinition) {
                        ChordShapePager(
                            chord = shownChord,
                            tuning = shownTuning,
                            notation = notation,
                            selection = selectShape(shownChord, shownTuning, storedShapes),
                            onShapeSelected = { shape -> viewModel.setChordVoicing(tuning.voicingKey, chord.chord.id, shape?.let(ChordVoicings::write)) },
                        )
                    }
                    if (chord.undrawnDefinition != null) {
                        UndrawnDefinitionNote(
                            isShown = showsDefinition,
                            onToggled = { showsDefinition = !showsDefinition },
                        )
                    }
                }
            }
        }
    }
}

/**
 * That the song defines the chord for standard tuning, which the tuning the sheet is in does not draw, with a button
 * showing the definition in the pager's place, and the tuning's shapes again.
 */
@Composable
private fun UndrawnDefinitionNote(
    isShown: Boolean,
    onToggled: () -> Unit,
) {
    Text(
        modifier = Modifier.padding(top = 12.dp),
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
