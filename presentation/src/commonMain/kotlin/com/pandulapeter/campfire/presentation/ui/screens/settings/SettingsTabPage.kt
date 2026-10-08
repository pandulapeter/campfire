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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.navigation.SettingsTab

@Composable
internal fun SettingsTabPage(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    tab: SettingsTab,
    sectionColumns: Int,
    scrollState: ScrollState,
    contentPadding: PaddingValues,
    isImporting: Boolean,
    isPerformanceModeEnabled: Boolean,
    userPreferences: UserPreferences?,
    urlOpener: (String) -> Unit,
) = when (tab) {
    SettingsTab.GENERAL -> SettingsPage(
        modifier = modifier,
        sectionColumns = sectionColumns,
        scrollState = scrollState,
        contentPadding = contentPadding,
        section = { GeneralSection(viewModel = viewModel, userPreferences = userPreferences) },
    )

    SettingsTab.FEATURES -> SettingsPage(
        modifier = modifier,
        sectionColumns = sectionColumns,
        scrollState = scrollState,
        contentPadding = contentPadding,
        section = {
            FeaturesSection(
                viewModel = viewModel,
                userPreferences = userPreferences,
                isPerformanceModeEnabled = isPerformanceModeEnabled,
            )
        },
    )

    SettingsTab.SONGS -> SettingsPage(
        modifier = modifier,
        sectionColumns = sectionColumns,
        scrollState = scrollState,
        contentPadding = contentPadding,
        section = { SongDisplaySection(viewModel = viewModel, userPreferences = userPreferences) },
    )

    SettingsTab.LIBRARY -> SettingsPage(
        modifier = modifier,
        sectionColumns = sectionColumns,
        scrollState = scrollState,
        contentPadding = contentPadding,
        section = { SyncSection(viewModel = viewModel) },
        secondSection = {
            LibrarySection(
                viewModel = viewModel,
                isImporting = isImporting,
                isPerformanceModeEnabled = isPerformanceModeEnabled,
            )
        },
    )

    SettingsTab.ABOUT -> SettingsPage(
        modifier = modifier,
        sectionColumns = sectionColumns,
        scrollState = scrollState,
        contentPadding = contentPadding,
        section = { AboutSection(urlOpener = urlOpener) },
    )
}
