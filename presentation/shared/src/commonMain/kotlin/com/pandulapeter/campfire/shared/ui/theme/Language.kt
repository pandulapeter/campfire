package com.pandulapeter.campfire.shared.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.intl.Locale
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.shared.localization.AppLocale
import com.pandulapeter.campfire.shared.localization.currentLanguage

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
