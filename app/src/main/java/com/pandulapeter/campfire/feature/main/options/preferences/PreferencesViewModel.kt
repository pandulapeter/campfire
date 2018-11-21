package com.pandulapeter.campfire.feature.main.options.preferences

import android.content.Context
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import com.pandulapeter.campfire.util.generateNotationExample
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

class PreferencesViewModel(private val context: Context) : CampfireViewModel() {

    val preferenceDatabase by inject<PreferenceDatabase>()
    private val firstTimeUserExperienceManager by inject<FirstTimeUserExperienceManager>()
    private val analyticsManager by inject<AnalyticsManager>()
    val shouldShowChords = ObservableBoolean(preferenceDatabase.shouldShowChords)
    val shouldUseGermanNotation = ObservableBoolean(preferenceDatabase.shouldUseGermanNotation)
    val englishNotationExample = generateNotationExample(false)
    val germanNotationExample = generateNotationExample(true)
    val theme = ObservableField<Theme>(Theme.fromId(preferenceDatabase.theme))
    val themeDescription = ObservableField("")
    val language = ObservableField<Language>(Language.fromId(preferenceDatabase.language))
    val languageDescription = ObservableField("")
    val shouldShowThemeSelector = ObservableBoolean()
    val shouldShowLanguageSelector = ObservableBoolean()
    val shouldShowExitConfirmation = ObservableBoolean(preferenceDatabase.shouldShowExitConfirmation)
    val shouldShowHintsResetConfirmation = ObservableBoolean()
    val shouldShowHintsResetSnackbar = ObservableBoolean()
    val shouldShareUsageData = ObservableBoolean(preferenceDatabase.shouldShareUsageData)
    val shouldShareCrashReports = ObservableBoolean(preferenceDatabase.shouldShareCrashReports)

    init {
        shouldShowChords.onPropertyChanged {
            analyticsManager.onShouldShowChordsToggled(it, AnalyticsManager.PARAM_VALUE_SCREEN_OPTIONS_PREFERENCES)
            preferenceDatabase.shouldShowChords = it
        }
        shouldUseGermanNotation.onPropertyChanged {
            analyticsManager.onNotationModeChanged(it)
            preferenceDatabase.shouldUseGermanNotation = it
        }
        theme.onPropertyChanged {
            preferenceDatabase.theme = it.id
            updateThemeDescription()
            analyticsManager.onThemeChanged(
                when (it) {
                    PreferencesViewModel.Theme.AUTOMATIC -> AnalyticsManager.PARAM_VALUE_AUTOMATIC
                    PreferencesViewModel.Theme.LIGHT -> AnalyticsManager.PARAM_VALUE_LIGHT
                    PreferencesViewModel.Theme.DARK -> AnalyticsManager.PARAM_VALUE_DARK
                }
            )
        }
        language.onPropertyChanged {
            preferenceDatabase.language = it.id
            updateLanguageDescription()
            analyticsManager.onLanguageChanged(if (it == PreferencesViewModel.Language.AUTOMATIC) AnalyticsManager.PARAM_VALUE_AUTOMATIC else it.id)
        }
        shouldShowExitConfirmation.onPropertyChanged {
            analyticsManager.onExitConfirmationToggled(it)
            preferenceDatabase.shouldShowExitConfirmation = it
        }
        shouldShareUsageData.onPropertyChanged { preferenceDatabase.shouldShareUsageData = it }
        shouldShareCrashReports.onPropertyChanged { preferenceDatabase.shouldShareCrashReports = it }
        updateThemeDescription()
        updateLanguageDescription()
    }

    fun onThemeClicked() = shouldShowThemeSelector.set(true)

    fun onLanguageClicked() = shouldShowLanguageSelector.set(true)

    fun onResetHintsClicked() {
        shouldShowHintsResetConfirmation.set(true)
    }

    fun resetHints() {
        firstTimeUserExperienceManager.resetAll()
        shouldShowHintsResetSnackbar.set(true)
    }

    private fun updateThemeDescription() = themeDescription.set(
        context.getString(
            when (theme.get()) {
                null, PreferencesViewModel.Theme.AUTOMATIC -> R.string.options_preferences_app_theme_automatic_description
                PreferencesViewModel.Theme.DARK -> R.string.options_preferences_app_theme_dark_description
                PreferencesViewModel.Theme.LIGHT -> R.string.options_preferences_app_theme_light_description
            }
        )
    )

    private fun updateLanguageDescription() = languageDescription.set(
        context.getString(
            when (language.get()) {
                null, PreferencesViewModel.Language.AUTOMATIC -> R.string.options_preferences_language_automatic_description
                PreferencesViewModel.Language.ENGLISH -> R.string.options_preferences_language_english_description
                PreferencesViewModel.Language.HUNGARIAN -> R.string.options_preferences_language_hungarian_description
                PreferencesViewModel.Language.ROMANIAN -> R.string.options_preferences_language_romanian_description
            }
        )
    )

    enum class Theme(val id: Int) {
        AUTOMATIC(0),
        DARK(1),
        LIGHT(2);

        companion object {
            fun fromId(id: Int) = Theme.values().find { it.id == id } ?: AUTOMATIC
        }
    }

    enum class Language(val id: String) {
        AUTOMATIC(""),
        ENGLISH(com.pandulapeter.campfire.data.model.local.Language.SupportedLanguages.ENGLISH.id),
        HUNGARIAN(com.pandulapeter.campfire.data.model.local.Language.SupportedLanguages.HUNGARIAN.id),
        ROMANIAN(com.pandulapeter.campfire.data.model.local.Language.SupportedLanguages.ROMANIAN.id);

        companion object {
            fun fromId(id: String) = Language.values().find { it.id == id } ?: AUTOMATIC
        }
    }
}