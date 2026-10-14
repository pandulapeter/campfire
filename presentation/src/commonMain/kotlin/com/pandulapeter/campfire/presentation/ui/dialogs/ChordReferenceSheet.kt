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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.chords.ChordVoicings
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_chord_shape_choose
import com.pandulapeter.campfire.presentation.resources.song_details_chord_tuning
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.instrument.chordTuning
import com.pandulapeter.campfire.presentation.ui.chords.chordTuningLabel
import com.pandulapeter.campfire.presentation.ui.chords.SelectedShape
import com.pandulapeter.campfire.presentation.ui.chords.selectShape
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.ui.instrument.storedChordShapes

/**
 * A chord of the Chords tab with every way it can be played, the song's chord sheet ([ChordShapeSheet]) with no song
 * around it: titled the same way, naming a tuning that is not the standard one under the title, and writing the shape
 * picked in its grid as the player's shape for the chord in that tuning at once, so that the tab and every song follow
 * it. The tuning is the one the tab drew the chord for, which nothing can change while the sheet covers the tab; an
 * instrument without shapes, which leaves the tab nothing to draw, closes it.
 */
@Composable
internal fun ChordReferenceSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.ChordReference,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val tuning = userPreferences?.chordTuning() ?: return LaunchedEffect(Unit) { viewModel.dismissSheet(dialog) }
    val notation = (userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default).notation.toChordNotation()
    val storedShapes = userPreferences?.storedChordShapes(tuning).orEmpty()
    val chord = dialog.chord
    val selection = selectShape(chord, tuning, storedShapes)
    // Put up once its shapes are found, see rememberChordShapes.
    val shapes = rememberChordShapes(chord.chord, tuning, isDefined = selection.source == SelectedShape.Source.DEFINED) ?: return
    CampfireBottomSheet(
        title = textResource(Res.string.song_details_chord_shape_choose, chord.name),
        subtitle = chordNoteNames(chord, notation),
        onDismiss = { viewModel.dismissSheet(dialog) },
    ) { contentPadding ->
        key(chord.chord.id, tuning) {
            ChordShapeGrid(
                modifier = Modifier.weight(1f, fill = false),
                chord = chord,
                shapes = shapes,
                tuning = tuning,
                notation = notation,
                selection = selection,
                tuningLine = if (tuning.isStandard) null else stringResource(Res.string.song_details_chord_tuning, chordTuningLabel(tuning, notation)),
                contentPadding = contentPadding,
                onShapeSelected = { shape -> viewModel.setChordVoicing(tuning.voicingKey, chord.chord.id, shape?.let(ChordVoicings::write)) },
            )
        }
    }
}
