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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.pandulapeter.campfire.presentation.ui.chords.selectShape
import com.pandulapeter.campfire.presentation.ui.chords.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll

/**
 * A chord of the Chords tab with every way it can be played, the song's chord sheet ([ChordShapeSheet]) with no song
 * around it: titled the same way, naming a tuning that is not the standard one under the title, and writing the page it
 * settles on as the player's shape for the chord in that tuning at once, so that the tab and every song follow it. The
 * tuning is the one the tab drew the chord for, which nothing can change while the sheet covers the tab; an instrument
 * without shapes, which leaves the tab nothing to draw, closes it.
 */
@Composable
internal fun ChordReferenceSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.ChordReference,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val tuning = userPreferences?.chordTuning() ?: return LaunchedEffect(Unit) { viewModel.dismissSheet(dialog) }
    val notation = (userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default).notation.toChordNotation()
    val storedShapes = userPreferences?.chordVoicings?.get(tuning.voicingKey).orEmpty()
    val chord = dialog.chord
    CampfireBottomSheet(
        title = textResource(Res.string.song_details_chord_shape_choose, chord.name),
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
            if (!tuning.isStandard) {
                ChordTuningLine(text = stringResource(Res.string.song_details_chord_tuning, chordTuningLabel(tuning, notation)))
            }
            key(chord.chord.id, tuning) {
                ChordShapePager(
                    chord = chord,
                    tuning = tuning,
                    notation = notation,
                    selection = selectShape(chord, tuning, storedShapes),
                    onShapeSelected = { shape -> viewModel.setChordVoicing(tuning.voicingKey, chord.chord.id, shape?.let(ChordVoicings::write)) },
                )
            }
        }
    }
}
