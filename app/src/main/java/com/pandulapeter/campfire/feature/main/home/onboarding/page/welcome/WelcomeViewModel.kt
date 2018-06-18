package com.pandulapeter.campfire.feature.main.home.onboarding.page.welcome

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.main.options.preferences.PreferencesViewModel
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.util.obtainColor
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

class WelcomeViewModel(private val context: Context) : CampfireViewModel() {
    private val preferenceDatabase by inject<PreferenceDatabase>()
    val shouldShowThemeSelector = ObservableBoolean()
    val shouldShowLanguageSelector = ObservableBoolean()
    val language = ObservableField<PreferencesViewModel.Language>(PreferencesViewModel.Language.fromId(preferenceDatabase.language))
    val languageLabel = ObservableField(SpannableString(""))
    private val languageText = context.getString(R.string.welcome_language)
    val theme = ObservableField<PreferencesViewModel.Theme>(PreferencesViewModel.Theme.fromId(preferenceDatabase.theme))
    val themeLabel = ObservableField(SpannableString(""))
    private val themeText = context.getString(R.string.welcome_theme)

    init {
        language.onPropertyChanged {
            preferenceDatabase.language = it.id
            updateLanguageDescription()
        }
        updateLanguageDescription()
        theme.onPropertyChanged {
            preferenceDatabase.theme = it.id
            updateThemeDescription()
        }
        updateThemeDescription()
    }

    fun onThemeClicked() = shouldShowThemeSelector.set(true)

    fun onLanguageClicked() = shouldShowLanguageSelector.set(true)

    private fun updateLanguageDescription() = languageLabel.set(
        SpannableString(
            "$languageText ${context.getString(
                when (language.get()) {
                    null, PreferencesViewModel.Language.AUTOMATIC -> R.string.options_preferences_language_automatic
                    PreferencesViewModel.Language.ENGLISH -> R.string.options_preferences_language_english
                    PreferencesViewModel.Language.HUNGARIAN -> R.string.options_preferences_language_hungarian
                    PreferencesViewModel.Language.ROMANIAN -> R.string.options_preferences_language_romanian
                }
            )}"
        ).apply {
            setSpan(ForegroundColorSpan(context.obtainColor(android.R.attr.textColorPrimary)), 0, languageText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    )

    private fun updateThemeDescription() = themeLabel.set(
        SpannableString(
            "$themeText ${context.getString(
                when (theme.get()) {
                    null, PreferencesViewModel.Theme.AUTOMATIC -> R.string.options_preferences_app_theme_automatic
                    PreferencesViewModel.Theme.DARK -> R.string.options_preferences_app_theme_dark
                    PreferencesViewModel.Theme.LIGHT -> R.string.options_preferences_app_theme_light
                }
            )}"
        ).apply {
            setSpan(ForegroundColorSpan(context.obtainColor(android.R.attr.textColorPrimary)), 0, themeText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    )
}