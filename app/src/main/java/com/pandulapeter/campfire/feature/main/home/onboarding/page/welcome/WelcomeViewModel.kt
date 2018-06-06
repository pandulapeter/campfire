package com.pandulapeter.campfire.feature.main.home.onboarding.page.welcome

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
    val theme = ObservableField<PreferencesViewModel.Theme>(PreferencesViewModel.Theme.fromId(preferenceDatabase.theme))
    val themeDescription = ObservableField("")
    val language = ObservableField<PreferencesViewModel.Language>(PreferencesViewModel.Language.fromId(preferenceDatabase.language))
    val languageDescription = ObservableField("")

    init {
        theme.onPropertyChanged {
            preferenceDatabase.theme = it.id
            updateThemeDescription()
        }
        language.onPropertyChanged {
            preferenceDatabase.language = it.id
            updateLanguageDescription()
        }
    }

    fun onThemeClicked() = shouldShowThemeSelector.set(true)

    fun onLanguageClicked() = shouldShowLanguageSelector.set(true)

    private fun updateThemeDescription() = Unit //themeDescription.set(
//        context.getString(
//            when (theme.get()) {
//                null, PreferencesViewModel.Theme.AUTOMATIC -> R.string.options_preferences_app_theme_automatic_description
//                PreferencesViewModel.Theme.DARK -> R.string.options_preferences_app_theme_dark_description
//                PreferencesViewModel.Theme.LIGHT -> R.string.options_preferences_app_theme_light_description
//            }
//        )
//    )

    private fun updateLanguageDescription() = Unit //languageDescription.set(
//        context.getString(
//            when (language.get()) {
//                null, PreferencesViewModel.Language.AUTOMATIC -> R.string.options_preferences_language_automatic_description
//                PreferencesViewModel.Language.ENGLISH -> R.string.options_preferences_language_english_description
//                PreferencesViewModel.Language.HUNGARIAN -> R.string.options_preferences_language_hungarian_description
//                PreferencesViewModel.Language.ROMANIAN -> R.string.options_preferences_language_romanian_description
//            }
//        )
//    )
}