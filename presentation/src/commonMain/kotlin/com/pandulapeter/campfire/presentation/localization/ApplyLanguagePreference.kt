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
import androidx.compose.ui.text.intl.Locale
import com.pandulapeter.campfire.chordpro.language.LanguageCodes
import com.pandulapeter.campfire.data.model.preferences.AppLanguage

/**
 * Keeps the language of the generated string tables in sync with the user preference. The tables are switched at
 * runtime on every platform, so no restart is needed.
 *
 * The switch is made as this is composed rather than in an effect, so everything composed after it in the same pass
 * (the layout direction `CampfireApp` provides from it, and every string) already reads the new language: an effect runs
 * after the frame is drawn, and the first frame with the preferences loaded would show the system's language, and an
 * interface written from the right would be drawn from the left once.
 */
@Composable
internal fun ApplyLanguagePreference(language: AppLanguage?) {
    val resolved = resolvedAppLocale(language = language, systemLanguage = Locale.current.language)
    if (currentLanguage.value != resolved) currentLanguage.value = resolved
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
