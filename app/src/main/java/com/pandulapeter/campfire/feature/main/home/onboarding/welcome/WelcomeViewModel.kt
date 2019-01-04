package com.pandulapeter.campfire.feature.main.home.onboarding.welcome

import android.content.res.Resources
import androidx.lifecycle.MutableLiveData
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.main.options.preferences.PreferencesViewModel
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.util.slowMutableLiveDataOf

class WelcomeViewModel(
    private val preferenceDatabase: PreferenceDatabase,
    interactionBlocker: InteractionBlocker
) : CampfireViewModel(interactionBlocker) {

    private val automaticLocaleCode = Resources.getSystem().configuration.locale.isO3Country.toUpperCase()
    val shouldShowThemeSelector = MutableLiveData<Boolean?>()
    val shouldShowLanguageSelector = MutableLiveData<Boolean?>()
    val language = slowMutableLiveDataOf(PreferencesViewModel.Language.fromId(preferenceDatabase.language)) { onLanguageChanged(it) }
    val theme = slowMutableLiveDataOf(PreferencesViewModel.Theme.fromId(preferenceDatabase.theme)) { onThemeChanged(it) }

    fun onLanguageClicked() {
        if (!isUiBlocked) {
            isUiBlocked = true
            shouldShowLanguageSelector.value = true
        }
    }

    fun onThemeClicked() {
        if (!isUiBlocked) {
            isUiBlocked = true
            shouldShowThemeSelector.value = true
        }
    }

    private fun onLanguageChanged(language: PreferencesViewModel.Language) {
        isUiBlocked = true
        preferenceDatabase.language = language.id
        preferenceDatabase.disabledLanguageFilters = if (language == PreferencesViewModel.Language.AUTOMATIC) {
            PreferenceDatabase.getDefaultLanguageFilters(automaticLocaleCode)
        } else {
            preferenceDatabase.disabledLanguageFilters.toMutableSet().apply { remove(language.id) }
        }
        preferenceDatabase.shouldUseGermanNotation = when (language) {
            PreferencesViewModel.Language.AUTOMATIC -> PreferenceDatabase.shouldEnableGermanNotationByDefault(automaticLocaleCode)
            PreferencesViewModel.Language.ENGLISH -> false
            PreferencesViewModel.Language.HUNGARIAN -> true
            PreferencesViewModel.Language.ROMANIAN -> false
        }
    }

    private fun onThemeChanged(theme: PreferencesViewModel.Theme) {
        isUiBlocked = true
        preferenceDatabase.theme = theme.id
    }
}