package com.pandulapeter.campfire.feature.home.options.preferences

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.model.local.Note
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.integration.FirstTimeUserExperienceManager
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

class PreferencesViewModel : CampfireViewModel() {

    private val preferenceDatabase by inject<PreferenceDatabase>()
    private val firstTimeUserExperienceManager by inject<FirstTimeUserExperienceManager>()
    val shouldShowChords = ObservableBoolean(preferenceDatabase.shouldShowChords)
    val shouldUseGermanNotation = ObservableBoolean(preferenceDatabase.shouldUseGermanNotation)
    val englishNotationExample = generateNotationExample(false)
    val germanNotationExample = generateNotationExample(true)
    val shouldUseDarkTheme = ObservableBoolean(preferenceDatabase.shouldUseDarkTheme)
    val shouldShowExitConfirmation = ObservableBoolean(preferenceDatabase.shouldShowExitConfirmation)
    val shouldShowHintsResetConfirmation = ObservableBoolean()
    val shouldShowHintsResetSnackbar = ObservableBoolean()
    val shouldShareUsageData = ObservableBoolean(preferenceDatabase.shouldShareUsageData)

    init {
        shouldShowChords.onPropertyChanged { preferenceDatabase.shouldShowChords = it }
        shouldUseGermanNotation.onPropertyChanged { preferenceDatabase.shouldUseGermanNotation = it }
        shouldUseDarkTheme.onPropertyChanged { preferenceDatabase.shouldUseDarkTheme = it }
        shouldShowExitConfirmation.onPropertyChanged { preferenceDatabase.shouldShowExitConfirmation = it }
        shouldShareUsageData.onPropertyChanged { preferenceDatabase.shouldShareUsageData = it }
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
}