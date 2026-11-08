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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_preferences
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.components.fadingVerticalEdges
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.CampfireBottomSheet
import com.pandulapeter.campfire.presentation.ui.dialogs.sheet.sheetContainerColor
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeController
import com.pandulapeter.campfire.presentation.ui.preferences.PreferencesContent
import com.pandulapeter.campfire.presentation.ui.preferences.PreferencesFrame
import com.pandulapeter.campfire.presentation.ui.preferences.preferencesPages
import com.pandulapeter.campfire.presentation.ui.state.PreferencesController

/**
 * Settings' General, Song, Chords, and Metronome tabs as one sheet over the screen it was opened from
 * ([DialogType.Preferences]), with only the pages that change something there: the same rows, written at once as they are there, so the screen behind the sheet changes as a
 * switch is flipped. It opens on the page its caller names, at its full height; the page scrolls above the buttons
 * that pick another ([PreferencesFrame]), which stay pinned at the bottom of the sheet.
 */
@Composable
internal fun PreferencesSheet(
    preferencesController: PreferencesController,
    metronomeController: MetronomeController,
    dialogHost: DialogHost,
    dialog: DialogType.Preferences,
) {
    val userPreferences by preferencesController.userPreferences.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by preferencesController.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val pages = preferencesPages(context = dialog.context, userPreferences = userPreferences)
    var selectedPage by rememberSaveable { mutableStateOf(dialog.page) }
    CampfireBottomSheet(
        title = stringResource(Res.string.settings_preferences),
        onDismiss = { dialogHost.dismissSheet(dialog) },
    ) { contentPadding ->
        val scrollState = rememberScrollState()
        val backgroundColor = sheetContainerColor()
        PreferencesFrame(
            // As tall as the sheet can be whatever the page, so that the buttons stay where they are as one page gives
            // way to a shorter or a taller one, rather than riding the sheet's edge up and down.
            modifier = Modifier.weight(1f),
            pages = pages,
            selectedPage = selectedPage,
            isKeyboard = userPreferences?.instrument == UserPreferences.Instrument.KEYBOARD,
            buttonsPadding = contentPadding.only(bottom = true),
            onPageSelected = { selectedPage = it },
        ) {
            PreferencesContent(
                modifier = Modifier
                    .fadingVerticalEdges(scrollState, backgroundColor)
                    .bounceVerticalScroll(scrollState)
                    .padding(bottom = 8.dp),
                preferencesController = preferencesController,
                metronomeController = metronomeController,
                userPreferences = userPreferences,
                isPerformanceModeEnabled = isPerformanceModeEnabled,
                pages = pages,
                selectedPage = selectedPage,
                // Over this sheet rather than in its place, so that picking one comes back here (DialogStack).
                showDialog = dialogHost::showDialog,
            )
        }
    }
}
