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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_notation_choose
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.chords.description
import com.pandulapeter.campfire.presentation.ui.chords.label
import com.pandulapeter.campfire.presentation.ui.components.RadioListItem
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.platform.bounceVerticalScroll

/**
 * The notation chords are written in, opened from its chip in Settings: the five as a list, each with its example under
 * it, which is what tells them apart to somebody who has only ever read one of them. A pick is written at once and
 * closes the sheet, as the [TuningSheet]'s does, since a choice is its whole answer.
 */
@Composable
internal fun NotationSheet(
    viewModel: CampfireViewModel,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val selected = (userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default).notation
    CampfireBottomSheet(
        title = stringResource(Res.string.settings_notation_choose),
        onDismiss = { viewModel.dismissSheet(DialogType.Notation) },
    ) { contentPadding ->
        val scrollState = rememberScrollState()
        val closeSheet = { close() }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .fadingTopEdge(scrollState, sheetContainerColor())
                .bounceVerticalScroll(scrollState)
                .padding(contentPadding)
                .padding(bottom = 8.dp)
                .selectableGroup(),
        ) {
            UserPreferences.Notation.entries.forEach { notation ->
                RadioListItem(
                    title = stringResource(notation.label),
                    description = stringResource(notation.description),
                    isSelected = notation == selected,
                    onSelected = {
                        viewModel.setNotation(notation)
                        closeSheet()
                    },
                )
            }
        }
    }
}
