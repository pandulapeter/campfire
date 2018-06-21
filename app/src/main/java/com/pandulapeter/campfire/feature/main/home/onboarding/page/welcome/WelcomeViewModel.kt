package com.pandulapeter.campfire.feature.main.home.onboarding.page.welcome

import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.text.SpannableString
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
    val languageLabel = ObservableField(SpannableString(""))
    val theme = ObservableField<PreferencesViewModel.Theme>(PreferencesViewModel.Theme.fromId(preferenceDatabase.theme))
    val themeLabel = ObservableField(SpannableString(""))

    init {
        language.onPropertyChanged { preferenceDatabase.language = it.id }
        theme.onPropertyChanged { preferenceDatabase.theme = it.id }
    }

    fun onThemeClicked() = shouldShowThemeSelector.set(true)

    fun onLanguageClicked() = shouldShowLanguageSelector.set(true)
}