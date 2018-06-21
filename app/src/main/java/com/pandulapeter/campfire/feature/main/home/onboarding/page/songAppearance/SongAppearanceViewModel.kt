package com.pandulapeter.campfire.feature.main.home.onboarding.page.songAppearance

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.util.generateNotationExample
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

class SongAppearanceViewModel : CampfireViewModel() {
    private val preferenceDatabase by inject<PreferenceDatabase>()
    val englishNotationExample = generateNotationExample(false)
    val germanNotationExample = generateNotationExample(true)
    //TODO: Initial values are not properly set.
    val isFirstOptionSelected = ObservableBoolean(preferenceDatabase.shouldShowChords && !preferenceDatabase.shouldUseGermanNotation)
    val isSecondOptionSelected = ObservableBoolean(preferenceDatabase.shouldShowChords && preferenceDatabase.shouldUseGermanNotation)
    val isThirdOptionSelected = ObservableBoolean(!preferenceDatabase.shouldShowChords)

    init {
        isFirstOptionSelected.onPropertyChanged {
            if (it) {
                isSecondOptionSelected.set(false)
                isThirdOptionSelected.set(false)
                preferenceDatabase.shouldShowChords = true
                preferenceDatabase.shouldUseGermanNotation = false
            }
        }
        isSecondOptionSelected.onPropertyChanged {
            if (it) {
                isFirstOptionSelected.set(false)
                isThirdOptionSelected.set(false)
                preferenceDatabase.shouldShowChords = true
                preferenceDatabase.shouldUseGermanNotation = true
            }
        }
        isThirdOptionSelected.onPropertyChanged {
            if (it) {
                isFirstOptionSelected.set(false)
                isSecondOptionSelected.set(false)
                preferenceDatabase.shouldShowChords = false
            }
        }
    }
}