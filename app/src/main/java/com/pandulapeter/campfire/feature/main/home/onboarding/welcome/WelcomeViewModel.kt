package com.pandulapeter.campfire.feature.main.home.onboarding.welcome

import android.content.res.Resources
import androidx.lifecycle.MutableLiveData
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.main.options.preferences.PreferencesViewModel
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

class WelcomeViewModel(private val preferenceDatabase: PreferenceDatabase) : CampfireViewModel() {

    val shouldShowThemeSelector = MutableLiveData<Boolean?>()
    val shouldShowLanguageSelector = MutableLiveData<Boolean?>()
    val language = MutableLiveData<PreferencesViewModel.Language>().apply { value = PreferencesViewModel.Language.fromId(preferenceDatabase.language) }
    val theme = MutableLiveData<PreferencesViewModel.Theme>().apply { value = PreferencesViewModel.Theme.fromId(preferenceDatabase.theme) }

    init {
        val automaticLocaleCode = Resources.getSystem().configuration.locale.isO3Country.toUpperCase()
        language.observeForever {
            isUiBlocked = true
            preferenceDatabase.language = it.id
            preferenceDatabase.disabledLanguageFilters = if (it == PreferencesViewModel.Language.AUTOMATIC) {
                PreferenceDatabase.getDefaultLanguageFilters(automaticLocaleCode)
            } else {
                preferenceDatabase.disabledLanguageFilters.toMutableSet().apply { remove(it.id) }
            }
            preferenceDatabase.shouldUseGermanNotation = when (it) {
                PreferencesViewModel.Language.AUTOMATIC -> PreferenceDatabase.shouldEnableGermanNotationByDefault(automaticLocaleCode)
                PreferencesViewModel.Language.ENGLISH -> false
                PreferencesViewModel.Language.HUNGARIAN -> true
                PreferencesViewModel.Language.ROMANIAN -> false
                null -> false
            }
        }
        theme.observeForever {
            isUiBlocked = true
            preferenceDatabase.theme = it.id
        }
    }

    fun onThemeClicked() {
        if (!isUiBlocked) {
            isUiBlocked = true
            shouldShowThemeSelector.value = true
        }
    }

    fun onLanguageClicked() {
        if (!isUiBlocked) {
            isUiBlocked = true
            shouldShowLanguageSelector.value = true
        }
    }
}