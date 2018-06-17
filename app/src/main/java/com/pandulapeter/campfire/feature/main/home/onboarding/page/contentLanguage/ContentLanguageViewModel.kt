package com.pandulapeter.campfire.feature.main.home.onboarding.page.contentLanguage

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

class ContentLanguageViewModel : CampfireViewModel() {

    private val preferenceDatabase by inject<PreferenceDatabase>()
    val shouldShowExplicit = ObservableBoolean(preferenceDatabase.shouldShowExplicit)

    init {
        shouldShowExplicit.onPropertyChanged { preferenceDatabase.shouldShowExplicit = it }
    }
}