/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.songDisplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.preferences.ChordNames
import com.pandulapeter.campfire.presentation.localization.joinIsolated
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.chord_names_example
import com.pandulapeter.campfire.presentation.resources.chord_names_keep_key
import com.pandulapeter.campfire.presentation.resources.chord_names_keep_key_description
import com.pandulapeter.campfire.presentation.resources.chord_names_shapes_description
import com.pandulapeter.campfire.presentation.resources.chord_names_sounding_description
import com.pandulapeter.campfire.presentation.resources.chord_names_title
import com.pandulapeter.campfire.presentation.resources.settings_chord_names_shapes
import com.pandulapeter.campfire.presentation.resources.settings_chord_names_sounding
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ChoiceCard
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType

/**
 * The question the first song a capo matters on asks once, over that song: whether its chords are named by the shapes
 * fingered above the capo or by what sounds, or whether the capo keeps the key instead (`UserPreferences.capoKeepsKey`):
 * the chords named as they sound and a capo changing only the shapes. The first two cards end in the song's first
 * chords named their way, so the choice is seen rather than imagined. A [ConfirmedQuestionSheet], as the layout question
 * ([SongLayoutSheet]) is: a pick is written at once, the song behind it changing as it goes, and only Confirm
 * answers the question; confirming without a pick keeps the shapes, which is what is shown as picked, and
 * a sheet put away without Confirm (back, Escape) is asked again.
 */
@Composable
internal fun ChordNamesSheet(
    viewModel: CampfireViewModel,
    dialog: DialogType.ChordNames,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    ConfirmedQuestionSheet(
        title = stringResource(Res.string.chord_names_title),
        onConfirm = viewModel.preferencesController::markChordNamesChosen,
        onDismiss = { viewModel.dialogHost.dismissSheet(dialog) },
    ) {
        Text(
            modifier = Modifier.padding(bottom = 16.dp),
            text = textResource(Res.string.chord_names_example, dialog.capo.toString()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChordNamesChoices(
            exampleChords = dialog.exampleChords(),
            chordNames = userPreferences?.chordNames,
            capoKeepsKey = userPreferences?.capoKeepsKey == true,
            onSelected = { answer ->
                viewModel.preferencesController.pickChordNamesAnswer(chordNames = answer.chordNames, capoKeepsKey = answer.keepsKey)
            },
        )
    }
}

/**
 * The song's first chords named both ways, by the shapes and as they sound, joined as a line each; null where it has
 * none to show or the two would read the same.
 */
internal fun DialogType.ChordNames.exampleChords() = if (shapesChords.isNotEmpty() && soundingChords.isNotEmpty() && shapesChords != soundingChords) {
    shapesChords.joinIsolated(CHORD_SEPARATOR) to soundingChords.joinIsolated(CHORD_SEPARATOR)
} else {
    null
}

private const val CHORD_SEPARATOR = " · "

/** The question's answers: the two ways of naming chords under a capo that raises the pitch, and keeping the key. */
private enum class CapoAnswer(
    val chordNames: ChordNames,
    val keepsKey: Boolean,
) {
    SHAPES(ChordNames.SHAPES, keepsKey = false),
    SOUNDING(ChordNames.SOUNDING, keepsKey = false),
    KEEP_KEY(ChordNames.SOUNDING, keepsKey = true),
}

@Composable
private fun ChordNamesChoices(
    exampleChords: Pair<String, String>?,
    chordNames: ChordNames?,
    capoKeepsKey: Boolean,
    onSelected: (CapoAnswer) -> Unit,
) = Column(
    modifier = Modifier.fillMaxWidth().selectableGroup(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    CapoAnswer.entries.forEach { answer ->
        ChoiceCard(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(
                when (answer) {
                    CapoAnswer.SHAPES -> Res.string.settings_chord_names_shapes
                    CapoAnswer.SOUNDING -> Res.string.settings_chord_names_sounding
                    CapoAnswer.KEEP_KEY -> Res.string.chord_names_keep_key
                },
            ),
            description = stringResource(
                when (answer) {
                    CapoAnswer.SHAPES -> Res.string.chord_names_shapes_description
                    CapoAnswer.SOUNDING -> Res.string.chord_names_sounding_description
                    CapoAnswer.KEEP_KEY -> Res.string.chord_names_keep_key_description
                },
            ),
            // Keeping the key is about what a capo changes rather than what the chords are called, which a row of chord
            // names would not show.
            detail = exampleChords?.let { (shapes, sounding) ->
                when (answer) {
                    CapoAnswer.SHAPES -> shapes
                    CapoAnswer.SOUNDING -> sounding
                    CapoAnswer.KEEP_KEY -> null
                }
            },
            isSelected = answer.keepsKey == capoKeepsKey && (answer.keepsKey || answer.chordNames == chordNames),
            role = Role.RadioButton,
            onClick = { onSelected(answer) },
        )
    }
}
