package com.pandulapeter.campfire.feature.main.options.preferences

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import com.pandulapeter.campfire.util.generateNotationExample

class PreferencesViewModel(
    private val context: Context,
    private val preferenceDatabase: PreferenceDatabase,
    private val firstTimeUserExperienceManager: FirstTimeUserExperienceManager,
    private val analyticsManager: AnalyticsManager
) : CampfireViewModel() {

    val shouldShowChords = MutableLiveData<Boolean>().apply { value = preferenceDatabase.shouldShowChords }
    val shouldUseGermanNotation = MutableLiveData<Boolean>().apply { value = preferenceDatabase.shouldUseGermanNotation }
    val englishNotationExample = generateNotationExample(false)
    val germanNotationExample = generateNotationExample(true)
    val theme = MutableLiveData<Theme>().apply { value = Theme.fromId(preferenceDatabase.theme) }
    val themeDescription = MutableLiveData<String>().apply { value = "" }
    val language = MutableLiveData<Language>().apply { value = Language.fromId(preferenceDatabase.language) }
    val languageDescription = MutableLiveData<String>().apply { value = "" }
    val shouldShowExitConfirmation = MutableLiveData<Boolean>().apply { value = preferenceDatabase.shouldShowExitConfirmation }
    val shouldShareUsageData = MutableLiveData<Boolean>().apply { value = preferenceDatabase.shouldShareUsageData }
    val shouldShareCrashReports = MutableLiveData<Boolean>().apply { value = preferenceDatabase.shouldShareCrashReports }
    val shouldShowHintsResetConfirmation = MutableLiveData<Boolean?>()
    val shouldShowHintsResetSnackbar = MutableLiveData<Boolean?>()
    val shouldShowThemeSelector = MutableLiveData<Boolean?>()
    val shouldShowLanguageSelector = MutableLiveData<Boolean?>()

    init {
        shouldShowChords.observeForever {
            analyticsManager.onShouldShowChordsToggled(it, AnalyticsManager.PARAM_VALUE_SCREEN_OPTIONS_PREFERENCES)
            preferenceDatabase.shouldShowChords = it
        }
        shouldUseGermanNotation.observeForever {
            analyticsManager.onNotationModeChanged(it)
            preferenceDatabase.shouldUseGermanNotation = it
        }
        theme.observeForever {
            if (it != null) {
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
        }
        language.observeForever {
            preferenceDatabase.language = it.id
            updateLanguageDescription()
            analyticsManager.onLanguageChanged(if (it == PreferencesViewModel.Language.AUTOMATIC) AnalyticsManager.PARAM_VALUE_AUTOMATIC else it.id)
        }
        shouldShowExitConfirmation.observeForever {
            analyticsManager.onExitConfirmationToggled(it)
            preferenceDatabase.shouldShowExitConfirmation = it
        }
        shouldShareUsageData.observeForever {
            analyticsManager.updateCollectionEnabledState()
            preferenceDatabase.shouldShareUsageData = it
        }
        shouldShareCrashReports.observeForever { preferenceDatabase.shouldShareCrashReports = it }
        updateThemeDescription()
        updateLanguageDescription()
    }

    fun onThemeClicked() {
        if (!isUiBlocked) {
            shouldShowThemeSelector.value = true
        }
    }

    fun onLanguageClicked() {
        if (!isUiBlocked) {
            shouldShowLanguageSelector.value = true
        }
    }

    fun onResetHintsClicked() {
        if (!isUiBlocked) {
            shouldShowHintsResetConfirmation.value = true
        }
    }

    fun resetHints() {
        firstTimeUserExperienceManager.resetAll()
        shouldShowHintsResetSnackbar.value = true
    }

    private fun updateThemeDescription() {
        themeDescription.value = context.getString(
            when (theme.value) {
                null, PreferencesViewModel.Theme.AUTOMATIC -> R.string.options_preferences_app_theme_automatic_description
                PreferencesViewModel.Theme.DARK -> R.string.options_preferences_app_theme_dark_description
                PreferencesViewModel.Theme.LIGHT -> R.string.options_preferences_app_theme_light_description
            }
        )
    }

    private fun updateLanguageDescription() {
        languageDescription.value =
                context.getString(
                    when (language.value) {
                        null, PreferencesViewModel.Language.AUTOMATIC -> R.string.options_preferences_language_automatic_description
                        PreferencesViewModel.Language.ENGLISH -> R.string.options_preferences_language_english_description
                        PreferencesViewModel.Language.HUNGARIAN -> R.string.options_preferences_language_hungarian_description
                        PreferencesViewModel.Language.ROMANIAN -> R.string.options_preferences_language_romanian_description
                    }
                )
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