package com.pandulapeter.campfire.feature.main.home.onboarding.page.musicianType

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.util.generateNotationExample
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

class MusicianTypeViewModel : CampfireViewModel() {
    private val preferenceDatabase by inject<PreferenceDatabase>()
    private var shouldTriggerCallback = true
    val englishNotationExample = generateNotationExample(false)
    val germanNotationExample = generateNotationExample(true)
    val isFirstOptionSelected = ObservableBoolean(preferenceDatabase.shouldShowChords && !preferenceDatabase.shouldUseGermanNotation)
    val isSecondOptionSelected = ObservableBoolean(preferenceDatabase.shouldShowChords && preferenceDatabase.shouldUseGermanNotation)
    val isThirdOptionSelected = ObservableBoolean(!preferenceDatabase.shouldShowChords)

    init {
        isFirstOptionSelected.onPropertyChangedImproved {
            isSecondOptionSelected.set(false)
            isThirdOptionSelected.set(false)
            preferenceDatabase.shouldShowChords = true
            preferenceDatabase.shouldUseGermanNotation = false
        }
        isSecondOptionSelected.onPropertyChangedImproved {
            isFirstOptionSelected.set(false)
            isThirdOptionSelected.set(false)
            preferenceDatabase.shouldShowChords = true
            preferenceDatabase.shouldUseGermanNotation = true
        }
        isThirdOptionSelected.onPropertyChangedImproved {
            isFirstOptionSelected.set(false)
            isSecondOptionSelected.set(false)
            preferenceDatabase.shouldShowChords = false
            preferenceDatabase.shouldUseGermanNotation = false
        }
    }

    private inline fun ObservableBoolean.onPropertyChangedImproved(crossinline callback: (Boolean) -> Unit) = onPropertyChanged {
        if (shouldTriggerCallback) {
            shouldTriggerCallback = false
            callback(it)
            shouldTriggerCallback = true
        }
    }
}