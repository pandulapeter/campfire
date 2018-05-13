package com.pandulapeter.campfire.feature.home.options.preferences

import android.content.Context
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.detail.page.parsing.Note
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

class PreferencesViewModel(private val context: Context) : CampfireViewModel() {

    private val preferenceDatabase by inject<PreferenceDatabase>()
    private val firstTimeUserExperienceManager by inject<FirstTimeUserExperienceManager>()
    val shouldShowChords = ObservableBoolean(preferenceDatabase.shouldShowChords)
    val shouldUseGermanNotation = ObservableBoolean(preferenceDatabase.shouldUseGermanNotation)
    val englishNotationExample = generateNotationExample(false)
    val germanNotationExample = generateNotationExample(true)
    val theme = ObservableField<Theme>(Theme.fromId(preferenceDatabase.theme))
    val themeDescription = ObservableField("")
    val shouldShowExitConfirmation = ObservableBoolean(preferenceDatabase.shouldShowExitConfirmation)
    val shouldShowHintsResetConfirmation = ObservableBoolean()
    val shouldShowHintsResetSnackbar = ObservableBoolean()
    val shouldShareUsageData = ObservableBoolean(preferenceDatabase.shouldShareUsageData)

    init {
        shouldShowChords.onPropertyChanged { preferenceDatabase.shouldShowChords = it }
        shouldUseGermanNotation.onPropertyChanged { preferenceDatabase.shouldUseGermanNotation = it }
        theme.onPropertyChanged {
            preferenceDatabase.theme = it.id
            updateThemeDescription()
        }
        shouldShowExitConfirmation.onPropertyChanged { preferenceDatabase.shouldShowExitConfirmation = it }
        shouldShareUsageData.onPropertyChanged { preferenceDatabase.shouldShareUsageData = it }
        updateThemeDescription()
    }

    fun onThemeClicked() {

    }

    fun onResetHintsClicked() {
        shouldShowHintsResetConfirmation.set(true)
    }

    fun resetHints() {
        firstTimeUserExperienceManager.resetAll()
        shouldShowHintsResetSnackbar.set(true)
    }

    private fun generateNotationExample(shouldUseGermanNotation: Boolean) = listOf(
        Note.C.getName(shouldUseGermanNotation),
        Note.CSharp.getName(shouldUseGermanNotation),
        Note.D.getName(shouldUseGermanNotation),
        Note.DSharp.getName(shouldUseGermanNotation),
        Note.E.getName(shouldUseGermanNotation),
        Note.F.getName(shouldUseGermanNotation),
        Note.FSharp.getName(shouldUseGermanNotation),
        Note.G.getName(shouldUseGermanNotation),
        Note.GSharp.getName(shouldUseGermanNotation),
        Note.A.getName(shouldUseGermanNotation),
        Note.ASharp.getName(shouldUseGermanNotation),
        Note.B.getName(shouldUseGermanNotation)
    ).joinToString(", ")

    private fun updateThemeDescription() = themeDescription.set(
        context.getString(
            when (theme.get()) {
                null, PreferencesViewModel.Theme.SYSTEM -> R.string.options_preferences_app_theme_system
                PreferencesViewModel.Theme.AUTOMATIC -> R.string.options_preferences_app_theme_automatic
                PreferencesViewModel.Theme.DARK -> R.string.options_preferences_app_theme_dark
                PreferencesViewModel.Theme.LIGHT -> R.string.options_preferences_app_theme_light
            }
        )
    )

    enum class Theme(val id: Int) {
        SYSTEM(0),
        AUTOMATIC(1),
        DARK(2),
        LIGHT(3);

        companion object {
            fun fromId(id: Int) = Theme.values().find { it.id == id } ?: AUTOMATIC
        }
    }
}