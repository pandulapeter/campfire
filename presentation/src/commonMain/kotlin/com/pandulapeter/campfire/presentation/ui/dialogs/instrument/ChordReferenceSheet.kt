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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicings
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shape_choose
import com.pandulapeter.campfire.presentation.resources.song_details_chord_tuning
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.instrument.chordTuning
import com.pandulapeter.campfire.presentation.ui.chords.chordTuningLabel
import com.pandulapeter.campfire.presentation.ui.chords.SelectedShape
import com.pandulapeter.campfire.presentation.ui.chords.SongChord
import com.pandulapeter.campfire.presentation.ui.chords.selectShape
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.ui.instrument.storedChordShapes
import com.pandulapeter.campfire.presentation.ui.chords.ScaleChipRow
import com.pandulapeter.campfire.presentation.ui.navigation.FeatureSwitches

/**
 * A chord of the Chords tab with every way it can be played, the song's chord sheet ([ChordShapeSheet]) with no song
 * around it: titled the same way, naming a tuning that is not the standard one under the title, and writing the shape
 * picked in its grid as the player's shape for the chord in that tuning at once, so that the tab and every song follow
 * it. Under the Chords tab's capo, or a song's capo for a chord of its scale sheet, its shapes are the ones fingered above
 * it, named under the title as the song's sheet names them (`G shape, capo 2`), and the one picked is stored for the
 * shape's chord ([SongChord.shapeChord]). The tuning is the one the tab drew the chord for, which nothing can change while the sheet covers the tab; an
 * instrument without shapes, which leaves the tab nothing to draw, closes it. It ends in the scales to play over the
 * chord ([ScaleChipRow]), each opening the scale sheet over it.
 */
@Composable
internal fun ChordReferenceSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.ChordReference,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val tuning = userPreferences?.chordTuning() ?: return LaunchedEffect(Unit) { viewModel.dialogHost.dismissSheet(dialog) }
    val notation = (userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default).notation.toChordNotation()
    val storedShapes = userPreferences?.storedChordShapes(tuning).orEmpty()
    val chord = dialog.chord
    val selection = selectShape(chord, tuning, storedShapes)
    // Put up once its shapes are found, see rememberChordShapes.
    val shapes = rememberChordShapes(chord.shapeChord, tuning, isDefined = selection.source == SelectedShape.Source.DEFINED) ?: return
    CampfireBottomSheet(
        title = textResource(Res.string.song_details_chord_shape_choose, chord.name),
        subtitle = chordNoteNames(chord, notation),
        onDismiss = { viewModel.dialogHost.dismissSheet(dialog) },
    ) { contentPadding ->
        key(chord.shapeChord.id, tuning) {
            ChordShapeGrid(
                modifier = Modifier.weight(1f, fill = false),
                chord = chord,
                shapes = shapes,
                tuning = tuning,
                notation = notation,
                selection = selection,
                tuningLine = if (tuning.isStandard) null else stringResource(Res.string.song_details_chord_tuning, chordTuningLabel(tuning, notation)),
                capoLine = capoLine(chord, notation),
                contentPadding = contentPadding,
                onShapeSelected = { shape -> viewModel.preferencesController.setChordVoicing(tuning.voicingKey, chord.shapeChord.id, shape?.let(ChordVoicings::write)) },
                footer = if (dialog.showsScales && FeatureSwitches.of(userPreferences).areScaleSuggestionsEnabled) {
                    {
                        ScaleChipRow(
                            chord = chord,
                            notation = notation,
                            onScaleSelected = { scaleId, rootName -> viewModel.dialogHost.showDialog(DialogType.Scale(scaleId, rootName, showsChords = false, capo = chord.capo)) },
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}
