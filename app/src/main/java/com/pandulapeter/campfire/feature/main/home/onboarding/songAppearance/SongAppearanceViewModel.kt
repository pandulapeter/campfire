package com.pandulapeter.campfire.feature.main.home.onboarding.songAppearance

import androidx.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.shared.deprecated.OldCampfireViewModel
import com.pandulapeter.campfire.util.generateNotationExample
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

class SongAppearanceViewModel : OldCampfireViewModel() {
    private val preferenceDatabase by inject<PreferenceDatabase>()
    private var areListenersSet = false
    val englishNotationExample = generateNotationExample(false)
    val germanNotationExample = generateNotationExample(true)
    val isFirstOptionSelected = ObservableBoolean()
    val isSecondOptionSelected = ObservableBoolean()
    val isThirdOptionSelected = ObservableBoolean()

    fun initialize() {
        isFirstOptionSelected.set(preferenceDatabase.shouldShowChords && !preferenceDatabase.shouldUseGermanNotation)
        isSecondOptionSelected.set(preferenceDatabase.shouldShowChords && preferenceDatabase.shouldUseGermanNotation)
        isThirdOptionSelected.set(!preferenceDatabase.shouldShowChords)
        if (!areListenersSet) {
            areListenersSet = true
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
}