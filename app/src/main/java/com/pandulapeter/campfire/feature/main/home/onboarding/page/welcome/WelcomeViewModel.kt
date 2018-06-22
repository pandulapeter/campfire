package com.pandulapeter.campfire.feature.main.home.onboarding.page.welcome

import android.content.res.Resources
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.main.options.preferences.PreferencesViewModel
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

class WelcomeViewModel : CampfireViewModel() {
    private val preferenceDatabase by inject<PreferenceDatabase>()
    val shouldShowThemeSelector = ObservableBoolean()
    val shouldShowLanguageSelector = ObservableBoolean()
    val language = ObservableField<PreferencesViewModel.Language>(PreferencesViewModel.Language.fromId(preferenceDatabase.language))
    val theme = ObservableField<PreferencesViewModel.Theme>(PreferencesViewModel.Theme.fromId(preferenceDatabase.theme))

    init {
        val automaticLocaleCode = Resources.getSystem().configuration.locale.isO3Country.toUpperCase()
        language.onPropertyChanged {
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
            }
        }
        theme.onPropertyChanged { preferenceDatabase.theme = it.id }
    }

    fun onThemeClicked() = shouldShowThemeSelector.set(true)

    fun onLanguageClicked() = shouldShowLanguageSelector.set(true)
}