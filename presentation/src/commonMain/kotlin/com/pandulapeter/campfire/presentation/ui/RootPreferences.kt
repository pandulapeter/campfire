/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.preferences.AppLanguage
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import kotlinx.coroutines.flow.StateFlow

/**
 * The preferences [CampfireApp] reads at the root, null where they have not been read yet. Derived from the whole
 * [UserPreferences] rather than read from it there, so that a write of anything else (a song open counted, a
 * transposition, a fold) does not compose the app's root again.
 */
@Immutable
internal data class RootPreferences(
    val language: AppLanguage? = null,
    val tagColors: Map<String, String> = emptyMap(),
    val areTooltipsEnabled: Boolean = true,
    val instrument: UserPreferences.Instrument? = null,
    val uiMode: UserPreferences.UiMode? = null,
    val themeColor: UserPreferences.ThemeColor? = null,
    val backgroundWarmth: Int = 0,
)

@Composable
internal fun rememberRootPreferences(userPreferences: StateFlow<UserPreferences?>): State<RootPreferences> {
    val preferences = userPreferences.collectAsStateWithLifecycle()
    return remember(preferences) {
        derivedStateOf {
            preferences.value?.let {
                RootPreferences(
                    language = it.language,
                    tagColors = it.tagColors,
                    areTooltipsEnabled = it.areTooltipsEnabled,
                    instrument = it.instrument,
                    uiMode = it.uiMode,
                    themeColor = it.themeColor,
                    backgroundWarmth = it.backgroundWarmth,
                )
            } ?: RootPreferences()
        }
    }
}
