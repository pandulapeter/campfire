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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_display_settings
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor
import com.pandulapeter.campfire.presentation.ui.songDisplay.ChordsSection
import com.pandulapeter.campfire.presentation.ui.songDisplay.SongPageSection

/**
 * The Preferences tab's sections about reading a song, over the song details screen: the same rows, written at once as
 * they are there, so the song behind the sheet changes as a switch is flipped. The song cards stay in Settings, since
 * nothing on this screen shows them.
 */
@Composable
internal fun SongDisplaySheet(
    viewModel: CampfireViewModel,
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    CampfireBottomSheet(
        title = stringResource(Res.string.song_details_display_settings),
        onDismiss = { viewModel.dialogHost.dismissSheet(DialogType.SongDisplay) },
    ) { contentPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .fadingTopEdge(scrollState, sheetContainerColor())
                .bounceVerticalScroll(scrollState)
                .padding(contentPadding)
                .padding(bottom = 8.dp),
        ) {
            ChordsSection(
                userPreferences = userPreferences,
                // Over this sheet rather than in its place, so that picking one comes back here (DialogStack).
                onNotationClicked = { viewModel.dialogHost.showDialog(DialogType.Notation) },
                onAccidentalsSelected = viewModel.preferencesController::setAccidentals,
            )
            SongPageSection(userPreferences = userPreferences, preferencesController = viewModel.preferencesController)
        }
    }
}
