/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.intl.Locale
import com.pandulapeter.campfire.chordpro.language.LanguageCodes
import com.pandulapeter.campfire.data.model.preferences.AppLanguage

/**
 * Keeps the language of the generated string tables in sync with the user preference. The tables are switched at
 * runtime on every platform, so no restart is needed.
 */
@Composable
internal fun ApplyLanguagePreference(language: AppLanguage?) {
    val systemLanguage = Locale.current.language
    LaunchedEffect(language, systemLanguage) {
        currentLanguage.value = resolvedAppLocale(language = language, systemLanguage = systemLanguage)
    }
}

/**
 * The string table for [language], or for [systemLanguage] where the user left it to the system. The system's code is
 * read as the standard writes it today first, since Android still reports Hebrew as `iw` where every other platform
 * says `he`.
 */
internal fun resolvedAppLocale(language: AppLanguage?, systemLanguage: String) = when (language) {
    AppLanguage.SYSTEM_DEFAULT, null -> AppLocale.findByCode(LanguageCodes.current(systemLanguage))
    else -> AppLocale.findByCode(language.id)
}
