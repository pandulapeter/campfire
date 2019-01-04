package com.pandulapeter.campfire.feature.main.options.preferences

import androidx.lifecycle.MutableLiveData
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import com.pandulapeter.campfire.util.generateNotationExample
import com.pandulapeter.campfire.util.mutableLiveDataOf

class PreferencesViewModel(
    private val preferenceDatabase: PreferenceDatabase,
    private val firstTimeUserExperienceManager: FirstTimeUserExperienceManager,
    private val analyticsManager: AnalyticsManager,
    interactionBlocker: InteractionBlocker
) : CampfireViewModel(interactionBlocker) {

    val shouldShowChords = mutableLiveDataOf(preferenceDatabase.shouldShowChords) { onShouldShowChordsChanged(it) }
    val shouldUseGermanNotation = mutableLiveDataOf(preferenceDatabase.shouldUseGermanNotation) { onShouldUseGermanNotationChanged(it) }
    val englishNotationExample = generateNotationExample(false)
    val germanNotationExample = generateNotationExample(true)
    val themeDescription = MutableLiveData<Int>()
    val theme = mutableLiveDataOf(Theme.fromId(preferenceDatabase.theme)) { onThemeChanged(it) }
    val languageDescription = MutableLiveData<Int>()
    val language = mutableLiveDataOf(Language.fromId(preferenceDatabase.language)) { onLanguageChanged(it) }
    val shouldShowExitConfirmation = mutableLiveDataOf(preferenceDatabase.shouldShowExitConfirmation) { onShouldShowExitConfirmationChanged(it) }
    val shouldShareUsageData = mutableLiveDataOf(preferenceDatabase.shouldShareUsageData) { onShouldShareUsageDataChanged(it) }
    val shouldShareCrashReports = mutableLiveDataOf(preferenceDatabase.shouldShareCrashReports) { onShouldShareCrashReportsChanged(it) }
    val shouldShowHintsResetConfirmation = MutableLiveData<Boolean?>()
    val shouldShowHintsResetSnackbar = MutableLiveData<Boolean?>()
    val shouldShowThemeSelector = MutableLiveData<Boolean?>()
    val shouldShowLanguageSelector = MutableLiveData<Boolean?>()

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

    fun onResetHintsClicked() {
        if (!isUiBlocked) {
            isUiBlocked = true
            shouldShowHintsResetConfirmation.value = true
        }
    }

    fun resetHints() {
        firstTimeUserExperienceManager.resetAll()
        shouldShowHintsResetSnackbar.value = true
    }

    private fun onShouldShowChordsChanged(shouldShowChords: Boolean) {
        analyticsManager.onShouldShowChordsToggled(shouldShowChords, AnalyticsManager.PARAM_VALUE_SCREEN_OPTIONS_PREFERENCES)
        preferenceDatabase.shouldShowChords = shouldShowChords
    }

    private fun onShouldUseGermanNotationChanged(shouldUseGermanNotation: Boolean) {
        analyticsManager.onNotationModeChanged(shouldUseGermanNotation)
        preferenceDatabase.shouldUseGermanNotation = shouldUseGermanNotation
    }

    private fun onThemeChanged(theme: Theme) {
        preferenceDatabase.theme = theme.id
        updateThemeDescription(theme)
        analyticsManager.onThemeChanged(
            when (theme) {
                PreferencesViewModel.Theme.AUTOMATIC -> AnalyticsManager.PARAM_VALUE_AUTOMATIC
                PreferencesViewModel.Theme.LIGHT -> AnalyticsManager.PARAM_VALUE_LIGHT
                PreferencesViewModel.Theme.DARK -> AnalyticsManager.PARAM_VALUE_DARK
            }
        )
    }

    private fun updateThemeDescription(theme: Theme) {
        themeDescription.value = when (theme) {
            PreferencesViewModel.Theme.AUTOMATIC -> R.string.options_preferences_app_theme_automatic_description
            PreferencesViewModel.Theme.DARK -> R.string.options_preferences_app_theme_dark_description
            PreferencesViewModel.Theme.LIGHT -> R.string.options_preferences_app_theme_light_description
        }
    }

    private fun onLanguageChanged(language: Language) {
        preferenceDatabase.language = language.id
        updateLanguageDescription(language)
        analyticsManager.onLanguageChanged(if (language == PreferencesViewModel.Language.AUTOMATIC) AnalyticsManager.PARAM_VALUE_AUTOMATIC else language.id)
    }

    private fun updateLanguageDescription(language: Language) {
        languageDescription.value = when (language) {
            PreferencesViewModel.Language.AUTOMATIC -> R.string.options_preferences_language_automatic_description
            PreferencesViewModel.Language.ENGLISH -> R.string.options_preferences_language_english_description
            PreferencesViewModel.Language.HUNGARIAN -> R.string.options_preferences_language_hungarian_description
            PreferencesViewModel.Language.ROMANIAN -> R.string.options_preferences_language_romanian_description
        }
    }

    private fun onShouldShowExitConfirmationChanged(shouldShowExitConfirmation: Boolean) {
        analyticsManager.onExitConfirmationToggled(shouldShowExitConfirmation)
        preferenceDatabase.shouldShowExitConfirmation = shouldShowExitConfirmation
    }

    private fun onShouldShareUsageDataChanged(shouldShareUsageData: Boolean) {
        analyticsManager.updateCollectionEnabledState()
        preferenceDatabase.shouldShareUsageData = shouldShareUsageData
    }

    private fun onShouldShareCrashReportsChanged(shouldShareCrashReports: Boolean) {
        preferenceDatabase.shouldShareCrashReports = shouldShareCrashReports
    }

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