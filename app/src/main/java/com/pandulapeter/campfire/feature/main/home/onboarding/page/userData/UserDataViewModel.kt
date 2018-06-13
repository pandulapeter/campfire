package com.pandulapeter.campfire.feature.main.home.onboarding.page.userData

import android.databinding.ObservableBoolean
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

class UserDataViewModel : CampfireViewModel() {

    private val preferenceDatabase by inject<PreferenceDatabase>()
    private val analyticsManager by inject<AnalyticsManager>()
    val isAnalyticsEnabled = ObservableBoolean(preferenceDatabase.shouldShareUsageData)
    val isCrashReportingEnabled = ObservableBoolean(preferenceDatabase.shouldShareCrashReports)

    init {
        isAnalyticsEnabled.onPropertyChanged {
            preferenceDatabase.shouldShareUsageData = it
            analyticsManager.updateCollectionEnabledState()
            if (it) {
                analyticsManager.onConsentGiven(System.currentTimeMillis())
            }
        }
        isCrashReportingEnabled.onPropertyChanged { preferenceDatabase.shouldShareCrashReports = it }
    }
}