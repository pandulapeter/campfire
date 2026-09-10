/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.intl.Locale
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.AppLocale
import com.pandulapeter.campfire.presentation.localization.currentLanguage

/**
 * Keeps the language of the generated string tables in sync with the user preference. The tables are switched at
 * runtime on every platform, so no restart is needed.
 */
@Composable
internal fun ApplyLanguagePreference(language: UserPreferences.Language?) {
    val systemLanguage = Locale.current.language
    LaunchedEffect(language, systemLanguage) {
        currentLanguage.value = when (language) {
            UserPreferences.Language.SYSTEM_DEFAULT, null -> AppLocale.findByCode(systemLanguage)
            else -> AppLocale.findByCode(language.id)
        }
    }
}
