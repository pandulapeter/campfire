/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.navigation.SettingsTab
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsPage
import com.pandulapeter.campfire.presentation.ui.settingsPage.SettingsSection

/**
 * One tab of the settings screen: a single column of titled [SettingsSection]s, the same on every tab and at every
 * width. A second column would put two lists side by side that are read one after the other, and it would come and go
 * with the width of the window, so that the same tab looked different from one size to the next; the room a wide
 * window has left is what the category pane next to the page is for.
 */
@Composable
internal fun SettingsTabPage(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    tab: SettingsTab,
    scrollState: ScrollState,
    contentPadding: PaddingValues,
    isImporting: Boolean,
    isPerformanceModeEnabled: Boolean,
    userPreferences: UserPreferences?,
    urlOpener: (String) -> Unit,
) = SettingsPage(
    modifier = modifier,
    sectionColumns = 1,
    scrollState = scrollState,
    contentPadding = contentPadding,
    section = {
        when (tab) {
            SettingsTab.GENERAL -> GeneralSections(
                viewModel = viewModel,
                userPreferences = userPreferences,
                isPerformanceModeEnabled = isPerformanceModeEnabled,
            )
            SettingsTab.FEATURES -> FeaturesSections(viewModel = viewModel, userPreferences = userPreferences)
            SettingsTab.PREFERENCES -> {
                ChordsSection(viewModel = viewModel, userPreferences = userPreferences)
                // The chords are written the same way in the Chords and Scales tabs, while all of this is about songs.
                AnimatedVisibility(
                    visible = userPreferences?.areSongsEnabled != false,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    SongDisplaySection(viewModel = viewModel, userPreferences = userPreferences)
                }
            }
            SettingsTab.LIBRARY -> {
                SyncSection(viewModel = viewModel)
                LibrarySections(
                    viewModel = viewModel,
                    isImporting = isImporting,
                    isPerformanceModeEnabled = isPerformanceModeEnabled,
                )
            }
            SettingsTab.ABOUT -> AboutSections(
                viewModel = viewModel,
                urlOpener = urlOpener,
            )
        }
    },
)
