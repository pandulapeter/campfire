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

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_folder
import com.pandulapeter.campfire.presentation.resources.ic_info
import com.pandulapeter.campfire.presentation.resources.ic_widgets
import com.pandulapeter.campfire.presentation.ui.navigation.SettingsTab
import com.pandulapeter.campfire.presentation.ui.preferences.PreferencesPage
import com.pandulapeter.campfire.presentation.ui.preferences.PreferencesPageIcon
import org.jetbrains.compose.resources.painterResource

/**
 * The icon of a tab of the settings screen in the wide layout's category pane, so that a category is found at a glance
 * rather than read: the Preferences' tabs have the icons of their pages' buttons in the sheet, so a page is recognized
 * in both places.
 *
 * @param isKeyboard Whether the Chords tab's icon is the keyboard's, as the Chords screen's is.
 */
@Composable
internal fun SettingsTabIcon(
    tab: SettingsTab,
    isKeyboard: Boolean,
) = when (tab) {
    SettingsTab.GENERAL -> PreferencesPageIcon(page = PreferencesPage.GENERAL, isKeyboard = isKeyboard)
    SettingsTab.SONG -> PreferencesPageIcon(page = PreferencesPage.SONG, isKeyboard = isKeyboard)
    SettingsTab.CHORDS -> PreferencesPageIcon(page = PreferencesPage.CHORDS, isKeyboard = isKeyboard)
    SettingsTab.METRONOME -> PreferencesPageIcon(page = PreferencesPage.METRONOME, isKeyboard = isKeyboard)
    SettingsTab.LIBRARY -> Icon(painter = painterResource(Res.drawable.ic_folder), contentDescription = null)
    SettingsTab.FEATURES -> Icon(painter = painterResource(Res.drawable.ic_widgets), contentDescription = null)
    SettingsTab.ABOUT -> Icon(painter = painterResource(Res.drawable.ic_info), contentDescription = null)
}
