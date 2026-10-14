/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.runtime.Composable
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_english
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_hungarian
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_system_default

/** What [language] is called in the language the app is set to. */
@Composable
internal fun appLanguageName(language: UserPreferences.Language) = stringResource(language.nameResource)

/** The string [appLanguageName] reads, which [LanguageChoice] also reads out of the language's own table. */
internal val UserPreferences.Language.nameResource
    get() = when (this) {
        UserPreferences.Language.SYSTEM_DEFAULT -> Res.string.settings_user_interface_language_system_default
        UserPreferences.Language.ENGLISH -> Res.string.settings_user_interface_language_english
        UserPreferences.Language.HUNGARIAN -> Res.string.settings_user_interface_language_hungarian
    }
